import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import mime from 'mime-types';

/**
 * Fallback MIME type mapping for common multimedia extensions.
 */
const MEDIA_MIME_FALLBACKS = {
  '.mp4': 'video/mp4',
  '.m4v': 'video/mp4',
  '.mkv': 'video/x-matroska',
  '.webm': 'video/webm',
  '.avi': 'video/x-msvideo',
  '.mov': 'video/quicktime',
  '.wmv': 'video/x-ms-wmv',
  '.flv': 'video/x-flv',
  '.ts': 'video/mp2t',
  '.mp3': 'audio/mpeg',
  '.flac': 'audio/flac',
  '.wav': 'audio/wav',
  '.aac': 'audio/aac',
  '.ogg': 'audio/ogg',
  '.m4a': 'audio/mp4',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.bmp': 'image/bmp',
  '.svg': 'image/svg+xml'
};

/**
 * Resolves content type for a given file path.
 * @param {string} filePath
 * @returns {string}
 */
export function getMimeType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (MEDIA_MIME_FALLBACKS[ext]) {
    return MEDIA_MIME_FALLBACKS[ext];
  }
  const detected = mime.lookup(filePath);
  return detected || 'application/octet-stream';
}

/**
 * Parses RFC 7233 byte Range header.
 *
 * @param {string|null|undefined} rangeHeader - e.g. "bytes=0-1048575", "bytes=500-", "bytes=-1024"
 * @param {number} totalSize - Total file size in bytes.
 * @returns {{ start: number, end: number, total: number } | { unsatisfiable: true, total: number } | null}
 */
export function parseRange(rangeHeader, totalSize) {
  if (!rangeHeader || typeof rangeHeader !== 'string') {
    return null;
  }

  const matches = rangeHeader.trim().match(/^bytes=(\d*)-(\d*)$/);
  if (!matches) {
    return null; // Invalid syntax or multi-range not supported -> fallback to full content
  }

  const rawStart = matches[1];
  const rawEnd = matches[2];

  if (rawStart === '' && rawEnd === '') {
    return null;
  }

  let start = 0;
  let end = totalSize - 1;

  if (totalSize === 0) {
    return { unsatisfiable: true, total: 0 };
  }

  if (rawStart === '') {
    // Suffix range: bytes=-suffixLength (last N bytes)
    const suffixLength = parseInt(rawEnd, 10);
    if (isNaN(suffixLength) || suffixLength <= 0) {
      return { unsatisfiable: true, total: totalSize };
    }
    start = Math.max(0, totalSize - suffixLength);
    end = totalSize - 1;
  } else if (rawEnd === '') {
    // Open-ended range: bytes=start-
    start = parseInt(rawStart, 10);
    if (isNaN(start) || start < 0 || start >= totalSize) {
      return { unsatisfiable: true, total: totalSize };
    }
    end = totalSize - 1;
  } else {
    // Explicit range: bytes=start-end
    start = parseInt(rawStart, 10);
    end = parseInt(rawEnd, 10);

    if (isNaN(start) || isNaN(end) || start < 0 || start > end || start >= totalSize) {
      return { unsatisfiable: true, total: totalSize };
    }

    // Clamp end to totalSize - 1 per RFC 7233
    if (end >= totalSize) {
      end = totalSize - 1;
    }
  }

  return { start, end, total: totalSize };
}

/**
 * RangeStreamer
 * Implements RFC 7233 HTTP Byte-Range streaming using O(1) constant-memory streams.
 */
export class RangeStreamer {
  /**
   * Streams a file to an HTTP response supporting RFC 7233 byte ranges.
   * @param {string} filePath - Absolute path to the file.
   * @param {import('node:http').IncomingMessage} req
   * @param {import('node:http').ServerResponse} res
   * @param {object} [options]
   * @returns {Promise<void>}
   */
  static async streamFile(filePath, req, res, options = {}) {
    let stat;
    try {
      stat = await fsp.stat(filePath);
      if (stat.isDirectory()) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Cannot stream a directory' }));
        return;
      }
    } catch (err) {
      res.statusCode = err.code === 'ENOENT' ? 404 : 500;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: err.code === 'ENOENT' ? 'File not found' : err.message }));
      return;
    }

    const totalSize = stat.size;
    const contentType = options.contentType || getMimeType(filePath);
    const rangeHeader = req.headers.range;

    // Standard headers for all range responses
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Content-Type', contentType);
    res.setHeader('Last-Modified', stat.mtime.toUTCString());
    res.setHeader('ETag', `"${totalSize.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`);

    // Handle range request
    if (rangeHeader) {
      const parsed = parseRange(rangeHeader, totalSize);

      if (parsed && parsed.unsatisfiable) {
        res.statusCode = 416; // Range Not Satisfiable
        res.setHeader('Content-Range', `bytes */${totalSize}`);
        res.setHeader('Content-Length', '0');
        res.end();
        return;
      }

      if (parsed) {
        const { start, end } = parsed;
        const chunkSize = end - start + 1;

        res.statusCode = 206; // Partial Content
        res.setHeader('Content-Range', `bytes ${start}-${end}/${totalSize}`);
        res.setHeader('Content-Length', String(chunkSize));
        res.setHeader('Cache-Control', 'no-cache');

        if (req.method === 'HEAD') {
          res.end();
          return;
        }

        // Stream range using fs.createReadStream with exact start/end bounds (O(1) memory)
        const stream = fs.createReadStream(filePath, { start, end });
        req.on('close', () => {
          stream.destroy();
        });
        stream.on('error', (err) => {
          if (!res.headersSent) {
            res.statusCode = 500;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Stream error', message: err.message }));
          }
        });
        stream.pipe(res);
        return;
      }
    }

    // Full content response (200 OK)
    res.statusCode = 200;
    res.setHeader('Content-Length', String(totalSize));

    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const stream = fs.createReadStream(filePath);
    req.on('close', () => {
      stream.destroy();
    });
    stream.on('error', (err) => {
      if (!res.headersSent) {
        res.statusCode = 500;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Stream error', message: err.message }));
      }
    });
    stream.pipe(res);
  }
}
