import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { SharedFolderManager } from './shared_folder_manager.js';
import { SecuritySandbox, SecuritySandboxError } from './security_sandbox.js';
import { RangeStreamer, getMimeType } from './range_streamer.js';
import { ThumbnailService } from './thumbnail_service.js';

/**
 * HttpStreamingServer
 * Exposes REST API for shared folders, directory browsing, RFC 7233 byte-range streaming,
 * and preview thumbnails with session authentication.
 */
export class HttpStreamingServer {
  /**
   * @param {object} [options]
   * @param {number} [options.port=42421] - HTTP port to listen on.
   * @param {SharedFolderManager} [options.folderManager]
   * @param {ThumbnailService} [options.thumbnailService]
   * @param {string|Function} [options.sessionToken]
   * @param {string|Function} [options.sessionPin]
   * @param {Function} [options.getSessionToken]
   * @param {Function} [options.getSessionPin]
   * @param {object} [options.trustStore]
   * @param {Function} [options.authValidator]
   */
  constructor(options = {}) {
    this.port = options.port !== undefined ? options.port : 42421;
    this.folderManager = options.folderManager || new SharedFolderManager();
    this.thumbnailService = options.thumbnailService || new ThumbnailService();
    this.sessionToken = options.sessionToken || null;
    this.sessionPin = options.sessionPin || null;
    this.getSessionToken = options.getSessionToken || null;
    this.getSessionPin = options.getSessionPin || null;
    this.trustStore = options.trustStore || null;
    this.authValidator = options.authValidator || null;

    this.server = null;
    this.listening = false;
  }

  /**
   * Validates authentication credentials from headers or query parameters.
   * @param {import('node:http').IncomingMessage} req
   * @param {URL} parsedUrl
   * @returns {{ ok: boolean, status?: number, error?: string }}
   */
  validateAuth(req, parsedUrl) {
    let token = null;
    let pin = null;

    // 1. Authorization: Bearer <token>
    const authHeader = req.headers.authorization;
    if (authHeader) {
      const parts = authHeader.split(' ');
      if (parts.length === 2 && /^bearer$/i.test(parts[0])) {
        token = parts[1];
      }
    }

    // 2. Custom headers
    if (!token && req.headers['x-session-token']) {
      token = req.headers['x-session-token'];
    }
    if (req.headers['x-session-pin']) {
      pin = req.headers['x-session-pin'];
    }

    // 3. URL query parameters
    if (!token && parsedUrl.searchParams.has('token')) {
      token = parsedUrl.searchParams.get('token');
    }
    if (!pin && parsedUrl.searchParams.has('pin')) {
      pin = parsedUrl.searchParams.get('pin');
    }

    // If no credentials provided at all -> 401 Unauthorized
    if (!token && !pin) {
      return { ok: false, status: 401, error: 'Unauthorized: Missing authentication credentials' };
    }

    // Custom validator
    if (typeof this.authValidator === 'function') {
      if (this.authValidator(token, pin, req)) {
        return { ok: true };
      }
    }

    // Token check
    const activeToken =
      typeof this.getSessionToken === 'function' ? this.getSessionToken() : this.sessionToken;
    if (token && activeToken && token === activeToken) {
      return { ok: true };
    }

    // PIN check (normalized)
    const activePin =
      typeof this.getSessionPin === 'function' ? this.getSessionPin() : this.sessionPin;
    if (pin && activePin) {
      const normPin = String(pin).replace(/\s+/g, '');
      const normExpected = String(activePin).replace(/\s+/g, '');
      if (normPin === normExpected) {
        return { ok: true };
      }
    }

    // TrustStore check
    if (token && this.trustStore) {
      if (typeof this.trustStore.isDeviceTrusted === 'function' && this.trustStore.isDeviceTrusted(token)) {
        return { ok: true };
      }
      if (this.trustStore.devices && this.trustStore.devices.has(token)) {
        return { ok: true };
      }
    }

    // Invalid credentials provided -> 403 Forbidden
    return { ok: false, status: 403, error: 'Access denied: Invalid credentials' };
  }

