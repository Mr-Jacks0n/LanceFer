import dgram from 'node:dgram';
import { EventEmitter } from 'node:events';
import os from 'node:os';

export const DEFAULT_DISCOVERY_PORT = 42424;
export const DEFAULT_BEACON_INTERVAL_MS = 2000;
export const DEFAULT_PEER_TTL_MS = 6000;

/**
 * Moteur de Découverte Réseau FastDrop (DiscoveryEngine)
 * Permet la détection automatique des pairs sur réseau local sans configuration
 */
export class DiscoveryEngine extends EventEmitter {
  /**
   * @param {object} localDeviceInfo
   * @param {string} localDeviceInfo.deviceId - Identifiant unique
   * @param {string} localDeviceInfo.deviceName - Nom lisible (ex: PC-Jackson)
   * @param {string} localDeviceInfo.platform - windows, android, linux, macos
   * @param {number} [localDeviceInfo.transferPort] - Port TCP FastDrop (défaut 42420)
   * @param {number} [localDeviceInfo.version] - Version du protocole
   * @param {Array<string>} [localDeviceInfo.ownAddresses] - Liste des adresses IP propres à l'hôte
   * @param {Array<string>} [localDeviceInfo.ownIps] - Alias de ownAddresses
   */
  constructor(localDeviceInfo) {
    super();
    this.localDevice = {
      deviceId: localDeviceInfo.deviceId,
      deviceName: localDeviceInfo.deviceName,
      platform: localDeviceInfo.platform || 'windows',
      transferPort: localDeviceInfo.transferPort || 42420,
      version: localDeviceInfo.version || 1,
    };

    // Suivi multi-interfaces des propres adresses IP de l'hôte
    this.ownIps = new Set();
    const initialAddresses = localDeviceInfo.ownAddresses || localDeviceInfo.ownIps;
    if (Array.isArray(initialAddresses) || initialAddresses instanceof Set) {
      for (const ip of initialAddresses) {
        this.addOwnIp(ip);
      }
    } else {
      // Découverte dynamique des interfaces physiques/externes (non-internes)
      try {
        const interfaces = os.networkInterfaces();
        for (const name of Object.keys(interfaces)) {
          for (const iface of interfaces[name]) {
            if (iface && iface.address && !iface.internal) {
              this.addOwnIp(iface.address);
            }
          }
        }
      } catch (_) {}
    }

    this.socket = null;
    this.peers = new Map(); // deviceId -> PeerInfo
    this.beaconTimer = null;
    this.cleanupTimer = null;
    this.running = false;
    this.discoveryPort = DEFAULT_DISCOVERY_PORT;
    this.targetPort = DEFAULT_DISCOVERY_PORT;
    this.broadcastAddress = '255.255.255.255';
    this.broadcastAddresses = ['255.255.255.255'];
  }

  /**
   * Enregistre une adresse IP comme appartenant à l'hôte local
   * @param {string} ip
   */
  addOwnIp(ip) {
    if (!ip) return;
    const clean = String(ip).replace(/^::ffff:/i, '').trim().toLowerCase();
    this.ownIps.add(clean);
    this.ownIps.add(String(ip).toLowerCase());
  }

  /**
   * Définit explicitement l'ensemble des adresses IP de l'hôte
   * @param {Array<string>|Set<string>} ips
   */
  setOwnIps(ips) {
    this.ownIps.clear();
    if (Array.isArray(ips) || ips instanceof Set) {
      for (const ip of ips) {
        this.addOwnIp(ip);
      }
    }
  }

  /**
   * Vérifie si une adresse IP correspond à l'hôte local
   * @param {string} addr
   * @returns {boolean}
   */
  isOwnAddress(addr) {
    if (!addr) return false;
    const clean = String(addr).replace(/^::ffff:/i, '').trim().toLowerCase();
    return this.ownIps.has(clean) || this.ownIps.has(String(addr).toLowerCase());
  }

