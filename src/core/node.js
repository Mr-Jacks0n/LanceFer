import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs/promises';
import { EventEmitter } from 'node:events';
import { DiscoveryEngine, DEFAULT_DISCOVERY_PORT } from '../discovery/discovery_engine.js';
import { TrustStore } from '../security/trust_store.js';
import { PairingEngine } from '../security/pairing_engine.js';
import { TransferSender, TransferReceiver } from './transfer_engine.js';
import { FrameParser } from '../protocol/framing.js';
import { MESSAGE_TYPES } from '../protocol/constants.js';

export class FastDropNode extends EventEmitter {
  constructor(options = {}) {
    super();
    this.deviceId = options.deviceId || `dev-${Math.random().toString(36).slice(2, 8)}`;
    this.deviceName = options.deviceName || os.hostname() || 'FastDrop-Node';
    this.platform = options.platform || process.platform;
    this.transferPort = options.transferPort || 42420;
    this.discoveryPort = options.discoveryPort || DEFAULT_DISCOVERY_PORT;
    this.storageDir = options.storageDir || path.resolve('tmp/fastdrop_storage');
    this.downloadDir = options.downloadDir || path.resolve('tmp/downloads');
    this.onPinPrompt = options.onPinPrompt || (async () => true);
    this.trustStore = new TrustStore(this.storageDir);
    this.pairingEngine = new PairingEngine({ deviceId: this.deviceId, deviceName: this.deviceName, platform: this.platform }, this.trustStore);
    this.discoveryEngine = new DiscoveryEngine({ deviceId: this.deviceId, deviceName: this.deviceName, platform: this.platform, transferPort: this.transferPort });
    this.tcpServer = null;
    this.activeSockets = new Set();
    this.running = false;
  }

  async start(networkOptions = {}) {
    if (this.running) return;
    await fs.mkdir(this.downloadDir, { recursive: true });
    await this.trustStore.load();
    this.tcpServer = net.createServer(socket => this._handleIncomingConnection(socket));
    await new Promise((resolve, reject) => {
      this.tcpServer.once('error', reject);
      this.tcpServer.listen(this.transferPort, '0.0.0.0', () => {
        this.transferPort = this.tcpServer.address().port;
        resolve();
      });
    });
    this.discoveryEngine.on('peer_discovered', async peer => {
      const isTrusted = await this.trustStore.isTrusted(peer.deviceId);
      this.emit('peer:found', { ...peer, isTrusted });
    });
    this.discoveryEngine.on('peer_lost', peer => this.emit('peer:lost', peer));
    await this.discoveryEngine.start({ port: this.discoveryPort, broadcastAddress: networkOptions.broadcastAddress || '255.255.255.255' });
    this.running = true;
    this.emit('started', { deviceId: this.deviceId, port: this.transferPort });
  }

  async stop() {
    if (!this.running) return;
    this.running = false;
    await this.discoveryEngine.stop();
    for (const socket of this.activeSockets) socket.destroy();
    this.activeSockets.clear();
    if (this.tcpServer) {
      await new Promise(resolve => this.tcpServer.close(resolve));
      this.tcpServer = null;
    }
  }

  getPeers() {
    return this.discoveryEngine.getPeers().map(p => ({ ...p, isTrusted: this.trustStore.devices.has(p.deviceId) }));
  }

  async sendToPeer(peerId, filePath, options = {}) {
    const peer = this.discoveryEngine.getPeer(peerId);
    if (!peer) throw new Error(`Pair ${peerId} introuvable`);
    const socket = net.connect({ host: peer.address, port: peer.transferPort });
    await new Promise((res, rej) => { socket.once('connect', res); socket.once('error', rej); });
    const sender = new TransferSender(socket);
    sender.on('progress', p => this.emit('transfer:progress', { peerId, ...p }));
    const stats = await sender.sendFile(filePath, options);
    socket.end();
    return stats;
  }

  _handleIncomingConnection(socket) {
    this.activeSockets.add(socket);
    socket.on('close', () => this.activeSockets.delete(socket));
    const parser = new FrameParser();
    socket.on('data', chunk => parser.push(chunk));
    const onInitial = async ({ type, payload }) => {
      parser.off('frame', onInitial);
      if (type === MESSAGE_TYPES.PAIR_REQUEST) {
        await this.pairingEngine.handlePairRequest(socket, JSON.parse(payload.toString('utf8')), (peer, pin) => this.onPinPrompt(peer, pin));
      } else if (type === MESSAGE_TYPES.TRANSFER_PROPOSAL) {
        const receiver = new TransferReceiver(socket, this.downloadDir);
        receiver.on('progress', p => this.emit('transfer:progress', p));
        receiver.parser.emit('frame', { type, payload });
        try {
          const result = await receiver.start();
          this.emit('transfer:completed', result);
        } catch (err) {
          this.emit('transfer:error', err);
        }
      }
    };
    parser.on('frame', onInitial);
  }
}
