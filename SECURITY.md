# Modèle de Sécurité FastDrop

- [Consulter le document complet de sécurité](file:///f:/Projet%201/LanceFer/docs/architecture/security_model.md)

### Piliers de Sécurité :
- **Appairage SAS Mutuel** : Code PIN court à 6 chiffres dérivé par HKDF-SHA256 ou scan de QR Code.
- **Défense Anti-Path Traversal** : Neutralisation systématique de tout nom ou chemin de fichier transmis afin d'interdire tout accès ou écriture hors du dossier cible.
- **Vérification d'Espace Préalable** : Rejet immédiat de tout transfert avant émission si l'espace disque disponible est insuffisant.