  /**
   * Démarre l'écoute et l'annonce de présence sur le réseau local
   * @param {object} [options]
   * @param {number} [options.port] - Port UDP d'écoute
   * @param {number} [options.targetPort] - Port UDP de destination (défaut: options.port)
   * @param {string} [options.broadcastAddress] - Adresse de broadcast
   * @param {number} [options.beaconIntervalMs] - Intervalle d'émission (ms)
   * @param {number} [options.peerTtlMs] - Durée de vie d'un pair sans signal (ms)
   * @returns {Promise<void>}
   */
  async start(options = {}) {
    if (this.running) return;

    this.discoveryPort = options.port || DEFAULT_DISCOVERY_PORT;
    this.targetPort = options.targetPort || this.discoveryPort;
    this.broadcastAddress = options.broadcastAddress || '255.255.255.255';
    this.broadcastAddresses = Array.isArray(options.broadcastAddresses) && options.broadcastAddresses.length > 0
      ? options.broadcastAddresses
      : [this.broadcastAddress];
    const beaconInterval = options.beaconIntervalMs || DEFAULT_BEACON_INTERVAL_MS;
    const peerTtl = options.peerTtlMs || DEFAULT_PEER_TTL_MS;

    this.socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });

    this.socket.on('message', (msg, rinfo) => {
      this._handleIncomingPacket(msg, rinfo);
    });

    this.socket.on('error', err => {
      this.emit('error', err);
    });

    await new Promise((resolve, reject) => {
      this.socket.bind(this.discoveryPort, () => {
        try {
          this.socket.setBroadcast(true);
        } catch (e) {
          // Sur certains OS ou interfaces restreintes, l'activation peut échouer temporairement
        }
        resolve();
      });
    });

    this.running = true;

    // Émettre immédiatement un premier beacon
    this.broadcastBeacon();

    // Timer d'annonce périodique
    this.beaconTimer = setInterval(() => {
      this.broadcastBeacon();
    }, beaconInterval);

    // Timer de nettoyage des pairs périmés (TTL)
    this.cleanupTimer = setInterval(() => {
      this._checkPeerTtl(peerTtl);
    }, Math.floor(peerTtl / 2));

    this.emit('started', { port: this.discoveryPort });
  }

  /**
   * Arrête le moteur de découverte
   * @returns {Promise<void>}
   */
  async stop() {
    if (!this.running) return;
    this.running = false;

    if (this.beaconTimer) {
      clearInterval(this.beaconTimer);
      this.beaconTimer = null;
    }
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }

    if (this.socket) {
      await new Promise(resolve => this.socket.close(resolve));
      this.socket = null;
    }

    this.peers.clear();
    this.emit('stopped');
  }

  /**
   * Diffuse un signal de présence (Beacon) en broadcast
   */
  broadcastBeacon() {
    if (!this.running || !this.socket) return;

    const payload = JSON.stringify({
      magic: 'FD',
      type: 'BEACON',
      deviceId: this.localDevice.deviceId,
      deviceName: this.localDevice.deviceName,
      platform: this.localDevice.platform,
      transferPort: this.localDevice.transferPort,
      version: this.localDevice.version,
      timestamp: Date.now(),
    });

    const msg = Buffer.from(payload, 'utf8');
    const targets = Array.isArray(this.broadcastAddresses) && this.broadcastAddresses.length > 0
      ? this.broadcastAddresses
      : [this.broadcastAddress];

    for (const bcast of targets) {
      this.socket.send(msg, 0, msg.length, this.targetPort, bcast, () => {});
    }
  }

  /**
   * Envoie une sonde de recherche active (Probe) pour obtenir des réponses immédiates
   */
  probe() {
    if (!this.running || !this.socket) return;

    const payload = JSON.stringify({
      magic: 'FD',
      type: 'PROBE',
      deviceId: this.localDevice.deviceId,
      timestamp: Date.now(),
    });

    const msg = Buffer.from(payload, 'utf8');
    this.socket.send(msg, 0, msg.length, this.targetPort, this.broadcastAddress);
  }

  /**
   * Sonde manuelle d'une IP directe (mode secours / AP Isolation)
   * @param {string} ip
   * @param {number} [port]
   */
  pingPeer(ip, port = this.discoveryPort) {
    if (!this.running || !this.socket) return;

    const payload = JSON.stringify({
      magic: 'FD',
      type: 'PROBE',
      deviceId: this.localDevice.deviceId,
      timestamp: Date.now(),
    });

    const msg = Buffer.from(payload, 'utf8');
    this.socket.send(msg, 0, msg.length, port, ip);
  }

  /**
   * Traitement interne des paquets UDP reçus
   * @private
   */
  _handleIncomingPacket(msg, rinfo) {
    try {
      const data = JSON.parse(msg.toString('utf8'));
      if (data.magic !== 'FD') return;

      // Ignorer ses propres annonces
      if (data.deviceId === this.localDevice.deviceId) return;

      // Filtrer les annonces provenant des propres adresses IP de l'hôte
      if (rinfo && rinfo.address && this.isOwnAddress(rinfo.address)) return;

      if (data.type === 'PROBE') {
        // Répondre immédiatement en unicast à l'émetteur
        this._sendBeaconTo(rinfo.address, rinfo.port);
        return;
      }

      if (data.type === 'BEACON') {
        const peerId = data.deviceId;
        const now = Date.now();
        const isNew = !this.peers.has(peerId);

        // Protection anti-DoS : éviction LRU si le nombre de pairs atteint le seuil maximal
        if (isNew && this.peers.size >= (this.maxPeers || 50)) {
          let oldestId = null;
          let oldestTime = Infinity;
          for (const [id, peer] of this.peers.entries()) {
            if (peer.lastSeen < oldestTime) {
              oldestTime = peer.lastSeen;
              oldestId = id;
            }
          }
          if (oldestId) {
            this.peers.delete(oldestId);
          }
        }

        const cleanName = String(data.deviceName || 'Inconnu')
          .replace(/[\x00-\x1F\x7F]/g, '')
          .slice(0, 64);

        const peerInfo = {
          deviceId: data.deviceId,
          deviceName: cleanName,
          platform: data.platform,
          address: rinfo.address,
          transferPort: data.transferPort || 42420,
          version: data.version || 1,
          lastSeen: now,
        };

        this.peers.set(peerId, peerInfo);

        if (isNew) {
          this.emit('peer_discovered', peerInfo);
          this.emit('peer_found', peerInfo);
        } else {
          this.emit('peer_updated', peerInfo);
        }
      }
    } catch {
      // Ignorer les paquets non JSON ou malformés
    }
  }

  /**
   * Traitement d'un objet d'annonce reçu (utilisé directement ou via socket)
   * @param {object} announcement
   * @param {object} rinfo
   */
  handleAnnouncement(announcement, rinfo = { address: '127.0.0.1', port: 42424 }) {
    const payload = Buffer.from(JSON.stringify({
      magic: 'FD',
      type: 'BEACON',
      ...announcement,
    }), 'utf8');
    this._handleIncomingPacket(payload, rinfo);
  }

  /**
   * Envoi d'un beacon direct à une IP spécifique
   * @private
   */
  _sendBeaconTo(address, port) {
    if (!this.running || !this.socket) return;

    const payload = JSON.stringify({
      magic: 'FD',
      type: 'BEACON',
      deviceId: this.localDevice.deviceId,
      deviceName: this.localDevice.deviceName,
      platform: this.localDevice.platform,
      transferPort: this.localDevice.transferPort,
      version: this.localDevice.version,
      timestamp: Date.now(),
    });

    const msg = Buffer.from(payload, 'utf8');
    this.socket.send(msg, 0, msg.length, port, address);
  }

  /**
   * Vérification des délais d'inactivité des pairs
   * @private
   */
  _checkPeerTtl(ttlMs) {
    const now = Date.now();
    for (const [peerId, peer] of this.peers.entries()) {
      if (now - peer.lastSeen > ttlMs) {
        this.peers.delete(peerId);
        this.emit('peer_lost', peer);
      }
    }
  }

  /**
   * Renvoie la liste de tous les pairs actifs actuellement détectés
   * @returns {Array<object>}
   */
  getPeers() {
    return Array.from(this.peers.values());
  }

  /**
   * Recherche un pair précis par son deviceId
   * @param {string} deviceId
   * @returns {object|null}
   */
  getPeer(deviceId) {
    return this.peers.get(deviceId) || null;
  }
}
