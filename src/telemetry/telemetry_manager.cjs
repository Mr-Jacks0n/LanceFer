'use strict';

/**
 * LanceDrop Telemetry & Monitoring Manager
 * 
 * Intègre Sentry (crash reporting & exception tracking) et Microsoft Clarity (UX / Session analytics)
 * avec une politique stricte de confidentialité (Zero-PII / Zero Data Leakage) :
 * - Masquage automatique des chemins de fichiers locaux (`C:\Users\username\...` -> `[USER_HOME]\...`)
 * - Masquage des tokens d'appairage, PIN, clés AES et payloads de fichiers
 * - Anonymisation des adresses IP locales
 * - Fonctionnement 100% résilient hors-ligne (No-Op transparent sans blocage du transfert)
 */

const os = require('os');
const path = require('path');

class TelemetryManager {
  constructor(options = {}) {
    this.appName = options.appName || 'LanceDrop';
    this.appVersion = options.appVersion || '0.1.0';
    this.sentryDsn = options.sentryDsn || process.env.LANCEDROP_SENTRY_DSN || '';
    this.clarityId = options.clarityId || process.env.LANCEDROP_CLARITY_ID || 'lancedrop_local';
    this.environment = options.environment || (process.env.NODE_ENV === 'production' ? 'production' : 'development');
    
    this.breadcrumbs = [];
    this.maxBreadcrumbs = 50;
    this.isOnline = true;
    this.userHome = os.homedir();
    this.userName = os.userInfo ? os.userInfo().username : '';
    this.initialized = false;
  }

  /**
   * Initialise le moteur de télémétrie
   */
  init() {
    if (this.initialized) return;
    this.initialized = true;

    this.addBreadcrumb('lifecycle', 'TelemetryManager initialized', {
      version: this.appVersion,
      platform: os.platform(),
      arch: os.arch()
    });

    // Enregistrement des hooks globaux d'exceptions Node.js
    if (typeof process !== 'undefined') {
      process.on('uncaughtException', (err) => {
        this.captureException(err, { source: 'uncaughtException' });
      });

      process.on('unhandledRejection', (reason) => {
        const err = reason instanceof Error ? reason : new Error(String(reason));
        this.captureException(err, { source: 'unhandledRejection' });
      });
    }
  }

  /**
   * Nettoie et anonymise toute chaîne contenant des informations sensibles ou privées
   * @param {string} text 
   * @returns {string}
   */
  scrub(text) {
    if (typeof text !== 'string') {
      if (text instanceof Error) {
        text = text.stack || text.message;
      } else {
        try {
          text = JSON.stringify(text);
        } catch (_) {
          text = String(text);
        }
      }
    }

    let clean = text;

    // 1. Masquer le chemin Home de l'utilisateur Windows/Unix
    if (this.userHome) {
      const escapedHome = this.userHome.replace(/\\/g, '\\\\');
      clean = clean.replace(new RegExp(escapedHome, 'gi'), '[USER_HOME]');
    }

    // 2. Masquer le nom d'utilisateur Windows
    if (this.userName && this.userName.length > 2) {
      clean = clean.replace(new RegExp(`Users\\\\${this.userName}`, 'gi'), 'Users\\[USER]');
      clean = clean.replace(new RegExp(`home/${this.userName}`, 'gi'), 'home/[USER]');
    }

    // 3. Masquer les clés de session hexadécimales 256 bits (64 car.)
    clean = clean.replace(/[0-9a-fA-F]{64}/g, '[REDACTED_KEY_256]');

    // 4. Masquer les codes PIN 6 chiffres
    clean = clean.replace(/(?:pin|code)["']?\s*[:=]\s*["']?(\d{6})["']?/gi, 'pin: "[REDACTED_PIN]"');

    // 5. Masquer les adresses IP privées (conserver le préfixe de sous-réseau pour diagnostic)
    clean = clean.replace(/\b(192\.168\.)\d{1,3}\.\d{1,3}\b/g, '$1*.*');
    clean = clean.replace(/\b(10\.)\d{1,3}\.\d{1,3}\.\d{1,3}\b/g, '$1*.*.*');
    clean = clean.replace(/\b(172\.(?:1[6-9]|2\d|3[01])\.)\d{1,3}\.\d{1,3}\b/g, '$1*.*');

    return clean;
  }

