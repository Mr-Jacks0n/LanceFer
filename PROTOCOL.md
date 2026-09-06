# Spécification du Protocole FastDrop (FDSP v1)

Le protocole FDSP v1 régit l'ensemble des échanges entre pairs FastDrop sur le réseau local.

- [Consulter la spécification complète du protocole v1](file:///f:/Projet%201/LanceFer/docs/architecture/fastdrop_protocol_v1.md)

### Résumé des Principes Clés :
1. **En-tête de Cadrage Fixe (8 octets)** : `Magic (FD) + Version (0x01) + MessageType + Length`.
2. **Messages JSON de Contrôle** : Négociation de session (`HELLO`), appairage (`PAIR`), proposition (`TRANSFER_PROPOSAL`), validation (`CHECKSUM_VERIFIED`).
3. **Payloads Binaires de Données** : `CHUNK_DATA` avec `FileIndex`, `ChunkIndex`, `Offset` et octets bruts pour éliminer toute surcharge CPU/JSON sur gros fichiers.
4. **Acquittement Glissant (Backpressure)** : `CHUNK_ACK` de 16 octets permettant de réguler l'émission à 8 blocs max en vol.
