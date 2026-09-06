# SPÉCIFICATION DU PROTOCOLE FASTDROP v1 (FDSP v1)
## FastDrop Streaming Protocol — Version 1.0.0
**Auteur :** `lancefer_system_architect`  
**Statut :** Spécification Formelle  
**Port Réseau par Défaut :** TCP `42420` (Contrôle & Transfert) / UDP `42424` (Découverte Fallback)

---

## 1. Vue d'Ensemble du Protocole

Le protocole **FDSP v1** est un protocole de niveau applicatif conçu pour opérer au-dessus d'une connexion TCP directe (sécurisée par TLS ou session chiffrée locale). Il se compose :
1. D'une phase de **Découverte & Présence** (mDNS / UDP).
2. D'une phase d'**Appairage & Négociation de session** (Handshake & Capabilities).
3. D'une phase de **Proposition de transfert** (Métadonnées du lot de fichiers).
4. D'une phase de **Streaming de données segmentées** (Chunks avec acquittement glissant).
5. D'une phase de **Validation d'intégrité & Clôture**.

---

## 2. Format de Cadrage des Trames (Framing)

Chaque message échangé sur le socket TCP utilise un en-tête fixe de **8 octets**, suivi d'un corps de taille variable (*Payload*) :

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Magic "FD"           | Version (0x01)| Message Type  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Payload Length (32 bits)               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                   Payload Data (Variable)                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Détail de l'En-tête (Header)
- **Magic Bytes (2 octets)** : `0x46, 0x44` (ASCII `"FD"`). Permet de rejeter immédiatement toute connexion invalide.
- **Protocol Version (1 octet)** : `0x01` pour FDSP v1.
- **Message Type (1 octet)** : Identifiant du type de message (voir section 3).
- **Payload Length (4 octets - Big Endian uint32)** : Taille du payload en octets (jusqu'à 4 Go par message, typiquement 1 Mo pour un chunk de données).

---

## 3. Types de Messages Protocolaire

| Code Type | Nom du Message | Direction | Rôle | Format du Payload |
|---|---|---|---|---|
| `0x01` | `HELLO_REQUEST` | Client → Serveur | Initialisation de session, présentation | JSON |
| `0x02` | `HELLO_RESPONSE` | Serveur → Client | Acceptation, présentation mutuelle | JSON |
| `0x03` | `PAIR_REQUEST` | Émetteur → Récepteur | Demande d'appairage avec défi cryptographique | JSON |
| `0x04` | `PAIR_RESPONSE` | Récepteur → Émetteur | Réponse de l'utilisateur (Accepté / Refusé) | JSON |
| `0x10` | `TRANSFER_PROPOSAL` | Émetteur → Récepteur | Annonce du lot de fichiers (nom, taille, hachage) | JSON |
| `0x11` | `TRANSFER_DECISION` | Récepteur → Émetteur | Acceptation du transfert ou rejet | JSON |
| `0x12` | `TRANSFER_RESUME_REQ`| Récepteur → Émetteur | Demande de reprise avec liste des chunks manquants | JSON |
| `0x20` | `CHUNK_DATA` | Émetteur → Récepteur | Bloc de données d'un fichier | Binaire (En-tête chunk + octets bruts) |
| `0x21` | `CHUNK_ACK` | Récepteur → Émetteur | Acquittement d'un chunk reçu et écrit sur disque | Binaire (16 octets) |
| `0x30` | `TRANSFER_COMPLETE` | Émetteur → Récepteur | Fin de transmission d'un fichier | JSON |
| `0x31` | `CHECKSUM_VERIFIED` | Récepteur → Émetteur | Confirmation d'intégrité SHA-256 finale | JSON |
| `0x40` | `TRANSFER_PAUSE` | Bidirectionnel | Demande de mise en pause | JSON |
| `0x41` | `TRANSFER_CANCEL` | Bidirectionnel | Annulation immédiate du transfert | JSON |
| `0xFF` | `ERROR_ABORT` | Bidirectionnel | Erreur critique et fermeture immédiate | JSON |

---

## 4. Spécification Détaillée des Échanges

### 4.1 Négociation de Capacités (`HELLO_REQUEST` / `HELLO_RESPONSE`)
Exemple de payload JSON échangé :
```json
{
  "device_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "device_name": "PC-BUREAU-JACKSON",
  "platform": "windows",
  "app_version": "0.1.0",
  "protocol_version": 1,
  "supported_chunk_sizes": [524288, 1048576, 2097152],
  "capabilities": ["resume", "sha256", "fast_ack", "multi_stream"]
}
```

### 4.2 Proposition de Transfert (`TRANSFER_PROPOSAL`)
Annonce les métadonnées avant l'envoi de données brutes :
```json
{
  "transfer_id": "tr-20260906-00124",
  "total_size": 4294967296,
  "files_count": 1,
  "files": [
    {
      "file_id": "f-001",
      "relative_path": "Vacances_4K.mp4",
      "size": 4294967296,
      "chunk_size": 1048576,
      "total_chunks": 4096,
      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
  ]
}
```

### 4.3 Structure Binaire d'un Bloc de Données (`CHUNK_DATA`)
Pour éviter la surcharge JSON pendant le transfert de plusieurs giga-octets, le payload du message `0x20` est strictement binaire :
```text
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      File Index (16 bits)     | Reserved (16b)|
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Chunk Index (32 bits)                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Offset in File (64 bits)                 |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Raw Chunk Data (N octets)                |
|                      (Taille par défaut : 1 048 576 octets)   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 4.4 Régulation de Débit & Contrôle de Flux (Backpressure)
- L'émetteur envoie des chunks dans une **fenêtre glissante** (par défaut : 8 chunks en vol, soit 8 Mo).
- Le récepteur émet un `CHUNK_ACK` dès que le bloc est écrit et synchronisé sur son disque local.
- Si le réseau ou le disque de destination ralentit, l'émetteur attend la réception des acquittements avant d'extraire de nouveaux blocs de son disque.
- **Résultat garanti :** Consommation mémoire de l'émetteur et du récepteur strictement plafonnée à moins de 32 Mo, même pour un fichier de 100 Go.

---

## 5. Algorithme de Reprise (Resume Engine)

1. En cours de transfert, le récepteur enregistre sur disque un fichier d'état local `.fastdrop_resume_<transfer_id>.tmp`.
2. Ce fichier maintient un **bitmap** de tous les `chunk_index` validés et hachés.
3. Si la connexion est rompue à 87% :
   - À la reconnexion, l'émetteur renvoie un `TRANSFER_PROPOSAL` avec le même `transfer_id`.
   - Le récepteur répond par un `TRANSFER_RESUME_REQ` contenant l'intervalle des chunks manquants (ex: `chunks 3564 à 4096`).
   - L'émetteur positionne le pointeur de lecture directement au bon offset (`offset = 3564 * chunk_size`) et poursuit l'émission sans retransférer les 3563 premiers mégaoctets.
4. Lorsque le dernier chunk est reçu, le récepteur recalcule l'empreinte SHA-256 globale du fichier final et valide le succès par `CHECKSUM_VERIFIED`.