  /**
   * Main HTTP request handler.
   * @param {import('node:http').IncomingMessage} req
   * @param {import('node:http').ServerResponse} res
   */
  async handleRequest(req, res) {
    // CORS headers for local web clients / dev previews
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, X-Session-Token, X-Session-PIN, Range, Content-Type');
    res.setHeader('Access-Control-Expose-Headers', 'Content-Range, Accept-Ranges, Content-Length');

    if (req.method === 'OPTIONS') {
      res.statusCode = 204;
      res.end();
      return;
    }

    const host = req.headers.host || `localhost:${this.port}`;
    const parsedUrl = new URL(req.url, `http://${host}`);
    const pathname = parsedUrl.pathname;

    // Route 1: Health check (unauthenticated)
    if (pathname === '/api/health') {
      res.statusCode = 200;
      res.setHeader('Content-Type', 'application/json');
      res.end(
        JSON.stringify({
          status: 'ok',
          version: '1.0.0',
          name: 'LanceDrop Streaming Server'
        })
      );
      return;
    }

    // Authenticate all other endpoints
    const authResult = this.validateAuth(req, parsedUrl);
    if (!authResult.ok) {
      res.statusCode = authResult.status || 401;
      res.setHeader('Content-Type', 'application/json');
      res.end(
        JSON.stringify({
          error: authResult.status === 401 ? 'Unauthorized' : 'Access denied',
          message: authResult.error
        })
      );
      return;
    }

    try {
      // Route 2: List shared folders
      if (pathname === '/api/shares') {
        const shares = await this.folderManager.listFolders(true);
        res.statusCode = 200;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify(shares));
        return;
      }

      // Parse shareId from path: /api/shares/:shareId/(browse|stream|thumbnail)
      const sharesMatch = pathname.match(/^\/api\/shares\/([^/]+)\/(browse|list|stream|thumbnail)$/);
      let routeType = null;
      let shareId = null;

      if (sharesMatch) {
        shareId = decodeURIComponent(sharesMatch[1]);
        routeType = sharesMatch[2];
        if (routeType === 'list') routeType = 'browse';
      } else if (pathname === '/api/remote-explorer/list') {
        routeType = 'browse';
        shareId = parsedUrl.searchParams.get('shareId');
      } else if (pathname === '/api/remote-explorer/stream') {
        routeType = 'stream';
        shareId = parsedUrl.searchParams.get('shareId');
      } else if (pathname === '/api/remote-explorer/thumbnail') {
        routeType = 'thumbnail';
        shareId = parsedUrl.searchParams.get('shareId');
      }

      if (!routeType || !shareId) {
        res.statusCode = 404;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Endpoint not found' }));
        return;
      }

      // Validate shareId
      const share = this.folderManager.getFolder(shareId);
      if (!share) {
        // If share not found, verify if this looks like a security traversal test on shareId
        if (shareId.includes('..') || shareId.includes('/') || shareId.includes('\\')) {
          res.statusCode = 403;
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({ error: 'Access denied' }));
          return;
        }
        res.statusCode = 404;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Share or directory not found' }));
        return;
      }

      const rawSubpath =
        parsedUrl.searchParams.get('subpath') ?? parsedUrl.searchParams.get('path') ?? '';

      // Route: Browse Directory
      if (routeType === 'browse') {
        await this.handleBrowse(share, rawSubpath, parsedUrl, req, res);
        return;
      }

      // Route: Stream File
      if (routeType === 'stream') {
        await this.handleStream(share, rawSubpath, req, res);
        return;
      }

