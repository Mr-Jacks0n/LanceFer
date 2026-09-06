import path from 'node:path';

/**
 * Nettoie et valide un nom de fichier pour empêcher toute attaque de type Path Traversal
 * Conforme aux règles d'audit de docs/architecture/security_model.md
 *
 * @param {string} rawFileName - Nom ou chemin relatif déclaré par l'émetteur
 * @param {string} targetDirectory - Dossier de destination autorisé sur la machine réceptrice
 * @returns {string} - Chemin absolu sécurisé
 * @throws {Error} - Si une tentative de traversée est détectée
 */
export function sanitizeAndResolvePath(rawFileName, targetDirectory) {
  if (!rawFileName || typeof rawFileName !== 'string') {
    throw new Error('Nom de fichier invalide ou manquant');
  }

  const resolvedTargetDir = path.resolve(targetDirectory);

  // 1. Extraire le nom de base en éliminant les séparateurs de dossiers malveillants
  // Si le transfert supporte les sous-dossiers, normaliser les séparateurs sans autoriser '..'
  const normalizedParts = rawFileName
    .replace(/\\/g, '/')
    .split('/')
    .filter(part => part && part !== '.' && part !== '..');

  if (normalizedParts.length === 0) {
    throw new Error('Nom de fichier vide après assainissement');
  }

  // 2. Nettoyer chaque segment des caractères interdits sous Windows
  const safeParts = normalizedParts.map(part => {
    // Supprimer les caractères Windows interdits (< > : " / \ | ? *) et octets nuls
    let clean = part.replace(/[<>:"/\\|?*\x00-\x1F]/g, '_');
    // Vérifier les noms de périphériques réservés Windows
    const reservedNames = /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$/i;
    if (reservedNames.test(clean)) {
      clean = `_${clean}`;
    }
    return clean;
  });

  // 3. Reconstituer le chemin relatif sécurisé
  const safeRelativePath = safeParts.join(path.sep);

  // 4. Résolution absolue
  const finalFullPath = path.resolve(resolvedTargetDir, safeRelativePath);

  // 5. Vérification canonique stricte : le chemin final doit impérativement être un descendant de resolvedTargetDir
  const relativeFromTarget = path.relative(resolvedTargetDir, finalFullPath);
  if (relativeFromTarget.startsWith('..') || path.isAbsolute(relativeFromTarget)) {
    throw new Error(`Tentative de Path Traversal détectée pour le chemin : ${rawFileName}`);
  }

  return finalFullPath;
}
