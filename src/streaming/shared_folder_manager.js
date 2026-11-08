import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

/**
 * SharedFolderManager
 * Manages authorized shared folders with persistence in .lancedrop_shares.json.
 */
export class SharedFolderManager {
  /**
   * @param {string} [storageDirectory] - Directory to persist shared folders config.
   * @param {string} [fileName] - File name (defaults to .lancedrop_shares.json).
   */
  constructor(storageDirectory = process.cwd(), fileName = '.lancedrop_shares.json') {
    if (typeof storageDirectory === 'string' && storageDirectory.endsWith('.json')) {
      this.storagePath = path.resolve(storageDirectory);
      this.storageDirectory = path.dirname(this.storagePath);
    } else {
      this.storageDirectory = path.resolve(storageDirectory);
      this.storagePath = path.join(this.storageDirectory, fileName);
    }
    this.shares = new Map(); // id -> Share object
    this.loaded = false;
  }

  /**
   * Loads shares from disk.
   */
  async load() {
    try {
      const data = await fs.readFile(this.storagePath, 'utf8');
      const list = JSON.parse(data);
      this.shares.clear();
      if (Array.isArray(list)) {
        for (const item of list) {
          if (item && item.id && item.path) {
            this.shares.set(item.id, {
              id: item.id,
              name: item.name || path.basename(item.path) || 'SharedFolder',
              path: path.resolve(item.path),
              createdAt: item.createdAt || Date.now()
            });
          }
        }
      }
    } catch {
      // Missing or corrupt file -> initialize empty map
      this.shares.clear();
    }
    this.loaded = true;
    return this.listFolders();
  }

  /**
   * Saves shares atomically to disk.
   */
  async save() {
    const list = Array.from(this.shares.values()).map(s => ({
      id: s.id,
      name: s.name,
      path: s.path,
      createdAt: s.createdAt
    }));
    await fs.mkdir(this.storageDirectory, { recursive: true });
    const tempFile = `${this.storagePath}.${crypto.randomBytes(4).toString('hex')}.tmp`;
    await fs.writeFile(tempFile, JSON.stringify(list, null, 2), 'utf8');
    await fs.rename(tempFile, this.storagePath);
  }

  /**
   * Validates that a directory exists and is accessible.
   * @param {string} folderPath
   * @returns {Promise<string>} Canonical resolved path.
   */
  async validateFolder(folderPath) {
    if (!folderPath || typeof folderPath !== 'string') {
      throw new Error('Folder path must be a non-empty string');
    }
    const resolved = path.resolve(folderPath);
    try {
      const stat = await fs.stat(resolved);
      if (!stat.isDirectory()) {
        throw new Error(`Path is not a directory: ${resolved}`);
      }
      await fs.access(resolved, fs.constants.R_OK);
      return resolved;
    } catch (err) {
      throw new Error(`Directory validation failed for ${folderPath}: ${err.message}`);
    }
  }

  /**
   * Adds an authorized shared folder.
   * @param {string} folderPath
   * @param {string} [customName]
   * @returns {Promise<object>} The added or existing share.
   */
  async addFolder(folderPath, customName = null) {
    const resolved = await this.validateFolder(folderPath);

    // Check if share already exists for this path
    for (const existing of this.shares.values()) {
      if (path.resolve(existing.path) === resolved) {
        if (customName && existing.name !== customName) {
          existing.name = customName;
          await this.save();
        }
        return existing;
      }
    }

    const shareId = `share-${crypto.randomBytes(6).toString('hex')}`;
    const shareName = customName || path.basename(resolved) || 'SharedFolder';
    const share = {
      id: shareId,
      name: shareName,
      path: resolved,
      createdAt: Date.now()
    };

    this.shares.set(shareId, share);
    await this.save();
    return share;
  }

  /**
   * Removes a shared folder by ID or path.
   * @param {string} idOrPath
   * @returns {Promise<boolean>} True if removed, false otherwise.
   */
  async removeFolder(idOrPath) {
    if (!idOrPath) return false;

    let targetId = null;
    if (this.shares.has(idOrPath)) {
      targetId = idOrPath;
    } else {
      const resolved = path.resolve(idOrPath);
      for (const [id, share] of this.shares.entries()) {
        if (share.id === idOrPath || path.resolve(share.path) === resolved) {
          targetId = id;
          break;
        }
      }
    }

    if (targetId) {
      this.shares.delete(targetId);
      await this.save();
      return true;
    }
    return false;
  }

  /**
   * Retrieves a share by ID.
   * @param {string} idOrPath
   * @returns {object|null}
   */
  getFolder(idOrPath) {
    if (!idOrPath) return null;
    if (this.shares.has(idOrPath)) {
      return this.shares.get(idOrPath);
    }
    for (const share of this.shares.values()) {
      if (share.id === idOrPath || path.resolve(share.path) === path.resolve(idOrPath)) {
        return share;
      }
    }
    return null;
  }

  getShare(idOrPath) {
    return this.getFolder(idOrPath);
  }

  /**
   * Lists all authorized shared folders.
   * @param {boolean} [withItemCount=false] - Whether to calculate item counts for each share root.
   * @returns {Promise<Array<object>>|Array<object>}
   */
  async listFolders(withItemCount = false) {
    const list = Array.from(this.shares.values()).map(s => ({ ...s }));
    if (!withItemCount) {
      return list;
    }

    return Promise.all(
      list.map(async (share) => {
        let itemCount = 0;
        try {
          const entries = await fs.readdir(share.path);
          itemCount = entries.length;
        } catch {
          itemCount = 0;
        }
        return {
          ...share,
          itemCount
        };
      })
    );
  }

  async getShares(withItemCount = false) {
    return this.listFolders(withItemCount);
  }

  listShares() {
    return Array.from(this.shares.values()).map(s => ({ ...s }));
  }
}
