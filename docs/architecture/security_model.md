# MODÈLE DE SÉCURITÉ & PROTOCOLE D'APPAIRAGE FASTDROP
## Security & Threat Model Document
**Auteur :** `lancefer_security_auditor`  
**Statut :** Spécification de Sécurité Validée  
**Version :** 1.0.0

---

## 1. Modèle de Menace (Threat Model)

Dans un environnement de réseau local Wi-Fi (domestique, colocation, espace de coworking, aéroport, hotspot) :
1. **Écoute passive (*Eavesdropping*)** : Un acteur malveillant connecté au même Wi-Fi peut capturer les trames réseau si elles ne sont pas chiffrées.
2. **Attaque de l'Homme du Milieu (*Man-In-The-Middle / MITM*)** : Altération des paquets en vol ou redirection DNS/ARP locale.
3. **Appareils non autorisés (*Rogue Device*)** : Une machine tierce tente d'envoyer des fichiers non sollicités ou d'aspirer des données.
4. **Attaque par traversée de répertoire (*Path Traversal*)** : Un fichier piégé avec un chemin relatif malveillant (ex: `../../../../Windows/System32/evil.dll`) tente d'écraser des fichiers système critiques.
5. **Déni de service local (*Resource Exhaustion*)** : Envoi de fichiers de taille infinie pour saturer le stockage ou la RAM.

---

## 2. Protocole d'Appairage Mutuel (Pairing & Trust)

Pour interdire toute connexion fortuite ou hostile, un protocole d'appairage à confirmation visuelle est appliqué lors du premier contact entre deux appareils.

```text
Appareil A (Émetteur)                     Appareil B (Récepteur)
        │                                         │
        │ 1. PAIR_REQUEST (Clé publique éphémère)  │
        ├────────────────────────────────────────►│
        │                                         │
        │ 2. PAIR_CHALLENGE (Clé publique B)       │
        │◄────────────────────────────────────────┤
        │                                         │
  Calcul secret partagé                     Calcul secret partagé
  (ECDH Curve25519)                         (ECDH Curve25519)
        │                                         │
  Dérivation SAS (6 chiffres)               Dérivation SAS (6 chiffres)
  ex: "749 182"                             ex: "749 182"
        │                                         │
  Affichage UI                              Affichage UI
  "Vérifiez le code : 749 182"              "Accepter 749 182 de A ?"
        │                                         │
        │              [ ACCEPTER ]               │
        │◄────────────────────────────────────────┤
        │                                         │
  Appareil de confiance enregistré          Appareil de confiance enregistré
```

### Règles d'Appairage :
- Dérivation d'une **Short Authentication String (SAS)** : Code à 6 chiffres dérivé du secret partagé ECDH (Curve25519) via HKDF-SHA256.
- Alternative mobile : Scan direct d'un **QR Code** contenant le secret éphémère généré par le PC.
- Une fois approuvé, l'empreinte de la clé publique de l'appareil est conservée dans le magasin local de confiance (*Trust Store*). L'utilisateur peut révoquer un appareil à tout moment.

---

## 3. Chiffrement de Session & Transport

- **Protocole :** TLS 1.3 avec certificats auto-signés épinglés (*Certificate Pinning* sur les empreintes ECDH échangées lors de l'appairage) ou session directe **Noise Protocol (XX Pattern)**.
- **Suite Cryptographique :** `ChaCha20-Poly1305` ou `AES-256-GCM` pour le chiffrement symétrique haute vitesse des chunks de données.
- **PFS (Perfect Forward Secrecy) :** Renouvellement des clés de session à chaque transfert.

---

## 4. Prévention Absolue du "Path Traversal"

L'agent de sécurité impose une règle de validation inviolable sur le récepteur avant toute écriture sur le système de fichiers.

### Algorithme de Sanitisation des Chemins :
```text
Entrée brute :  "../../../../Windows/System32/cmd.exe"
1. Extraction du nom de base uniquement (suppression de tout préfixe de répertoire ou de lecteur).
2. Remplacement des caractères interdits sous Windows (< > : " / \ | ? * et octets nuls).
3. Détection des noms de périphériques réservés Windows (CON, PRN, AUX, NUL, COM1..9, LPT1..9).
4. Concaténation stricte au répertoire de destination validé :
   DossierCible = "C:\Users\<User>\Downloads\FastDrop\"
   CheminFinal = Path.Combine(DossierCible, NomSainNettoye)
5. Vérification canonique : Path.GetFullPath(CheminFinal).StartsWith(DossierCible) == true
Sortie saine :  "C:\Users\<User>\Downloads\FastDrop\cmd.exe"
```

Si le contrôle échoue : le transfert est immédiatement interrompu avec une alerte de sécurité et le fichier est rejeté.

---

## 5. Protection Contre l'Épuisement des Ressources

1. **Vérification d'Espace Préalable** : Dès la réception de `TRANSFER_PROPOSAL`, le récepteur vérifie que l'espace disque disponible est supérieur à `total_size + 500 Mo`. Si l'espace est insuffisant, le transfert est refusé avant même l'envoi du premier octet.
2. **Taille Maximale de Chunk** : Tout chunk dont la taille déclarée excède 4 Mo est immédiatement rejeté comme anormal.
3. **Timeout Réseau** : Fermeture automatique de toute socket inactive depuis plus de 15 secondes sans signal de vie (*Keepalive*).
