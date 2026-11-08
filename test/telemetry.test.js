import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TelemetryManager } from '../src/telemetry/telemetry_manager.cjs';

test('Telemetry: Scrubbing strict des chemins utilisateurs locaux (Zero PII)', () => {
  const tm = new TelemetryManager();
  tm.userHome = 'C:\\Users\\Alice';
  tm.userName = 'Alice';

  const rawPath = 'Erreur lors de la lecture de C:\\Users\\Alice\\Documents\\rapport_secret.pdf';
  const scrubbed = tm.scrub(rawPath);

  assert.ok(!scrubbed.includes('Alice'), 'Le nom d’utilisateur ne doit pas figurer dans le message scrubbé');
  assert.ok(scrubbed.includes('[USER_HOME]') || scrubbed.includes('[USER]'), 'Le chemin Home doit être remplacé par un token anonymisé');
});

test('Telemetry: Scrubbing des clés cryptographiques AES-256 et des codes PIN', () => {
  const tm = new TelemetryManager();

  const rawKey = 'Clé de session: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 pour le transfert';
  const scrubbedKey = tm.scrub(rawKey);
  assert.ok(!scrubbedKey.includes('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'), 'La clé hex 256 bits doit être masquée');
  assert.ok(scrubbedKey.includes('[REDACTED_KEY_256]'), 'Le token REDACTED_KEY_256 doit être présent');

  const rawPin = 'Validation échec: pin: 842190 invalide';
  const scrubbedPin = tm.scrub(rawPin);
  assert.ok(!scrubbedPin.includes('842190'), 'Le PIN 6 chiffres ne doit pas fuiter');
  assert.ok(scrubbedPin.includes('[REDACTED_PIN]'), 'Le token REDACTED_PIN doit être présent');
});

test('Telemetry: Masquage des adresses IP locales privées', () => {
  const tm = new TelemetryManager();

  const rawIp1 = 'Connexion établie avec 192.168.1.45:42420';
  const scrubbed1 = tm.scrub(rawIp1);
  assert.ok(!scrubbed1.includes('192.168.1.45'), 'L’IP locale spécifique doit être masquée');
  assert.ok(scrubbed1.includes('192.168.*.*'), 'Le sous-réseau doit être anonymisé avec *.*');

  const rawIp2 = 'Pair détecté sur 10.0.0.12';
  const scrubbed2 = tm.scrub(rawIp2);
  assert.ok(scrubbed2.includes('10.*.*.*'), 'Le sous-réseau 10.x doit être anonymisé');
});

test('Telemetry: Buffer circulaire des breadcrumbs borné à maxBreadcrumbs (50)', () => {
  const tm = new TelemetryManager();
  tm.clearBreadcrumbs();

  for (let i = 0; i < 65; i++) {
    tm.addBreadcrumb('test', `Événement ${i}`, { index: i });
  }

  const crumbs = tm.getRecentBreadcrumbs();
  assert.equal(crumbs.length, 50, 'Le nombre de breadcrumbs doit être borné à 50');
  assert.equal(crumbs[crumbs.length - 1].data.index, 64, 'Le dernier événement doit être conservé');
  assert.equal(crumbs[0].data.index, 15, 'Les plus anciens événements au-delà de 50 doivent être évincés');
});

test('Telemetry: Résilience hors-ligne totale (Capture d’exception sans crash)', () => {
  const tm = new TelemetryManager({
    sentryDsn: 'https://fakekey@o0.ingest.sentry.io/0',
    clarityId: 'clarity_test'
  });

  // Capture d'une erreur simulée
  const event = tm.captureException(new Error('Erreur socket simulee en mode hors-ligne'), {
    module: 'transfer_engine'
  });

  assert.ok(event.event_id, 'Un ID d’événement doit être généré');
  assert.equal(event.exception.values[0].type, 'Error');
  assert.equal(event.platform, 'node');
});

test('Telemetry: Configuration Microsoft Clarity sûre pour le frontend', () => {
  const tm = new TelemetryManager({
    clarityId: 'project_clarity_xyz'
  });

  const config = tm.getClarityConfig();
  assert.equal(config.clarityId, 'project_clarity_xyz');
  assert.equal(config.maskSensitive, true);
  assert.equal(config.appName, 'LanceDrop');
});
