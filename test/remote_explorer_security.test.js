import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { SharedFolderManager } from '../src/streaming/shared_folder_manager.js';
import { HttpStreamingServer } from '../src/streaming/http_streaming_server.js';
import { SecuritySandbox, SecuritySandboxError } from '../src/streaming/security_sandbox.js';

const TMP_DIR = path.resolve('tmp/test_security_' + Date.now());
const VALID_TOKEN = 'token_secret_session_verified_abc123';
const VALID_PIN = '654 321';

function makeRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const req = http.request(
      {
        hostname: parsed.hostname,
        port: parsed.port,
        path: parsed.pathname + parsed.search,
        method: options.method || 'GET',
        headers: options.headers || {}
      },
      (res) => {
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
      }
    );
    req.on('error', reject);
    req.end();
  });
}

test('Security Sandbox & Confinement Penetration Test Suite', async (t) => {
  let server = null;
  let shareManager = null;
  let baseUrl = '';
  let share = null;
  let authorizedDir = '';
  let confidentialDir = '';

  await t.test('Setup: Création du dossier autorisé et du dossier confidentiel adjacent', async () => {
    await fs.mkdir(TMP_DIR, { recursive: true });
    authorizedDir = path.join(TMP_DIR, 'authorized_share');
    confidentialDir = path.join(TMP_DIR, 'confidential_area');

    await fs.mkdir(authorizedDir, { recursive: true });
    await fs.mkdir(confidentialDir, { recursive: true });

    // Fichier légitime dans le partage
    await fs.writeFile(path.join(authorizedDir, 'notes.txt'), 'Hello Safe World', 'utf8');

    // Fichier secret en dehors du partage
    await fs.writeFile(path.join(confidentialDir, 'passwords.txt'), 'SUPER_SECRET_KEYS', 'utf8');

    shareManager = new SharedFolderManager(TMP_DIR);
    share = await shareManager.addFolder(authorizedDir, 'SafeShare');

    server = new HttpStreamingServer({
      port: 0,
      folderManager: shareManager,
      sessionToken: VALID_TOKEN,
      sessionPin: VALID_PIN
    });

    await server.start();
    baseUrl = `http://127.0.0.1:${server.port}`;
  });

  await t.test('Auth: Absence de jeton retourne 401 Unauthorized', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares`);
    assert.equal(res.statusCode, 401);
    assert.ok(res.json);
    assert.equal(res.json.error, 'Unauthorized');
  });

  await t.test('Auth: Jeton invalide ou falsifié retourne 403 Forbidden', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares`, {
      headers: { Authorization: 'Bearer forged-bad-token-xyz' }
    });
    assert.equal(res.statusCode, 403);
    assert.ok(res.json);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Auth: Jeton valide par Authorization: Bearer <token> retourne 200 OK', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares`, {
      headers: { Authorization: `Bearer ${VALID_TOKEN}` }
    });
    assert.equal(res.statusCode, 200);
    assert.ok(Array.isArray(res.json));
    assert.equal(res.json.length, 1);
  });

  await t.test('Auth: Jeton valide par X-Session-Token retourne 200 OK', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares`, {
      headers: { 'X-Session-Token': VALID_TOKEN }
    });
    assert.equal(res.statusCode, 200);
    assert.equal(res.json.length, 1);
  });

  await t.test('Auth: PIN valide par X-Session-PIN retourne 200 OK (avec ou sans espace)', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares`, {
      headers: { 'X-Session-PIN': '654321' }
    });
    assert.equal(res.statusCode, 200);
  });

  await t.test('Auth: Jeton valide via URL query ?token= retourne 200 OK', async () => {
    const res = await makeRequest(`${baseUrl}/api/shares?token=${VALID_TOKEN}`);
    assert.equal(res.statusCode, 200);
  });

  await t.test('Auth: Endpoint /api/health accessible sans authentification', async () => {
    const res = await makeRequest(`${baseUrl}/api/health`);
    assert.equal(res.statusCode, 200);
    assert.equal(res.json.status, 'ok');
  });

  await t.test('Sandbox: Rejet Path Traversal classique ../ vers dossier confidentiel -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=../../confidential_area/passwords.txt&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet Path Traversal avec backslash ..\\ -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=..\\..\\confidential_area\\passwords.txt&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet Path Traversal encodé %2e%2e -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=%2e%2e%2f%2e%2e%2fconfidential_area%2fpasswords.txt&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet Path Traversal double encodage %252e%252e -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=%252e%252e%252fconfidential_area&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet chemin absolu Windows (C:\\Windows) -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=C:\\Windows\\System32\\cmd.exe&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet chemin absolu Unix (/etc/passwd) -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=/etc/passwd&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet chemins réseau UNC (\\\\127.0.0.1\\c$) -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=\\\\127.0.0.1\\c$\\Windows&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet injection d’octet nul (%00) -> 403 Forbidden', async () => {
    const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=notes.txt%00.jpg&token=${VALID_TOKEN}`;
    const res = await makeRequest(targetUrl);
    assert.equal(res.statusCode, 403);
    assert.equal(res.json.error, 'Access denied');
  });

  await t.test('Sandbox: Rejet des noms de périphériques réservés (CON, NUL, AUX) -> 403 Forbidden', async () => {
    for (const devName of ['CON', 'PRN', 'AUX', 'NUL', 'COM1', 'LPT1']) {
      const targetUrl = `${baseUrl}/api/shares/${share.id}/browse?subpath=${devName}&token=${VALID_TOKEN}`;
      const res = await makeRequest(targetUrl);
      assert.equal(res.statusCode, 403, `Périphérique ${devName} doit être rejeté avec 403`);
    }
  });

  await t.test('Sandbox: Rejet lien symbolique ou tentative d’échappement hors racine', async () => {
    // Tenter de créer un lien symbolique vers le dossier confidentiel si les permissions OS le permettent
    const symlinkPath = path.join(authorizedDir, 'escaped_symlink');
    let symlinkCreated = false;
    try {
      await fs.symlink(confidentialDir, symlinkPath, 'junction');
      symlinkCreated = true;
    } catch {
      // Sous Windows sans privilèges développeur, symlink peut échouer avec EPERM
    }

    if (symlinkCreated) {
      const targetUrl = `${baseUrl}/api/shares/${share.id}/stream?path=escaped_symlink/passwords.txt&token=${VALID_TOKEN}`;
      const res = await makeRequest(targetUrl);
      assert.equal(res.statusCode, 403, 'Accès via lien symbolique externe doit être rejeté avec 403');
    }

    // Tester directement la logique SecuritySandbox.resolveSafePath
    await assert.rejects(
      async () => {
        await SecuritySandbox.resolveSafePath(authorizedDir, '../../confidential_area/passwords.txt');
      },
      (err) => {
        return err instanceof SecuritySandboxError && err.statusCode === 403;
      }
    );
  });

  await t.test('Teardown: Arrêt du serveur et nettoyage', async () => {
    if (server) {
      await server.stop();
    }
    await fs.rm(TMP_DIR, { recursive: true, force: true });
  });
});
