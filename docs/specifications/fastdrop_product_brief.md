# FASTDROP — PRODUCT BRIEF & CAHIER DES CHARGES MVP
## LanceFer Pilot Project — Document de Spécification Produit
**Auteur :** `lancefer_product_owner`  
**Statut :** Validé pour Conception  
**Version :** 1.0.0 (Phase 1)

---

## 1. Vision & Proposition de Valeur

### 1.1 Problème Résolu
Transférer des fichiers entre un smartphone Android et un ordinateur (Windows/Linux/macOS) est aujourd'hui une expérience souvent lente, contraignante ou compromise :
- Les solutions cloud (Google Drive, Dropbox, WeTransfer) consomment inutilement la bande passante internet, échouent sur les fichiers volumineux (>2 Go), dépendent d'un compte en ligne et soulèvent des questions de confidentialité.
- Les câbles USB imposent une manipulation physique, des configurations MTP souvent instables ou des pilotes manquants.
- Les solutions propriétaires (AirDrop, Quick Share) sont cloisonnées par écosystème ou nécessitent des configurations réseau complexes.

### 1.2 Mission FastDrop
Fournir une application de transfert de fichiers **directe, instantanée, sécurisée et hors ligne** entre smartphone et ordinateur :
> **Ouvrir l'application → Détecter l'appareil en local → Sélectionner les fichiers → Envoyer → Fini.**

---

## 2. Ordre de Priorité Absolu

Conformément aux principes directeurs LanceFer :
1. **Vitesse** : Exploiter au maximum la capacité du réseau local (Wi-Fi 5 / Wi-Fi 6 / Ethernet) sans goulot d'étranglement applicatif.
2. **Fiabilité** : Tolérance aux perturbations ; zéro perte de données ; reprise systématique après coupure.
3. **Simplicité** : Aucune saisie d'adresse IP requise dans 99% des cas ; découverte automatique en un clic.
4. **Sécurité** : Chiffrement local de bout en bout, appairage explicite, défense impérative contre le Path Traversal.
5. **Compatibilité** : Priorité initiale au binôme Android ↔ Windows, avec architecture extensible vers Linux, macOS et iOS.
6. **Design** : Interface moderne, épurée, centrée sur la lisibilité des débits et de la progression.

---

## 3. Le "Golden Path" (Parcours Utilisateur Idéal)

```text
┌─────────────────┐       ┌─────────────────┐
│ Émetteur        │       │ Récepteur       │
│ (ex: Smartphone)│       │ (ex: PC Windows)│
└────────┬────────┘       └────────┬────────┘
         │                         │
         │ 1. Découverte locale     │ (mDNS / Annonce réseau)
         ├────────────────────────►│
         │                         │
         │ 2. Appairage (1ère fois)│ (Code PIN 6 chiffres / QR Code)
         │◄───────────────────────►│
         │                         │
         │ 3. Sélection fichiers   │ (Photos, vidéos 4K, dossiers, archives)
         │                         │
         │ 4. Proposition transfert│ (Nom, taille totale, nombre de fichiers)
         ├────────────────────────►│
         │    Acceptation          │
         │◄────────────────────────┤
         │                         │
         │ 5. Transfert Streaming  │ (Chunks, Débit temps réel, Backpressure)
         ├════════════════════════►│
         │                         │
         │ 6. Validation SHA-256   │ (Intégrité confirmée)
         │◄────────────────────────┤
         │                         │
         │ 7. Succès & Notification│ (Fichiers disponibles dans "Téléchargements")
         └                         └
```

---

## 4. Périmètre Fonctionnel

### 4.1 Inclus dans le MVP (Phase Pilote)
- **Découverte automatique locale** sur le même réseau Wi-Fi ou via le point d'accès (Hotspot) du smartphone sans configuration manuelle.
- **Saisie IP manuelle en fallback** (si l'isolation réseau AP bloque la découverte automatique).
- **Appairage sécurisé** : Code PIN éphémère à 6 chiffres affiché sur un écran et validé sur l'autre. Mémorisation des terminaux de confiance.
- **Transferts bidirectionnels** :
  - Smartphone Android → Ordinateur Windows.
  - Ordinateur Windows → Smartphone Android.
- **Types de charges supportées** :
  - Fichiers individuels (photos, vidéos, documents, APK, ISO).
  - Multi-fichiers simultanés.
  - Fichiers volumineux (> 10 Go) sans saturation de mémoire RAM.
- **Reprise après interruption (Resume)** :
  - En cas de perte de signal Wi-Fi ou de redémarrage, le transfert reprend exactement au dernier chunk validé sans repartir à zéro.
- **Vérification d'intégrité** : Calcul et contrôle d'empreinte SHA-256 pour chaque fichier transféré.
- **Feedback UX en direct** :
  - Jauge de progression (pourcentage et octets transférés).
  - Vitesse instantanée (Mo/s).
  - Temps restant estimé (ETA).
  - Notification système à la fin du transfert.

### 4.2 Hors Périmètre du MVP (Prévu en Phase 2 / Versions ultérieures)
- Transfert direct Smartphone ↔ Smartphone.
- Relai via internet / serveur cloud de signalement (STUN/TURN).
- Synchronisation automatique de dossiers en continu (type Dropbox/Syncthing).
- Édition ou conversion de médias à la volée.

---

## 5. Exigences d'Expérience Utilisateur (UX)

### 5.1 Écran Principal Mobile (Android)
- Statut de connectivité clair (Connecté au Wi-Fi "Maison" / Hotspot actif).
- Liste des ordinateurs détectés à proximité avec nom de la machine et icône de système d'exploitation.
- Deux actions majeures bien visibles : **[ ENVOYER DES FICHIERS ]** et **[ RECEVOIR ]**.
- Panneau d'activité rétractable affichant l'historique récent des transferts.

### 5.2 Écran Desktop (Windows)
- Fenêtre compacte moderne et discrète, pouvant être minimisée dans la zone de notification (*System Tray*).
- Zone de glisser-déposer (*Drag & Drop*) immédiate : glisser un fichier sur l'icône du smartphone lance le transfert.
- Répertoire de destination configurable (par défaut : `C:\Users\<User>\Downloads\FastDrop\`).

### 5.3 Comportement en cas d'erreur
- Pas de code d'erreur opaque : messages explicites avec action corrective suggérée :
  - *Exemple* : *"Connexion interrompue à 74%. Reconnexion en cours... Cliquez sur Reprendre dès que le Wi-Fi est rétabli."*
  - *Exemple* : *"Espace disque insuffisant sur l'ordinateur (requis : 4.2 Go, disponible : 1.1 Go)."*
