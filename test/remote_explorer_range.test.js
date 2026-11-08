import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { SharedFolderManager } from '../src/streaming/shared_folder_manager.js';
import { HttpStreamingServer } from '../src/streaming/http_streaming_server.js';
import { parseRange } from '../src/streaming/range_streamer.js';

const TMP_DIR = path.resolve('tmp/test_range_' + Date.now());
const SESSION_TOKEN = 'test-session-token-range-12345';
const TOTAL_FILE_SIZE = 512 * 1024 * 1024; // 536,870,912 bytes (512 MB sparse file)

/**
 * Helper to perform HTTP GET requests and collect response buffer and headers.
 */
function makeRequest(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { headers }, (res) => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: Buffer.concat(chunks)
        });
      });
    });
    req.on('error', reject);
  });
}

test('RFC 7233 HTTP Range Streaming Suite', async (t) => {
  let server = null;
  let shareManager = null;
  let baseUrl = '';
  let share = null;
  let videoPath = '';

  await t.test('Setup: Initialiser le dossier partagé, le fichier creux de 512 Mo et le serveur HTTP', async () => {
    await fs.mkdir(TMP_DIR, { recursive: true });
    const sharedDir = path.join(TMP_DIR, 'shared');
    await fs.mkdir(sharedDir, { recursive: true });

    // Création d'un fichier creux (sparse file) de 512 Mo avec des marqueurs précis
    videoPath = path.join(sharedDir, 'heavy_video.mp4');
    const handle = await fs.open(videoPath, 'w+');
    await handle.truncate(TOTAL_FILE_SIZE);

    // Marqueur début (offset 0)
    await handle.write(Buffer.from('HEAD_HDR'), 0, 8, 0);

    // Marqueur milieu (offset 256 Mo = 268,435,456)
    await handle.write(Buffer.from('MID_BODY'), 0, 8, 256 * 1024 * 1024);

    // Marqueur fin (derniers 8 octets = 536,870,904)
    await handle.write(Buffer.from('TAIL_END'), 0, 8, TOTAL_FILE_SIZE - 8);

    await handle.close();

    const stat = await fs.stat(videoPath);
    assert.equal(stat.size, TOTAL_FILE_SIZE, 'Le fichier vidéo de test doit faire exactement 512 Mo');

    // Initialiser le gestionnaire de partages et le serveur HTTP
    shareManager = new SharedFolderManager(TMP_DIR);
    share = await shareManager.addFolder(sharedDir, 'TestSharedVideo');

    server = new HttpStreamingServer({
      port: 0, // Port éphémère
      folderManager: shareManager,
      sessionToken: SESSION_TOKEN
    });

    await server.start();
    baseUrl = `http://127.0.0.1:${server.port}`;
  });

  await t.test('Unit: parseRange valide la syntaxe et les bornes RFC 7233', () => {
    // Range standard
    const r1 = parseRange('bytes=0-1048575', 536870912);
    assert.deepEqual(r1, { start: 0, end: 1048575, total: 536870912 });

    // Range ouvert à la fin
    const r2 = parseRange('bytes=1000-', 5000);
    assert.deepEqual(r2, { start: 1000, end: 4999, total: 5000 });

    // Suffix range (derniers 500 octets)
    const r3 = parseRange('bytes=-500', 5000);
    assert.deepEqual(r3, { start: 4500, end: 4999, total: 5000 });

    // Range avec dépassement de fin (doit être borné à total - 1)
    const r4 = parseRange('bytes=1000-99999999', 5000);
    assert.deepEqual(r4, { start: 1000, end: 4999, total: 5000 });

    // Invalide / hors bornes -> unsatisfiable
    const r5 = parseRange('bytes=6000-7000', 5000);
    assert.equal(r5.unsatisfiable, true);

    const r6 = parseRange('bytes=500-200', 5000);
    assert.equal(r6.unsatisfiable, true);

    const r7 = parseRange('bytes=-0', 5000);
    assert.equal(r7.unsatisfiable, true);

    // En-tête absent ou malformé
    assert.equal(parseRange(null, 5000), null);
    assert.equal(parseRange('invalid-range', 5000), null);
  });

  await t.test('RFC 7233: Requête Range 0-1048575 retourne 206 avec exactement 1 Mo et Content-Range conforme', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: 'bytes=0-1048575'
    });

    assert.equal(res.statusCode, 206, 'Le code de statut doit être 206 Partial Content');
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-range'], `bytes 0-1048575/${TOTAL_FILE_SIZE}`);
    assert.equal(res.headers['content-length'], '1048576', 'Content-Length doit être exactement 1 Mo');
    assert.equal(res.headers['content-type'], 'video/mp4');
    assert.equal(res.body.length, 1048576, 'Le corps de réponse doit contenir exactement 1,048,576 octets');

    // Vérifier les 8 premiers octets correspondant au marqueur début
    const headerMarker = res.body.subarray(0, 8).toString('utf8');
    assert.equal(headerMarker, 'HEAD_HDR');
  });

  await t.test('Seeking O(1) à 256 Mo (milieu de fichier 500 Mo+) : réponse instantanée avec MID_BODY', async () => {
    const midStart = 256 * 1024 * 1024; // 268,435,456
    const midEnd = midStart + 1048576 - 1; // 269,484,031 (1 Mo)
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;

    const startTime = Date.now();
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: `bytes=${midStart}-${midEnd}`
    });
    const duration = Date.now() - startTime;

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes ${midStart}-${midEnd}/${TOTAL_FILE_SIZE}`);
    assert.equal(res.headers['content-length'], '1048576');
    assert.equal(res.body.length, 1048576);

    const midMarker = res.body.subarray(0, 8).toString('utf8');
    assert.equal(midMarker, 'MID_BODY', 'Le bloc de 256 Mo doit commencer par MID_BODY sans lire depuis 0');
    assert.ok(duration < 500, `La réponse doit être quasi instantanée (< 500ms, mesuré: ${duration}ms)`);
  });

  await t.test('Seeking fin de fichier (derniers 8 octets) : retourne TAIL_END', async () => {
    const tailStart = TOTAL_FILE_SIZE - 8;
    const tailEnd = TOTAL_FILE_SIZE - 1;
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;

    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: `bytes=${tailStart}-${tailEnd}`
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes ${tailStart}-${tailEnd}/${TOTAL_FILE_SIZE}`);
    assert.equal(res.headers['content-length'], '8');
    assert.equal(res.body.length, 8);
    assert.equal(res.body.toString('utf8'), 'TAIL_END');
  });

  await t.test('Suffix Range: bytes=-1024 retourne les derniers 1024 octets', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: 'bytes=-1024'
    });

    assert.equal(res.statusCode, 206);
    const expectedStart = TOTAL_FILE_SIZE - 1024;
    const expectedEnd = TOTAL_FILE_SIZE - 1;
    assert.equal(res.headers['content-range'], `bytes ${expectedStart}-${expectedEnd}/${TOTAL_FILE_SIZE}`);
    assert.equal(res.headers['content-length'], '1024');
    assert.equal(res.body.length, 1024);

    const tailEnd = res.body.subarray(res.body.length - 8).toString('utf8');
    assert.equal(tailEnd, 'TAIL_END');
  });

  await t.test('Open-ended Range: bytes=536870000- retourne jusqu’à la fin', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: 'bytes=536870000-'
    });

    assert.equal(res.statusCode, 206);
    const expectedLength = TOTAL_FILE_SIZE - 536870000; // 912 octets
    assert.equal(res.headers['content-range'], `bytes 536870000-${TOTAL_FILE_SIZE - 1}/${TOTAL_FILE_SIZE}`);
    assert.equal(res.headers['content-length'], String(expectedLength));
    assert.equal(res.body.length, expectedLength);
  });

  await t.test('Range hors bornes retourne 416 Range Not Satisfiable avec Content-Range: bytes */total', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=heavy_video.mp4`;
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: 'bytes=600000000-700000000'
    });

    assert.equal(res.statusCode, 416, 'Doit retourner 416 Range Not Satisfiable');
    assert.equal(res.headers['content-range'], `bytes */${TOTAL_FILE_SIZE}`);

    // Test plage inversée start > end
    const res2 = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`,
      Range: 'bytes=100-50'
    });
    assert.equal(res2.statusCode, 416);
    assert.equal(res2.headers['content-range'], `bytes */${TOTAL_FILE_SIZE}`);
  });

  await t.test('Requête sans en-tête Range retourne 200 OK avec le fichier complet', async () => {
    // Créer un petit fichier de 4 Ko pour vérifier le téléchargement complet
    const smallFile = path.join(TMP_DIR, 'shared', 'small.txt');
    const content = 'LanceDrop RFC 7233 Full Content Stream Verification Buffer';
    await fs.writeFile(smallFile, content, 'utf8');

    const url = `${baseUrl}/api/shares/${share.id}/stream?path=small.txt`;
    const res = await makeRequest(url, {
      Authorization: `Bearer ${SESSION_TOKEN}`
    });

    assert.equal(res.statusCode, 200);
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-length'], String(Buffer.byteLength(content)));
    assert.equal(res.body.toString('utf8'), content);
  });

  await t.test('Teardown: Fermer le serveur HTTP et purger les fichiers de test', async () => {
    if (server) {
      await server.stop();
    }
    await fs.rm(TMP_DIR, { recursive: true, force: true });
  });
});