  /**
   * Enregistre un jalon ou événement de cycle de vie (Breadcrumb)
   * @param {string} category 
   * @param {string} message 
   * @param {object} [data] 
   */
  addBreadcrumb(category, message, data = {}) {
    const scrubbedData = {};
    if (data && typeof data === 'object') {
      for (const [key, val] of Object.entries(data)) {
        if (key.toLowerCase().includes('key') || key.toLowerCase().includes('pin') || key.toLowerCase().includes('secret')) {
          scrubbedData[key] = '[REDACTED]';
        } else if (typeof val === 'string') {
          scrubbedData[key] = this.scrub(val);
        } else {
          scrubbedData[key] = val;
        }
      }
    }

    const crumb = {
      timestamp: Date.now(),
      category: category || 'app',
      message: this.scrub(message),
      data: scrubbedData
    };

    this.breadcrumbs.push(crumb);
    if (this.breadcrumbs.length > this.maxBreadcrumbs) {
      this.breadcrumbs.shift();
    }
  }

  /**
   * Capture et signale une erreur ou exception
   * @param {Error|string} error 
   * @param {object} [context] 
   */
  captureException(error, context = {}) {
    const errObj = error instanceof Error ? error : new Error(String(error));
    const scrubbedMessage = this.scrub(errObj.message);
    const scrubbedStack = this.scrub(errObj.stack || '');

    const eventPayload = {
      event_id: Math.random().toString(36).substring(2, 15),
      timestamp: new Date().toISOString(),
      platform: 'node',
      level: 'error',
      release: `${this.appName}@${this.appVersion}`,
      environment: this.environment,
      exception: {
        values: [
          {
            type: errObj.name || 'Error',
            value: scrubbedMessage,
            stacktrace: scrubbedStack
          }
        ]
      },
      breadcrumbs: [...this.breadcrumbs],
      extra: {
        ...context,
        os_platform: os.platform(),
        os_release: os.release(),
        total_mem_mb: Math.round(os.totalmem() / (1024 * 1024)),
        free_mem_mb: Math.round(os.freemem() / (1024 * 1024))
      }
    };

    // Mode Sentry actif uniquement si un DSN valide est renseigné
    if (this.sentryDsn && this.sentryDsn.startsWith('http')) {
      this._sendToSentry(eventPayload).catch(() => {
        // En cas d'échec réseau hors-ligne, aucune exception n'est levée
      });
    }

    return eventPayload;
  }

  /**
   * Envoi asynchrone non-bloquant vers l'API Sentry Store
   * @private
   */
  async _sendToSentry(payload) {
    try {
      const urlObj = new URL(this.sentryDsn);
      const projectId = urlObj.pathname.replace(/^\//, '');
      const publicKey = urlObj.username;
      if (!projectId || !publicKey) return;

      const endpoint = `${urlObj.protocol}//${urlObj.host}/api/${projectId}/store/`;
      const authHeader = `Sentry sentry_version=7, sentry_key=${publicKey}, sentry_client=LanceDrop/${this.appVersion}`;

      const https = urlObj.protocol === 'https:' ? require('https') : require('http');
      const data = JSON.stringify(payload);

      await new Promise((resolve, reject) => {
        const req = https.request(endpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Sentry-Auth': authHeader,
            'Content-Length': Buffer.byteLength(data)
          },
          timeout: 3000
        }, (res) => {
          res.resume();
          resolve();
        });

        req.on('error', reject);
        req.on('timeout', () => {
          req.destroy();
          resolve();
        });
        req.write(data);
        req.end();
      });
    } catch (_) {
      // Résilience hors-ligne totale
    }
  }

  /**
   * Retourne la configuration Microsoft Clarity pour le frontend
   */
  getClarityConfig() {
    return {
      clarityId: this.clarityId,
      appName: this.appName,
      version: this.appVersion,
      maskSensitive: true
    };
  }

  /**
   * Récupère l'historique récent des breadcrumbs (pour diagnostic local)
   */
  getRecentBreadcrumbs() {
    return [...this.breadcrumbs];
  }

  /**
   * Vide l'historique des breadcrumbs
   */
  clearBreadcrumbs() {
    this.breadcrumbs = [];
  }
}

// Instance Singleton partagée
const telemetryInstance = new TelemetryManager();

module.exports = {
  TelemetryManager,
  telemetry: telemetryInstance
};
