import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { sanitizeAndResolvePath } from '../src/security/sanitizer.js';

const TEST_DIR = path.resolve('tmp/downloads');

test('Sanitizer: accepte les noms de fichiers sains', () => {
  const result = sanitizeAndResolvePath('photo.jpg', TEST_DIR);
  assert.equal(result, path.join(TEST_DIR, 'photo.jpg'));
});

test('Sanitizer: neutralise les tentatives de Path Traversal avec ../', () => {
  // Une attaque classique ../../../windows/system32.dll doit être nettoyée et rester sous TEST_DIR
  const result = sanitizeAndResolvePath('../../../windows/system32.dll', TEST_DIR);
  assert.equal(result, path.join(TEST_DIR, 'windows', 'system32.dll'));
  assert.ok(result.startsWith(TEST_DIR));
});

test('Sanitizer: nettoie les caractères interdits sous Windows', () => {
  const result = sanitizeAndResolvePath('rapport<2026>:final?.pdf', TEST_DIR);
  assert.equal(result, path.join(TEST_DIR, 'rapport_2026__final_.pdf'));
});

test('Sanitizer: neutralise les noms de périphériques réservés Windows', () => {
  const result = sanitizeAndResolvePath('aux.txt', TEST_DIR);
  assert.equal(result, path.join(TEST_DIR, '_aux.txt'));
});

test('Sanitizer: rejette les entrées vides ou invalides', () => {
  assert.throws(() => sanitizeAndResolvePath('', TEST_DIR), /Nom de fichier invalide/);
  assert.throws(() => sanitizeAndResolvePath('../..', TEST_DIR), /Nom de fichier vide/);
});
