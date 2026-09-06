# FastDrop 🚀
> Application de transfert direct ultra-rapide de fichiers entre Smartphone et Ordinateur (Android ↔ Windows / Linux / macOS) en réseau local sans cloud obligatoire.

Développé sous l'égide du **Programme LanceFer V4 — Universal Autonomous Software Factory**.

---

## ⚡ Caractéristiques Principales
- **Vitesse Maximale** : Transfert direct appareil-à-appareil par streaming binaire segmenté (Chunks de 1 Mo).
- **Zéro Cloud / 100% Hors-Ligne** : Fonctionne sur Wi-Fi local ou en mode Point d'accès (Hotspot) mobile sans consommer d'internet.
- **Reprise après Interruption (Resume Engine)** : Reprise chirurgicale sur coupure réseau sans repartir de zéro.
- **Mémoire RAM Maîtrisée** : Contrôle de flux (*Backpressure*) garantissant une empreinte RAM minimale (< 30 Mo) même pour des fichiers de 50 Go.
- **Intégrité Cryptographique** : Validation systématique SHA-256 de chaque fichier reçu.
- **Sécurité Native** : Appairage mutuel (code PIN / QR Code) et immunité absolue contre le Path Traversal.

---

## 📂 Architecture du Projet

```text
├── .agents/                    # Écosystème Multi-Agents LanceFer (12 agents)
│   └── plugins/lancefer/
│       ├── plugin.json
│       └── agents/             # Définitions des agents autonomes
├── .lancefer/                  # Mémoire persistante & Traçabilité autonome
│   ├── state.json              # État de santé et phase courante
│   ├── tasks.json              # Backlog et preuves de vérification
│   ├── decisions.json          # Registre des ADRs
│   ├── metrics.json            # Benchmarks de débits réels
│   └── risks.json              # Registre des risques
├── docs/                       # Spécifications & Architecture
│   ├── specifications/         # Product Brief & Golden Path
│   └── architecture/           # ADR-001, Spécification Protocole v1, Modèle de Sécurité
├── src/                        # FastDrop Transfer Core (Moteur universel)
│   ├── protocol/               # Framing binaire & types de messages FDSP v1
│   ├── core/                   # Chunks, Backpressure, Resume, Transfer Engine
│   └── security/               # Assainissement anti-Path Traversal
├── test/                       # Suites de tests automatisés (16 tests validés)
└── scripts/                    # Outils de benchmark et mesures de performance
```

---

## 🧪 Tests & Validation

Pour exécuter la suite de tests automatisés (16 tests unitaires et d'intégration E2E) :
```bash
node --test test/framing.test.js test/sanitizer.test.js test/chunk_manager.test.js test/resume_manager.test.js test/transfer_engine.test.js test/resume_e2e.test.js
```

Pour lancer le banc de mesure de performance et mesurer le débit réel :
```bash
node scripts/benchmark.js
```
