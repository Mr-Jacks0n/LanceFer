# Changelog — LanceFer / LanceDrop

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).

## [0.2.0] — 2026-11

### Added
- **Explorateur distant** : parcourir l'appareil distant et *streamer*
  photos/vidéos (serveur HTTP avec *range requests*, sandbox,
  aperçus/vignettes, dossiers partagés choisis) depuis le mobile.
- **Presse-papiers partagé** : synchronisation instantanée du texte
  entre mobile et bureau.
- **Streaming chiffré** reproductible (`crypto_stream` côté Node,
  `CryptoStreamManager` côté Kotlin), QR de certification signé
  (HMAC, `crypto_qr.cjs` / `QrSecurityCrypto.kt`).
- **Télémétrie** intégrée des deux côtés (débits, événements resume).
- **Historique persistant** des transferts (Node `history_store`,
  Android `HistoryDatabaseHelper`).
- **Auto-exclusion « Mon appareil »** : filtrage multi-interfaces
  (Wi-Fi, Ethernet, hotspot, VPN, WSL/Hyper-V, loopback) du bureau
  (5 couches dans `main.cjs`) et du mobile (`DiscoveryFilter`,
  NSD + UDP + ViewModel) ; section dédiée non-interactive conforme
  WCAG AAA.
- **Tests** : 145 tests Node (dont suites adversariales, parité
  Windows, contraste AAA, E2E physique) + 59 tests JVM Android.
- **CI de tests** (`ci.yml`) à chaque push/PR ; la release reste
  déclenchée par les tags `v*`.
- Documentation produit : bilingue EN/FR, LICENSE MIT, templates
  GitHub, diagramme FDSP, changelog.

### Removed
- Artefacts de l'usine autonome retirés du dépôt public
  (`.lancefer/`, `.agents/`, caches de build) — conservés en local
  comme mémoire de travail.

## [0.1.0] — 2026-09-07

### Added
- Première release officielle : **LanceDrop Desktop** (Electron,
  installateur NSIS + portable Windows) et **LanceDrop Android**
  (APK debug, SAF zéro-permission).
- Protocole FDSP v1 (framing binaire), chunks 1 Mo, backpressure RAM,
  resume engine, intégrité SHA-256, appairage mutuel, sanitization
  anti-path-traversal.
- Écosystème multi-agents LanceFer (12 rôles) + mémoire tracée
  `.lancefer/` + ADRs dans `docs/architecture/`.
- Publication automatisée par CI (exe + apk + notes de release).
