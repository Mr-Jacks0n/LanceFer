import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { SharedFolderManager } from '../src/streaming/shared_folder_manager.js';
import { HttpStreamingServer } from '../src/streaming/http_streaming_server.js';

const TMP_DIR = path.resolve('tmp/test_pagination_' + Date.now());
const SESSION_TOKEN = 'token_test_pagination_session_98765';

function makeRequest(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { headers }, (res) => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        let json = null;
        const text = Buffer.concat(chunks).toString('utf8');
        try {
          json = JSON.parse(text);
        } catch {}
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          text,
          json
        });
      });
    });
    req.on('error', reject);
  });
}

test('Remote Explorer Directory Listing, Pagination & Sorting Suite', async (t) => {
  let server = null;
  let shareManager = null;
  let baseUrl = '';
  let share = null;
  let sharedRoot = '';

  await t.test('Setup: Création d’une arborescence de 100 éléments (5 dossiers + 95 fichiers) et sous-dossiers', async () => {
    await fs.mkdir(TMP_DIR, { recursive: true });
    sharedRoot = path.join(TMP_DIR, 'pagination_share');
    await fs.mkdir(sharedRoot, { recursive: true });

    // Créer 5 sous-dossiers dans la racine (folder_a à folder_e)
    for (const d of ['folder_a', 'folder_b', 'folder_c', 'folder_d', 'folder_e']) {
      await fs.mkdir(path.join(sharedRoot, d), { recursive: true });
    }

    // Créer 95 fichiers dans la racine avec des tailles variables
    for (let i = 1; i <= 95; i++) {
      const numStr = String(i).padStart(3, '0');
      const fileName = `file_${numStr}.txt`;
      const content = 'X'.repeat(i * 10); // Taille = i * 10 octets
      await fs.writeFile(path.join(sharedRoot, fileName), content, 'utf8');
    }

    // Créer un sous-dossier imbriqué folder_a/sub_1 avec des médias pour tester les vignettes et breadcrumbs
    const nestedDir = path.join(sharedRoot, 'folder_a', 'sub_1');
    await fs.mkdir(nestedDir, { recursive: true });
    await fs.writeFile(path.join(nestedDir, 'inner.txt'), 'Inner text file', 'utf8');
    await fs.writeFile(path.join(nestedDir, 'photo.jpg'), 'Fake JPEG binary buffer for test', 'utf8');

    // Dossier vide pour test de cas limite
    const emptyDir = path.join(sharedRoot, 'folder_e', 'empty_sub');
    await fs.mkdir(emptyDir, { recursive: true });

    shareManager = new SharedFolderManager(TMP_DIR);
    share = await shareManager.addFolder(sharedRoot, 'PaginationShare');

    server = new HttpStreamingServer({
      port: 0,
      folderManager: shareManager,
      sessionToken: SESSION_TOKEN
    });

    await server.start();
    baseUrl = `http://127.0.0.1:${server.port}`;
  });

  await t.test('Listing: Dossier vide retourne items=[] et total=0', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?subpath=folder_e/empty_sub&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    assert.ok(res.json);
    assert.equal(res.json.items.length, 0);
    assert.equal(res.json.pagination.total, 0);
    assert.equal(res.json.pagination.totalPages, 1);
  });

  await t.test('Pagination Page 1: 40 éléments retournés, dossiers en tête puis fichiers alphabétiques', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?page=1&limit=40&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    const data = res.json;

    assert.equal(data.shareId, share.id);
    assert.equal(data.currentPath, '');
    assert.equal(data.breadcrumbs.length, 1);
    assert.equal(data.breadcrumbs[0].name, 'Root');

    assert.equal(data.pagination.total, 100, 'Le dossier racine doit contenir exactement 100 éléments (5 dossiers + 95 fichiers)');
    assert.equal(data.pagination.page, 1);
    assert.equal(data.pagination.limit, 40);
    assert.equal(data.pagination.totalPages, 3);
    assert.equal(data.items.length, 40);

    // Les 5 premiers éléments doivent être les dossiers
    for (let i = 0; i < 5; i++) {
      assert.equal(data.items[i].isDirectory, true, `L'élément ${i} doit être un dossier`);
    }
    assert.equal(data.items[0].name, 'folder_a');
    assert.equal(data.items[4].name, 'folder_e');

    // Les éléments suivants sont des fichiers
    for (let i = 5; i < 40; i++) {
      assert.equal(data.items[i].isDirectory, false, `L'élément ${i} doit être un fichier`);
    }
    assert.equal(data.items[5].name, 'file_001.txt');
    assert.equal(data.items[39].name, 'file_035.txt');
  });

  await t.test('Pagination Page 2: 40 éléments suivants disjoints de la page 1', async () => {
    const res1 = await makeRequest(`${baseUrl}/api/shares/${share.id}/browse?page=1&limit=40&token=${SESSION_TOKEN}`);
    const res2 = await makeRequest(`${baseUrl}/api/shares/${share.id}/browse?page=2&limit=40&token=${SESSION_TOKEN}`);

    assert.equal(res2.statusCode, 200);
    const page1Names = new Set(res1.json.items.map(item => item.name));
    const page2Names = new Set(res2.json.items.map(item => item.name));

    assert.equal(res2.json.items.length, 40);
    assert.equal(res2.json.items[0].name, 'file_036.txt');
    assert.equal(res2.json.items[39].name, 'file_075.txt');

    // Vérifier la stricte disjonction entre page 1 et page 2
    for (const name of page2Names) {
      assert.equal(page1Names.has(name), false, `L'élément ${name} ne doit pas être présent sur les deux pages`);
    }
  });

  await t.test('Pagination Page 3: 20 derniers éléments restants (100 - 80 = 20)', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?page=3&limit=40&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    assert.equal(res.json.items.length, 20);
    assert.equal(res.json.items[0].name, 'file_076.txt');
    assert.equal(res.json.items[19].name, 'file_095.txt');
  });

  await t.test('Pagination Page 4 (hors bornes): retourne tableau vide avec total inchangé', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?page=4&limit=40&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    assert.equal(res.json.items.length, 0);
    assert.equal(res.json.pagination.total, 100);
    assert.equal(res.json.pagination.page, 4);
  });

  await t.test('Navigation imbriquée & Breadcrumbs: sous-dossier folder_a/sub_1', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?subpath=folder_a/sub_1&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    const data = res.json;

    assert.equal(data.currentPath, 'folder_a/sub_1');
    assert.deepEqual(data.breadcrumbs, [
      { name: 'Root', subpath: '' },
      { name: 'folder_a', subpath: 'folder_a' },
      { name: 'sub_1', subpath: 'folder_a/sub_1' }
    ]);

    assert.equal(data.items.length, 2);
    const photoItem = data.items.find(i => i.name === 'photo.jpg');
    assert.ok(photoItem);
    assert.equal(photoItem.mimeType, 'image/jpeg');
    assert.equal(photoItem.hasThumbnail, true);
    assert.equal(photoItem.isDirectory, false);
  });

  await t.test('Tri par taille (sort=size): ordre croissant des tailles', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/browse?sort=size&page=1&limit=20&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    const filesOnly = res.json.items.filter(i => !i.isDirectory);

    for (let i = 1; i < filesOnly.length; i++) {
      assert.ok(
        filesOnly[i].size >= filesOnly[i - 1].size,
        `Taille de ${filesOnly[i].name} (${filesOnly[i].size}) doit être >= ${filesOnly[i - 1].size}`
      );
    }
  });

  await t.test('Service de Vignettes: GET /api/shares/:id/thumbnail retourne 200 OK avec image/jpeg', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/thumbnail?path=folder_a/sub_1/photo.jpg&token=${SESSION_TOKEN}`;
    const res = await makeRequest(url);

    assert.equal(res.statusCode, 200);
    assert.equal(res.headers['content-type'], 'image/jpeg');
    assert.ok(parseInt(res.headers['content-length'], 10) > 0);
  });

  await t.test('Teardown: Nettoyage et fermeture du serveur', async () => {
    if (server) {
      await server.stop();
    }
    await fs.rm(TMP_DIR, { recursive: true, force: true });
  });
});