      // Route: Thumbnail
      if (routeType === 'thumbnail') {
        await this.handleThumbnail(share, rawSubpath, parsedUrl, req, res);
        return;
      }
    } catch (err) {
      if (err instanceof SecuritySandboxError || err.isSecurityViolation) {
        res.statusCode = 403;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Access denied', message: err.message }));
        return;
      }

      res.statusCode = 500;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Internal server error', message: err.message }));
    }
  }

  /**
   * Handles directory browsing and pagination.
   */
  async handleBrowse(share, rawSubpath, parsedUrl, req, res) {
    const safeDirPath = await SecuritySandbox.resolveSafePath(share.path, rawSubpath);

    let stat;
    try {
      stat = await fs.stat(safeDirPath);
    } catch (err) {
      res.statusCode = 404;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Share or directory not found' }));
      return;
    }

    if (!stat.isDirectory()) {
      res.statusCode = 400;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Target path is not a directory' }));
      return;
    }

    const cleanSubpath = SecuritySandbox.validateRelativeSubpath(rawSubpath);

    // Build breadcrumbs
    const breadcrumbs = [{ name: 'Root', subpath: '' }];
    if (cleanSubpath) {
      const segments = cleanSubpath.split('/');
      let currentAcc = '';
      for (const seg of segments) {
        currentAcc = currentAcc ? `${currentAcc}/${seg}` : seg;
        breadcrumbs.push({ name: seg, subpath: currentAcc });
      }
    }

    // Read entries
    const dirents = await fs.readdir(safeDirPath, { withFileTypes: true });
    const items = [];

    for (const entry of dirents) {
      const entryName = entry.name;
      const fullEntryPath = path.join(safeDirPath, entryName);
      let entryStat = null;
      try {
        entryStat = await fs.stat(fullEntryPath);
      } catch {
        continue;
      }

      const isDir = entry.isDirectory();
      const entrySubpath = cleanSubpath ? `${cleanSubpath}/${entryName}` : entryName;
      const mimeType = isDir ? 'inode/directory' : getMimeType(entryName);
      const isMedia =
        !isDir &&
        (mimeType.startsWith('image/') ||
          mimeType.startsWith('video/') ||
          mimeType.startsWith('audio/'));
      const hasThumbnail =
        !isDir && (mimeType.startsWith('image/') || mimeType.startsWith('video/'));

      items.push({
        name: entryName,
        path: entrySubpath,
        subpath: entrySubpath,
        isDirectory: isDir,
        size: isDir ? 0 : entryStat.size,
        mimeType: mimeType,
        mtime: Math.floor(entryStat.mtimeMs),
        modifiedTime: Math.floor(entryStat.mtimeMs),
        hasThumbnail,
        isStreamable: isMedia
      });
    }

    // Sorting: directories first, then requested sort attribute
    const sortField = parsedUrl.searchParams.get('sort') || 'name';
    items.sort((a, b) => {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;

      if (sortField === 'size') {
        return a.size - b.size;
      }
      if (sortField === 'mtime' || sortField === 'date') {
        return a.mtime - b.mtime;
      }
      return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' });
    });

    // Pagination
    const page = Math.max(1, parseInt(parsedUrl.searchParams.get('page'), 10) || 1);
    const limit = Math.min(
      100,
      Math.max(
        1,
        parseInt(
          parsedUrl.searchParams.get('limit') || parsedUrl.searchParams.get('pageSize'),
          10
        ) || 50
      )
    );

    const total = items.length;
    const totalPages = Math.max(1, Math.ceil(total / limit));
    const startIndex = (page - 1) * limit;
    const pagedItems = startIndex < total ? items.slice(startIndex, startIndex + limit) : [];

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/json');
    res.end(
      JSON.stringify({
        shareId: share.id,
        shareName: share.name,
        currentPath: cleanSubpath,
        breadcrumbs,
        items: pagedItems,
        totalItems: total,
        pagination: {
          page,
          limit,
          pageSize: limit,
          total,
          totalItems: total,
          totalPages
        }
      })
    );
  }

  /**
   * Handles HTTP Range streaming for media files.
   */
  async handleStream(share, rawSubpath, req, res) {
    if (!rawSubpath) {
      res.statusCode = 400;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Missing file path' }));
      return;
    }

    const safeFilePath = await SecuritySandbox.resolveSafePath(share.path, rawSubpath);
    await RangeStreamer.streamFile(safeFilePath, req, res);
  }

  /**
   * Handles thumbnail requests.
   */
  async handleThumbnail(share, rawSubpath, parsedUrl, req, res) {
    if (!rawSubpath) {
      res.statusCode = 400;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'Missing file path' }));
      return;
    }

    const size = parseInt(parsedUrl.searchParams.get('size'), 10) || 256;
    const safeFilePath = await SecuritySandbox.resolveSafePath(share.path, rawSubpath);
    await this.thumbnailService.serveThumbnail(safeFilePath, req, res, { size });
  }

  /**
   * Starts HTTP streaming server.
   * @returns {Promise<HttpStreamingServer>}
   */
  async start() {
    if (this.listening) return this;

    await this.folderManager.load();

    this.server = http.createServer((req, res) => {
      this.handleRequest(req, res).catch((err) => {
        if (!res.headersSent) {
          res.statusCode = 500;
          res.setHeader('Content-Type', 'application/json');
          res.end(JSON.stringify({ error: 'Internal server error', message: err.message }));
        }
      });
    });

    await new Promise((resolve, reject) => {
      this.server.once('error', reject);
      this.server.listen(this.port, '0.0.0.0', () => {
        const address = this.server.address();
        if (address && typeof address === 'object') {
          this.port = address.port;
        }
        this.listening = true;
        resolve();
      });
    });

    return this;
  }

  /**
   * Stops HTTP streaming server.
   * @returns {Promise<void>}
   */
  async stop() {
    if (!this.listening || !this.server) return;

    await new Promise((resolve) => {
      this.server.close(() => {
        this.listening = false;
        resolve();
      });
    });
  }
}
