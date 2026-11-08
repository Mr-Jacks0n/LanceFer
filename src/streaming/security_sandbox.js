import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Custom Error for Security Sandbox violations.
 * Contains HTTP status 403 and error message "Access denied".
 */
export class SecuritySandboxError extends Error {
  /**
   * @param {string} [message]
   * @param {number} [statusCode]
   */
  constructor(message = 'Access denied', statusCode = 403) {
    super(message);
    this.name = 'SecuritySandboxError';
    this.statusCode = statusCode;
    this.isSecurityViolation = true;
  }
}

/**
 * SecuritySandbox
 * Enforces a strict 4-tier security boundary around shared directories:
 * Tier 1: Input decoding and traversal token detection (.., %2e%2e, null bytes, device names).
 * Tier 2: UNC, drive letter, and absolute path rejection.
 * Tier 3: Canonical path resolution and relative boundary verification (path.relative).
 * Tier 4: Physical realpath and symlink/junction containment verification (fs.realpath).
 */
export class SecuritySandbox {
  /**
   * Iteratively decodes percent-encoded strings to catch single and double encoding attacks.
   * @param {string} input
   * @returns {string}
   */
  static decodeIterative(input) {
    if (typeof input !== 'string') {
      throw new SecuritySandboxError('Access denied: Invalid path format');
    }

    let current = input;
    for (let depth = 0; depth < 3; depth++) {
      try {
        const decoded = decodeURIComponent(current);
        if (decoded === current) break;
        current = decoded;
      } catch {
        // If URI malformed, reject immediately
        throw new SecuritySandboxError('Access denied: Malformed URL encoding');
      }
    }
    return current;
  }

  /**
   * Tier 1 & Tier 2: Validates raw subpath input against traversal, UNC, absolute paths,
   * null bytes, and reserved device names.
   * @param {string} rawSubpath
   * @returns {string} Normalized relative subpath.
   */
  static validateRelativeSubpath(rawSubpath) {
    if (rawSubpath === undefined || rawSubpath === null) {
      return '';
    }

    if (typeof rawSubpath !== 'string') {
      throw new SecuritySandboxError('Access denied: Invalid subpath format');
    }

    // Check for null bytes (raw or encoded)
    if (rawSubpath.includes('\0') || rawSubpath.includes('%00')) {
      throw new SecuritySandboxError('Access denied: Null byte injection detected');
    }

    // Decode iterative
    const decoded = this.decodeIterative(rawSubpath);

    if (decoded.includes('\0')) {
      throw new SecuritySandboxError('Access denied: Null byte injection detected');
    }

    const trimmed = decoded.trim();
    if (trimmed === '' || trimmed === '.' || trimmed === '/' || trimmed === '\\') {
      return '';
    }

    // Check for UNC paths (e.g. \\server\share, //server/share, \\?\C:\, \\.\COM1)
    if (/^[\\/]{2}/.test(trimmed) || /^[\\/]\?[\\/]/.test(trimmed)) {
      throw new SecuritySandboxError('Access denied: UNC path rejected');
    }

    // Check for absolute paths starting with / or \
    if (trimmed.startsWith('/') || trimmed.startsWith('\\')) {
      throw new SecuritySandboxError('Access denied: Absolute path rejected');
    }

    // Check for Windows drive letters (e.g. C:, C:\, D:/)
    if (/^[a-zA-Z]:/.test(trimmed)) {
      throw new SecuritySandboxError('Access denied: Drive letter rejected');
    }

    // Split into normalized segments using both / and \
    const segments = trimmed.split(/[/\\]+/);
    const reservedDeviceNames = /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?$/i;

    for (const segment of segments) {
      if (segment === '..') {
        throw new SecuritySandboxError('Access denied: Directory traversal detected');
      }
      if (reservedDeviceNames.test(segment)) {
        throw new SecuritySandboxError('Access denied: Reserved system device name rejected');
      }
    }

    return segments.join('/');
  }

  /**
   * Tier 3 & Tier 4: Resolves safe physical path inside authorized root.
   * Throws SecuritySandboxError (403) on any boundary violation or escaping symlink.
   * @param {string} authorizedRoot
   * @param {string} rawSubpath
   * @returns {Promise<string>} Canonical absolute path verified inside authorizedRoot.
   */
  static async resolveSafePath(authorizedRoot, rawSubpath) {
    if (!authorizedRoot || typeof authorizedRoot !== 'string') {
      throw new SecuritySandboxError('Access denied: Authorized root must be configured');
    }

    // Tier 1 & 2: Parse and validate relative subpath
    const cleanSubpath = this.validateRelativeSubpath(rawSubpath);

    // Tier 3: Canonical resolution against authorizedRoot
    const canonicalRoot = path.resolve(authorizedRoot);
    const candidatePath = path.resolve(canonicalRoot, cleanSubpath);

    const relativeCheck = path.relative(canonicalRoot, candidatePath);
    if (relativeCheck.startsWith('..') || path.isAbsolute(relativeCheck)) {
      throw new SecuritySandboxError('Access denied: Path escapes authorized root boundary');
    }

    // Tier 4: Physical realpath check for symlinks and NTFS junctions
    let realRoot;
    try {
      realRoot = await fs.realpath(canonicalRoot);
    } catch (err) {
      throw new SecuritySandboxError(`Access denied: Authorized root inaccessible (${err.message})`);
    }

    // Check candidate realpath if it exists
    try {
      const realCandidate = await fs.realpath(candidatePath);
      const realRelative = path.relative(realRoot, realCandidate);
      if (realRelative.startsWith('..') || path.isAbsolute(realRelative)) {
        throw new SecuritySandboxError('Access denied: Symlink escapes authorized root boundary');
      }
      return realCandidate;
    } catch (err) {
      if (err.code === 'ENOENT') {
        // Path does not exist yet. Check all existing parent components to ensure no symlink escaped.
        let currentParent = path.dirname(candidatePath);
        while (currentParent.length >= canonicalRoot.length) {
          try {
            const realParent = await fs.realpath(currentParent);
            const parentRelative = path.relative(realRoot, realParent);
            if (parentRelative.startsWith('..') || path.isAbsolute(parentRelative)) {
              throw new SecuritySandboxError('Access denied: Ancestor symlink escapes authorized root boundary');
            }
            break; // Found first existing ancestor and it is safe
          } catch (pErr) {
            if (pErr.code === 'ENOENT') {
              const nextParent = path.dirname(currentParent);
              if (nextParent === currentParent) break;
              currentParent = nextParent;
            } else {
              throw new SecuritySandboxError(`Access denied: Security inspection failed (${pErr.message})`);
            }
          }
        }
        return candidatePath;
      }
      throw new SecuritySandboxError(`Access denied: Security verification error (${err.message})`);
    }
  }

  /**
   * Helper returning boolean whether path is safely confined.
   * @param {string} authorizedRoot
   * @param {string} rawSubpath
   * @returns {Promise<boolean>}
   */
  static async isSafeSubpath(authorizedRoot, rawSubpath) {
    try {
      await this.resolveSafePath(authorizedRoot, rawSubpath);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Instance method forwarding to static resolveSafePath.
   */
  async resolveSafePath(authorizedRoot, rawSubpath) {
    return SecuritySandbox.resolveSafePath(authorizedRoot, rawSubpath);
  }
}
