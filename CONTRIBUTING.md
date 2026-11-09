# Contribuer à LanceFer / LanceDrop

Merci de votre intérêt ! LanceDrop est un transfert de fichiers
**local et sans cloud** : appairage PIN/QR, chunks SHA-256,
reprise réseau. Toutes les contributions sont bienvenues.

## Pour commencer

```bash
git clone https://github.com/Mr-Jacks0n/LanceFer.git
cd LanceFer
npm install
npm test              # 145 tests Node — doivent passer au vert
npm run desktop       # UI Electron locale
```

Côté Android : Android Studio + JDK 17, ouvrez `android/`.

## Les règles de la maison

- **Aucune donnée personnelle dans les commits** : `dist/`, `tmp/`,
  historiques locaux et fils de session ne sont pas versionnés
  (voir `.gitignore`).
- **Le protocole d'abord** : tout changement affectant FDSP doit être
  réfléchi dans `docs/architecture/` (ADR) avant le code.
- **Tests obligatoires** : toute nouvelle fonctionnalité vient avec
  sa suite `node --test` (et JUnit si Android). Refermez un bug avec
  un test qui échouait avant.
- **Sécurité** : jamais de `eval`, jamais de chemin utilisateur non
  passé par `src/security/sanitizer.js`. L'anti-Path-Traversal est
  non négociable.
- **Style** : Node ES modules côté bureau (`type: module`, main.cjs
  pour l'Electron main), Kotlin idiomatique côté Android.

## Comment proposer un changement

1. Ouvrez une **issue** (templates bug / idée fournis).
2. Branche : `git checkout -b feat/ma-fonctionnalite`
3. Testez Node **et** l'app concernée (desktop ou mobile).
4. Commits conventionnels (`feat:`, `fix:`, `docs:`, `test:`…).
5. PR avec le template ; vérifiez que la CI verte.

## Bonnes premières contributions

- Build AppImage / macOS (bureau Electron).
- Normalisation des accents/casse dans le matching des noms d'appareils.
- Améliorer le bench (`scripts/benchmark.js`): plus de tailles, jeux
  de fichiers réels.
- Nettoyer les TODOs dans `src/streaming/`.

Merci de faire avancer un transfert qui reste *chez vous* 🚀
