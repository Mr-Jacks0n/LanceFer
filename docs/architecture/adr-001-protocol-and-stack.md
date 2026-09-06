# ADR-001 : SÉLECTION DE LA STACK TECHNIQUE ET DU PROTOCOLE FASTDROP
## Architectural Decision Record
**Auteur :** `lancefer_system_architect`  
**Date :** 2026-09-06  
**Statut :** ACCEPTÉ  
**Projet :** FastDrop (Programme LanceFer V4)

---

## 1. Contexte & Problématique

FastDrop doit assurer le transfert ultra-rapide, direct et fiable de fichiers (pouvant dépasser 10 Go) entre un terminal Android et un PC Windows sur un réseau local Wi-Fi / Ethernet, sans nécessiter d'infrastructure cloud.

Les défis techniques majeurs sont :
1. **Performance réseau & Débit** : Minimiser la surcharge protocolaire et exploiter les capacités maximales du Wi-Fi 5/6 (débits visés : 30 à 80+ Mo/s en local).
2. **Gestion de la mémoire** : Éviter tout débordement RAM lors du traitement de gros fichiers (streaming obligatoire avec backpressure).
3. **Découverte automatique sans configuration** : Les appareils doivent se trouver instantanément sans obliger l'utilisateur à entrer des adresses IP.
4. **Maintenabilité et découplage** : Le cœur de transfert (*Transfer Core*) doit être autonome et testable indépendamment des interfaces graphiques.

---

## 2. Évaluation Comparative des Options

### 2.1 Pile de Transport Réseau

| Protocole | Débit & Surcharge | Complexité d'implémentation | Support Mobile / Desktop | Verdict |
|---|---|---|---|---|
| **TCP Sockets (Streams binaires + framing léger)** | **Optimal** (Très faible surcharge, contrôle de flux natif via fenêtre TCP) | **Faible à moyenne** (Standard sur tous les OS) | Universel (Android Java/Kotlin, Windows C#/Rust/C++) | **RETENU** |
| **QUIC / UDP** | Excellent en cas de perte de paquets, mais surcharge CPU plus élevée | Élevée (dépendances volumineuses, support natif variable sur Android SAF) | Bon mais bibliothèques tierces lourdes | *Écarté pour le MVP, conservé pour v2* |
| **HTTP/2 ou HTTP/3** | Bon, mais encapsulations de headers inutiles pour du streaming pur | Moyenne | Universel | *Écarté (surcharge inutile)* |
| **WebSockets** | Correct pour les messages de contrôle, médiocre pour le transfert de fichiers de 10 Go | Faible | Universel | *Écarté pour les données* |

**Décision Transport :**  
- Canal de contrôle et de signalement : **TCP avec framing léger (ou TLS local)**.
- Canal de données : **Flux TCP binaire segmenté en blocs (Chunks de 512 Ko à 2 Mo)** avec acquittement asynchrone pour réguler le débit.

---

### 2.2 Mécanisme de Découverte d'Appareils (*Discovery*)

| Mécanisme | Expérience utilisateur | Fiabilité LAN | Contraintes techniques |
|---|---|---|---|
| **mDNS / DNS-SD (Bonjour / Zeroconf)** | **Excellente** (Zéro configuration, annonce automatique du nom de machine) | Excellente sur la quasi-totalité des box et routeurs Wi-Fi domestiques | Support natif sous Android (`NsdManager`) et Windows (`mDNS` natif ou socket multicast UDP 5353) |
| **UDP Broadcast (Port dédié)** | Bonne | Variable (souvent bloqué sur routeurs d'entreprise) | Très simple en fallback |
| **Code PIN / QR Code avec IP** | Manuel mais 100% garanti | 100% infaillible même avec isolation AP | Solution de secours indispensable si le Wi-Fi bloque le multicast |

**Décision Découverte :**  
- Mécanisme primaire : **mDNS / DNS-SD** service type `_fastdrop._tcp.local.`.
- Mécanisme secondaire (fallback) : **Broadcast UDP local** sur port 42424.
- Mécanisme de secours absolu : **Affichage du QR Code / IP manuelle** sur l'écran du récepteur.

---

### 2.3 Choix des Stacks Applicatives

#### 1. Moteur Logique Commun (Transfer Core)
- **Architecture :** Architecture découplée en couches :
  - `Framing & Protocol Layer` (sérialisation des paquets, validation des signatures magiques).
  - `ChunkManager` (lecture par buffer de 1 Mo, hachage incrémental SHA-256).
  - `BackpressureController` (contrôle réactif de flux).
  - `ResumeManifest` (gestion d'état JSON/binaire persisté sur disque).
- **Implémentation :** Modules logiques propres, réutilisables ou implémentés selon les idiomes natifs garantissant des performances maximales :
  - **Côté Android** : Kotlin natif (Coroutines, `ByteReadChannel`, `Storage Access Framework`).
  - **Côté Windows** : C# .NET 8 AOT ou Rust (faible consommation RAM, intégration fluide Windows Sockets).

#### 2. Application Android
- **Langage :** Kotlin.
- **Architecture :** Clean Architecture / MVVM.
- **Stockage :** `Storage Access Framework (SAF)` avec lecture/écriture par `ContentResolver.openInputStream()` / `openOutputStream()` via des buffers de taille calibrée (1 Mo).
- **Exécution d'arrière-plan :** `ForegroundService` avec notification de transfert permanente et WakeLock temporaire.

#### 3. Application Windows Desktop
- **Langage & Framework :** Modern .NET 8 (C#) avec UI réactive et moderne (ou Rust avec Tauri pour une empreinte binaire minimale).
- **Réseau :** `System.Net.Sockets` asynchrone à haute performance (`SocketAsyncEventArgs` / `Pipelines`).
- **Disque :** `FileStream` avec `FileOptions.Asynchronous` et allocation d'espace préalable (*Sparse file / SetLength*) pour éliminer la fragmentation disque lors de gros transferts.

---

## 3. Conséquences de l'Architecture

### Positives :
- Débit maximal garanti sans intermédiaire réseau.
- Empreinte RAM bornée et constante (< 60 Mo sur PC, < 40 Mo sur smartphone), même pour un fichier de 50 Go.
- Portabilité ultérieure facilitée grâce au découplage complet de la spécification de protocole.
- Aucune dépendance externe opaque ou abonnement à des services tiers.

### Négatives / Risques identifiés & mitigés :
- Nécessite d'autoriser l'application dans le Pare-feu Windows à l'installation (l'installeur ou le premier lancement devra créer la règle de pare-feu locale).
- Certains routeurs publics avec isolation client (*AP Isolation*) bloqueront la connexion directe : le mode Point d'accès (Hotspot) ou la saisie manuelle de l'adresse sera documenté dans l'application.
