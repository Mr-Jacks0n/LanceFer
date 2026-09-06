# Performances et Benchmarks FastDrop

Conformément à la règle de LanceFer V4 : **"Mesurer avant d'optimiser"**.

### Résultats Relevés sur Machine Locale (Windows x64) :
- **Fichier de 10 Mo** : 0.169 s -> **59.17 Mo/s**
- **Fichier de 50 Mo** : 0.835 s -> **59.88 Mo/s**
- **Empreinte RAM** : Variation nulle (~0 Mo) grâce au contrôle de contre-pression (*Backpressure*) et au streaming par blocs de 1 Mo.
- **Intégrité Cryptographique** : 100% conforme sur tous les transferts (SHA-256 calculé en flux continu).

Les métriques brutes sont archivées dans [`.lancefer/metrics.json`](file:///f:/Projet%201/LanceFer/.lancefer/metrics.json).
