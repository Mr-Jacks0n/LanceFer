import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import { MESSAGE_TYPES } from '../protocol/constants.js';
import { encodeFrame } from '../protocol/framing.js';

/**
 * Moteur d'Appairage Sécurisé FastDrop (PairingEngine)
 * Conforme au modèle SAS (Short Authentication String) de docs/architecture/security_model.md
 */
export class PairingEngine extends EventEmitter {
  /**
   * @param {object} localDevice
   * @param {string} localDevice.deviceId
   * @param {string} localDevice.deviceName
   * @param {string} [localDevice.platform]
   * @param {import('./trust_store.js').TrustStore} trustStore
   */
  constructor(localDevice, trustStore) {
    super();
    this.localDevice = localDevice;
    this.trustStore = trustStore;
  }

  /**
   * Dérive un code PIN à 6 chiffres (SAS) à partir de deux secrets éphémères
   * @param {string|Buffer} secretA
   * @param {string|Buffer} secretB
   * @returns {string} - Code formaté "123 456"
   */
  static deriveSasPin(secretA, secretB) {
    const bufA = Buffer.isBuffer(secretA) ? secretA : Buffer.from(String(secretA));
    const bufB = Buffer.isBuffer(secretB) ? secretB : Buffer.from(String(secretB));

    // Ordonnancement canonique pour que les deux côtés calculent le même code
    const combined = Buffer.compare(bufA, bufB) < 0
      ? Buffer.concat([bufA, bufB])
      : Buffer.concat([bufB, bufA]);

    const hash = crypto.createHash('sha256').update(combined).digest();
    const num = hash.readUInt32BE(0) % 1000000;
    const rawPin = num.toString().padStart(6, '0');
    return `${rawPin.slice(0, 3)} ${rawPin.slice(3)}`;
  }

  /**
   * Génère les données formatées pour affichage sous forme de QR Code
   * @param {string} ip
   * @param {number} port
   * @param {string} ephemeralSecret
   * @returns {string}
   */
  generateQrCodePayload(ip, port, ephemeralSecret) {
    const params = new URLSearchParams({
      id: this.localDevice.deviceId,
      name: this.localDevice.deviceName,
      platform: this.localDevice.platform || 'windows',
      ip,
      port: String(port),
      token: ephemeralSecret,
    });
    return `fastdrop://pair?${params.toString()}`;
  }

  /**
   * Initie un appairage en tant qu'émetteur
   * @param {import('node:net').Socket} socket
   * @param {import('../protocol/framing.js').FrameParser} parser
   * @param {Function} onPinPrompt - Callback (pin) pour validation humaine ou auto
   * @returns {Promise<{ success: boolean, deviceId: string, sasPin: string }>}
   */
  async initiatePairing(socket, parser, onPinPrompt) {
    const mySecret = crypto.randomBytes(32).toString('hex');

    const requestPayload = {
      deviceId: this.localDevice.deviceId,
      deviceName: this.localDevice.deviceName,
      platform: this.localDevice.platform || 'windows',
      ephemeralSecret: mySecret,
      timestamp: Date.now(),
    };

    return new Promise((resolve, reject) => {
      const onFrame = async ({ type, payload }) => {
        if (type === MESSAGE_TYPES.PAIR_RESPONSE) {
          parser.off('frame', onFrame);
          try {
            const resp = JSON.parse(payload.toString('utf8'));
            if (!resp.accepted) {
              return resolve({ success: false, reason: resp.reason || 'Rejeté par le destinataire' });
            }

            const expectedPin = PairingEngine.deriveSasPin(mySecret, resp.ephemeralSecret);

            // Présenter le PIN à l'utilisateur
            const userConfirmed = await onPinPrompt(expectedPin);
            if (!userConfirmed) {
              return resolve({ success: false, reason: 'Rejeté localement par l\'utilisateur' });
            }

            // Enregistrer l'appareil comme approuvé dans le TrustStore
            await this.trustStore.addTrustedDevice({
              deviceId: resp.deviceId,
              deviceName: resp.deviceName,
              publicKeyFingerprint: resp.publicKeyFingerprint || expectedPin,
              platform: resp.platform,
            });

            resolve({
              success: true,
              deviceId: resp.deviceId,
              deviceName: resp.deviceName,
              sasPin: expectedPin,
            });
          } catch (e) {
            reject(e);
          }
        } else if (type === MESSAGE_TYPES.ERROR_ABORT) {
          parser.off('frame', onFrame);
          reject(new Error(`Erreur lors de l'appairage : ${payload.toString('utf8')}`));
        }
      };

      parser.on('frame', onFrame);
      socket.write(encodeFrame(MESSAGE_TYPES.PAIR_REQUEST, requestPayload));
    });
  }

  /**
   * Traite une demande d'appairage en tant que récepteur
   * @param {import('node:net').Socket} socket
   * @param {object} pairRequest - Contenu de PAIR_REQUEST
   * @param {Function} onConfirmationPrompt - Callback (peerInfo, sasPin) -> boolean
   * @returns {Promise<{ accepted: boolean, sasPin: string }>}
   */
  async handlePairRequest(socket, pairRequest, onConfirmationPrompt) {
    // Vérifier si déjà approuvé
    const alreadyTrusted = await this.trustStore.isTrusted(pairRequest.deviceId);
    if (alreadyTrusted) {
      socket.write(encodeFrame(MESSAGE_TYPES.PAIR_RESPONSE, {
        accepted: true,
        deviceId: this.localDevice.deviceId,
        deviceName: this.localDevice.deviceName,
        platform: this.localDevice.platform,
        alreadyTrusted: true,
      }));
      return { accepted: true, alreadyTrusted: true };
    }

    const mySecret = crypto.randomBytes(32).toString('hex');
    const sasPin = PairingEngine.deriveSasPin(pairRequest.ephemeralSecret, mySecret);

    // Demander confirmation à l'utilisateur récepteur
    const accepted = await onConfirmationPrompt({
      deviceId: pairRequest.deviceId,
      deviceName: pairRequest.deviceName,
      platform: pairRequest.platform,
    }, sasPin);

    if (!accepted) {
      socket.write(encodeFrame(MESSAGE_TYPES.PAIR_RESPONSE, {
        accepted: false,
        reason: 'Refusé par l\'utilisateur récepteur',
      }));
      return { accepted: false, sasPin };
    }

    // Sauvegarder dans le TrustStore
    await this.trustStore.addTrustedDevice({
      deviceId: pairRequest.deviceId,
      deviceName: pairRequest.deviceName,
      publicKeyFingerprint: sasPin,
      platform: pairRequest.platform,
    });

    socket.write(encodeFrame(MESSAGE_TYPES.PAIR_RESPONSE, {
      accepted: true,
      deviceId: this.localDevice.deviceId,
      deviceName: this.localDevice.deviceName,
      platform: this.localDevice.platform,
      ephemeralSecret: mySecret,
      publicKeyFingerprint: sasPin,
    }));

    return { accepted: true, sasPin };
  }
}
