import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import http from 'node:http';
import { SharedFolderManager } from '../src/streaming/shared_folder_manager.js';
import { HttpStreamingServer } from '../src/streaming/http_streaming_server.js';
import { RangeStreamer, parseRange } from '../src/streaming/range_streamer.js';

const TMP_DIR = path.resolve('tmp/challenger_streaming_' + Date.now());
const SESSION_TOKEN = 'challenger-token-range-stress-999';
const SPARSE_512MB = 512 * 1024 * 1024; // 536,870,912 bytes

/**
 * Helper to perform HTTP requests and collect status, headers, and body.
 */
function requestHttp(url, options = {}) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const reqOptions = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port,
      path: parsedUrl.pathname + parsedUrl.search,
      method: options.method || 'GET',
      headers: options.headers || {}
    };

    const req = http.request(reqOptions, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => {
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: Buffer.concat(chunks)
        });
      });
    });

    req.on('error', reject);
    req.end();
  });
}

test('Challenger Adversarial HTTP Range & Seeking Stress Harness', async (t) => {
  let server = null;
  let folderManager = null;
  let baseUrl = '';
  let share = null;
  let sparseFilePath = '';
  let zeroByteFilePath = '';
  let textFilePath = '';
  const textContent = 'Challenger Empirical Verification Sample Content for RFC 7233 200 OK test.';

  await t.test('Setup: Create 512MB sparse file with multi-offset sentinels, 0-byte file, text file and HTTP server', async () => {
    await fs.mkdir(TMP_DIR, { recursive: true });
    const sharedDir = path.join(TMP_DIR, 'shared');
    await fs.mkdir(sharedDir, { recursive: true });

    // 1. Create 512MB sparse file with sentinel markers at strategic offsets:
    // Offset 0 (0MB): "HEAD_START_BYTE" (15 bytes)
    // Offset 134,217,728 (128MB): "QUARTER_128M_MK" (15 bytes)
    // Offset 268,435,456 (256MB): "MIDPOINT_256M_M" (15 bytes)
    // Offset 402,653,184 (384MB): "THREEQTR_384M_M" (15 bytes)
    // Offset 536,870,900 (EOF - 12): "TAIL_EOF_MK!" (12 bytes)
    sparseFilePath = path.join(sharedDir, 'massive_video.mp4');
    const handle = await fs.open(sparseFilePath, 'w+');
    await handle.truncate(SPARSE_512MB);

    await handle.write(Buffer.from('HEAD_START_BYTE'), 0, 15, 0);
    await handle.write(Buffer.from('QUARTER_128M_MK'), 0, 15, 128 * 1024 * 1024);
    await handle.write(Buffer.from('MIDPOINT_256M_M'), 0, 15, 256 * 1024 * 1024);
    await handle.write(Buffer.from('THREEQTR_384M_M'), 0, 15, 384 * 1024 * 1024);
    await handle.write(Buffer.from('TAIL_EOF_MK!'), 0, 12, SPARSE_512MB - 12);
    await handle.close();

    const statSparse = await fs.stat(sparseFilePath);
    assert.equal(statSparse.size, SPARSE_512MB);

    // 2. Create 0-byte empty file for edge case testing
    zeroByteFilePath = path.join(sharedDir, 'empty.mp4');
    await fs.writeFile(zeroByteFilePath, Buffer.alloc(0));

    // 3. Create regular small text file
    textFilePath = path.join(sharedDir, 'document.txt');
    await fs.writeFile(textFilePath, textContent, 'utf8');

    // 4. Start HTTP Streaming Server
    folderManager = new SharedFolderManager(TMP_DIR);
    share = await folderManager.addFolder(sharedDir, 'AdversarialTestShare');

    server = new HttpStreamingServer({
      port: 0,
      folderManager,
      sessionToken: SESSION_TOKEN
    });
    await server.start();
    baseUrl = `http://127.0.0.1:${server.port}`;
  });

  // TEST 1: Exact 1MB chunk on bytes=0-1048575
  await t.test('Empirical: Exact 1MB chunk bytes=0-1048575 returns 206 Partial Content with Content-Range bytes 0-1048575/536870912', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-1048575'
      }
    });

    assert.equal(res.statusCode, 206, 'Status must be 206');
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-range'], `bytes 0-1048575/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1048576');
    assert.equal(res.body.length, 1048576, 'Response body must be exactly 1,048,576 bytes');
    assert.equal(res.body.subarray(0, 15).toString('utf8'), 'HEAD_START_BYTE');
  });

  // TEST 2: Single-byte boundary bytes=0-0
  await t.test('Empirical: Single-byte boundary bytes=0-0 returns 206 with 1 byte (first byte)', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-0'
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes 0-0/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1');
    assert.equal(res.body.length, 1);
    assert.equal(res.body[0], Buffer.from('H')[0], 'First byte must be "H" of HEAD_START_BYTE');
  });

  // TEST 3: Single-byte boundary at end of file via open-ended bytes=<total-1>-
  await t.test('Empirical: Single-byte boundary bytes=(total-1)- returns 206 with last byte', async () => {
    const lastByteIndex = SPARSE_512MB - 1;
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${lastByteIndex}-`
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes ${lastByteIndex}-${lastByteIndex}/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1');
    assert.equal(res.body.length, 1);
    assert.equal(res.body[0], Buffer.from('!')[0], 'Last byte must be "!" from TAIL_EOF_MK!');
  });

  // TEST 4: Single-byte boundary at end of file via explicit bytes=<total-1>-<total-1>
  await t.test('Empirical: Single-byte boundary bytes=(total-1)-(total-1) returns 206 with 1 byte', async () => {
    const lastByteIndex = SPARSE_512MB - 1;
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${lastByteIndex}-${lastByteIndex}`
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes ${lastByteIndex}-${lastByteIndex}/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1');
    assert.equal(res.body.length, 1);
    assert.equal(res.body[0], Buffer.from('!')[0]);
  });

  // TEST 5: Suffix range bytes=-500
  await t.test('Empirical: Suffix range bytes=-500 returns last 500 bytes and Content-Range', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=-500'
      }
    });

    assert.equal(res.statusCode, 206);
    const expectedStart = SPARSE_512MB - 500;
    const expectedEnd = SPARSE_512MB - 1;
    assert.equal(res.headers['content-range'], `bytes ${expectedStart}-${expectedEnd}/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '500');
    assert.equal(res.body.length, 500);

    const tailEnd = res.body.subarray(500 - 12).toString('utf8');
    assert.equal(tailEnd, 'TAIL_EOF_MK!');
  });

  // TEST 6: Suffix range bytes=-1 (single last byte)
  await t.test('Empirical: Suffix range bytes=-1 returns last 1 byte', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=-1'
      }
    });

    assert.equal(res.statusCode, 206);
    const lastByteIndex = SPARSE_512MB - 1;
    assert.equal(res.headers['content-range'], `bytes ${lastByteIndex}-${lastByteIndex}/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1');
    assert.equal(res.body.length, 1);
    assert.equal(res.body[0], Buffer.from('!')[0]);
  });

  // TEST 7: Suffix range larger than file size (RFC 7233: clamps to whole file)
  await t.test('Empirical: Suffix range larger than total file size clamps to whole representation', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=-999999'
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes 0-${total - 1}/${total}`);
    assert.equal(res.headers['content-length'], String(total));
    assert.equal(res.body.toString('utf8'), textContent);
  });

  // TEST 8: Open-ended range bytes=1000-
  await t.test('Empirical: Open-ended range bytes=1000- returns bytes from 1000 to EOF', async () => {
    const total = Buffer.byteLength(textContent); // ~73 bytes
    // Test on 512MB file: bytes=536870000-
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const offset = 536870000;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${offset}-`
      }
    });

    const expectedLength = SPARSE_512MB - offset;
    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes ${offset}-${SPARSE_512MB - 1}/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], String(expectedLength));
    assert.equal(res.body.length, expectedLength);

    // Also test open-ended on text file from 10-
    const urlText = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const resText = await requestHttp(urlText, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=10-'
      }
    });
    assert.equal(resText.statusCode, 206);
    assert.equal(resText.headers['content-range'], `bytes 10-${total - 1}/${total}`);
    assert.equal(resText.headers['content-length'], String(total - 10));
    assert.equal(resText.body.toString('utf8'), textContent.slice(10));
  });

  // TEST 9: Entire file via open-ended range bytes=0-
  await t.test('Empirical: bytes=0- returns 206 with full range 0-(total-1)/total', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-'
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes 0-${total - 1}/${total}`);
    assert.equal(res.headers['content-length'], String(total));
    assert.equal(res.body.toString('utf8'), textContent);
  });

  // TEST 10: Clamping end offset when end >= total (RFC 7233 Section 2.1)
  await t.test('Empirical: Explicit range with end >= total clamps to total-1', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-9999999999'
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['content-range'], `bytes 0-${total - 1}/${total}`);
    assert.equal(res.headers['content-length'], String(total));
    assert.equal(res.body.toString('utf8'), textContent);
  });

  // TEST 11: Reversed / invalid ranges return 416 Range Not Satisfiable
  await t.test('Empirical: Reversed range bytes=500-100 returns 416 Range Not Satisfiable', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=500-100'
      }
    });

    assert.equal(res.statusCode, 416);
    assert.equal(res.headers['content-range'], `bytes */${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '0');
    assert.equal(res.body.length, 0);
  });

  // TEST 12: Range start >= total returns 416
  await t.test('Empirical: Range start >= total returns 416 Range Not Satisfiable', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;

    // start == total
    const res1 = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${SPARSE_512MB}-`
      }
    });
    assert.equal(res1.statusCode, 416);
    assert.equal(res1.headers['content-range'], `bytes */${SPARSE_512MB}`);

    // start > total
    const res2 = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${SPARSE_512MB + 5000}-${SPARSE_512MB + 6000}`
      }
    });
    assert.equal(res2.statusCode, 416);
    assert.equal(res2.headers['content-range'], `bytes */${SPARSE_512MB}`);
  });

  // TEST 13: Invalid suffix range bytes=-0 returns 416
  await t.test('Empirical: Suffix range bytes=-0 returns 416 Range Not Satisfiable', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=-0'
      }
    });

    assert.equal(res.statusCode, 416);
    assert.equal(res.headers['content-range'], `bytes */${SPARSE_512MB}`);
  });

  // TEST 14: Empty file (0 bytes) with range returns 416 bytes */0
  await t.test('Empirical: Range request on 0-byte file returns 416 with Content-Range bytes */0', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=empty.mp4`;
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-0'
      }
    });

    assert.equal(res.statusCode, 416);
    assert.equal(res.headers['content-range'], 'bytes */0');
    assert.equal(res.headers['content-length'], '0');
  });

  // TEST 15: Requests without Range header return 200 OK with full Content-Length
  await t.test('Empirical: Request without Range returns 200 OK with full Content-Length and Accept-Ranges: bytes', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);
    const res = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`
      }
    });

    assert.equal(res.statusCode, 200);
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-length'], String(total));
    assert.equal(res.headers['content-range'], undefined);
    assert.equal(res.body.toString('utf8'), textContent);
  });

  // TEST 16: Malformed Range header gracefully ignored per RFC 7233 (fallback to 200 OK)
  await t.test('Empirical: Malformed Range headers fallback to 200 OK with full content', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);

    const malformedHeaders = [
      'invalid-syntax',
      'bytes=',
      'bytes=-',
      'bytes=abc-def',
      'notbytes=0-10'
    ];

    for (const badRange of malformedHeaders) {
      const res = await requestHttp(url, {
        headers: {
          Authorization: `Bearer ${SESSION_TOKEN}`,
          Range: badRange
        }
      });

      assert.equal(res.statusCode, 200, `Range "${badRange}" must fallback to 200 OK`);
      assert.equal(res.headers['content-length'], String(total));
      assert.equal(res.body.toString('utf8'), textContent);
    }
  });

  // TEST 17: Seeking into middle (256MB) and end of 500MB+ sparse file without buffer delay
  await t.test('Empirical: O(1) seeking into middle (256MB) and end of 512MB file without buffer delay (<100ms)', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;

    // Seek to 128MB
    const t1 = Date.now();
    const res128 = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${128 * 1024 * 1024}-${128 * 1024 * 1024 + 14}`
      }
    });
    const d128 = Date.now() - t1;
    assert.equal(res128.statusCode, 206);
    assert.equal(res128.body.toString('utf8'), 'QUARTER_128M_MK');
    assert.ok(d128 < 100, `Seek to 128MB took ${d128}ms (expected < 100ms)`);

    // Seek to 256MB (middle)
    const t2 = Date.now();
    const res256 = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${256 * 1024 * 1024}-${256 * 1024 * 1024 + 14}`
      }
    });
    const d256 = Date.now() - t2;
    assert.equal(res256.statusCode, 206);
    assert.equal(res256.body.toString('utf8'), 'MIDPOINT_256M_M');
    assert.ok(d256 < 100, `Seek to 256MB took ${d256}ms (expected < 100ms)`);

    // Seek to 384MB
    const t3 = Date.now();
    const res384 = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${384 * 1024 * 1024}-${384 * 1024 * 1024 + 14}`
      }
    });
    const d384 = Date.now() - t3;
    assert.equal(res384.statusCode, 206);
    assert.equal(res384.body.toString('utf8'), 'THREEQTR_384M_M');
    assert.ok(d384 < 100, `Seek to 384MB took ${d384}ms (expected < 100ms)`);

    // Seek to end (last 12 bytes)
    const t4 = Date.now();
    const resEnd = await requestHttp(url, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: `bytes=${SPARSE_512MB - 12}-${SPARSE_512MB - 1}`
      }
    });
    const dEnd = Date.now() - t4;
    assert.equal(resEnd.statusCode, 206);
    assert.equal(resEnd.body.toString('utf8'), 'TAIL_EOF_MK!');
    assert.ok(dEnd < 100, `Seek to EOF took ${dEnd}ms (expected < 100ms)`);
  });

  // TEST 18: HEAD Method RFC Compliance with and without Range
  await t.test('Empirical: HEAD request with Range returns 206 headers without response body', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const res = await requestHttp(url, {
      method: 'HEAD',
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-1048575'
      }
    });

    assert.equal(res.statusCode, 206);
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-range'], `bytes 0-1048575/${SPARSE_512MB}`);
    assert.equal(res.headers['content-length'], '1048576');
    assert.equal(res.body.length, 0, 'HEAD response must have empty body');
  });

  await t.test('Empirical: HEAD request without Range returns 200 headers without response body', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=document.txt`;
    const total = Buffer.byteLength(textContent);
    const res = await requestHttp(url, {
      method: 'HEAD',
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`
      }
    });

    assert.equal(res.statusCode, 200);
    assert.equal(res.headers['accept-ranges'], 'bytes');
    assert.equal(res.headers['content-length'], String(total));
    assert.equal(res.body.length, 0, 'HEAD response must have empty body');
  });

  // TEST 19: High concurrency stress: 25 simultaneous range requests across 512MB file
  await t.test('Stress: 25 concurrent range requests across 512MB file execute cleanly without corruption', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const offsets = [
      0,
      1024,
      64 * 1024,
      1024 * 1024,
      32 * 1024 * 1024,
      64 * 1024 * 1024,
      128 * 1024 * 1024,
      150 * 1024 * 1024,
      200 * 1024 * 1024,
      256 * 1024 * 1024,
      300 * 1024 * 1024,
      384 * 1024 * 1024,
      400 * 1024 * 1024,
      450 * 1024 * 1024,
      500 * 1024 * 1024,
      510 * 1024 * 1024,
      512 * 1024 * 1024 - 100,
      512 * 1024 * 1024 - 15,
      100,
      5000,
      1000000,
      256 * 1024 * 1024 + 1000,
      384 * 1024 * 1024 + 5000,
      128 * 1024 * 1024 + 7,
      256 * 1024 * 1024 + 7
    ];

    const startTime = Date.now();
    const promises = offsets.map(async (offset, idx) => {
      const sliceSize = 256; // 256 bytes slice
      const endOffset = Math.min(SPARSE_512MB - 1, offset + sliceSize - 1);
      const expectedSize = endOffset - offset + 1;

      const res = await requestHttp(url, {
        headers: {
          Authorization: `Bearer ${SESSION_TOKEN}`,
          Range: `bytes=${offset}-${endOffset}`
        }
      });

      assert.equal(res.statusCode, 206, `Request #${idx} at offset ${offset} failed with status ${res.statusCode}`);
      assert.equal(res.headers['content-range'], `bytes ${offset}-${endOffset}/${SPARSE_512MB}`);
      assert.equal(res.headers['content-length'], String(expectedSize));
      assert.equal(res.body.length, expectedSize);

      // Verify specific sentinel if aligned
      if (offset === 0) {
        assert.equal(res.body.subarray(0, 15).toString('utf8'), 'HEAD_START_BYTE');
      } else if (offset === 128 * 1024 * 1024) {
        assert.equal(res.body.subarray(0, 15).toString('utf8'), 'QUARTER_128M_MK');
      } else if (offset === 256 * 1024 * 1024) {
        assert.equal(res.body.subarray(0, 15).toString('utf8'), 'MIDPOINT_256M_M');
      } else if (offset === 384 * 1024 * 1024) {
        assert.equal(res.body.subarray(0, 15).toString('utf8'), 'THREEQTR_384M_M');
      }
    });

    await Promise.all(promises);
    const totalDuration = Date.now() - startTime;
    assert.ok(totalDuration < 2000, `25 concurrent requests took ${totalDuration}ms (expected < 2000ms)`);
  });

  // TEST 20: Abrupt client socket termination mid-stream (Connection drop resilience)
  await t.test('Resilience: Server cleanly handles abrupt client socket termination mid-stream', async () => {
    const url = `${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`;
    const parsedUrl = new URL(url);

    // Make request and destroy socket after first chunk of data
    await new Promise((resolve) => {
      const req = http.request({
        hostname: parsedUrl.hostname,
        port: parsedUrl.port,
        path: parsedUrl.pathname + parsedUrl.search,
        method: 'GET',
        headers: {
          Authorization: `Bearer ${SESSION_TOKEN}`,
          Range: 'bytes=0-10485750' // 10MB chunk
        }
      }, (res) => {
        res.once('data', () => {
          // Abruptly destroy client socket
          req.destroy();
          resolve();
        });
      });

      req.on('error', () => {
        // Expected client abort error
        resolve();
      });

      req.end();
    });

    // Wait a brief moment for server to process close event
    await new Promise((r) => setTimeout(r, 50));

    // Next request must succeed immediately without server crash or hung descriptors
    const healthRes = await requestHttp(`${baseUrl}/api/health`);
    assert.equal(healthRes.statusCode, 200);

    const streamRes = await requestHttp(`${baseUrl}/api/shares/${share.id}/stream?path=massive_video.mp4`, {
      headers: {
        Authorization: `Bearer ${SESSION_TOKEN}`,
        Range: 'bytes=0-10'
      }
    });
    assert.equal(streamRes.statusCode, 206);
    assert.equal(streamRes.body.length, 11);
  });

  // TEST 21: Security & Path Confinement on streaming endpoint
  await t.test('Security: Stream route strictly rejects traversal, directory streaming, and missing files', async () => {
    // 1. Path traversal attempt on stream
    const traversalUrl = `${baseUrl}/api/shares/${share.id}/stream?path=../../test_secret.txt`;
    const resTraversal = await requestHttp(traversalUrl, {
      headers: { Authorization: `Bearer ${SESSION_TOKEN}` }
    });
    assert.equal(resTraversal.statusCode, 403, 'Path traversal on stream must return 403 Forbidden');

    // 2. Missing subpath parameter
    const noPathUrl = `${baseUrl}/api/shares/${share.id}/stream`;
    const resNoPath = await requestHttp(noPathUrl, {
      headers: { Authorization: `Bearer ${SESSION_TOKEN}` }
    });
    assert.equal(resNoPath.statusCode, 400, 'Missing subpath must return 400 Bad Request');

    // 3. Attempting to stream a directory
    const dirUrl = `${baseUrl}/api/shares/${share.id}/stream?path=`;
    const resDir = await requestHttp(dirUrl, {
      headers: { Authorization: `Bearer ${SESSION_TOKEN}` }
    });
    // Either 400 from handleStream or 400 from RangeStreamer.streamFile
    assert.ok(resDir.statusCode === 400 || resDir.statusCode === 403);

    // 4. Non-existent file
    const missingUrl = `${baseUrl}/api/shares/${share.id}/stream?path=nonexistent_file_404.mp4`;
    const resMissing = await requestHttp(missingUrl, {
      headers: { Authorization: `Bearer ${SESSION_TOKEN}` }
    });
    assert.equal(resMissing.statusCode, 404, 'Non-existent file must return 404 Not Found');
  });

  await t.test('Teardown: Stop HTTP server and clean up temporary files', async () => {
    if (server) {
      await server.stop();
    }
    await fs.rm(TMP_DIR, { recursive: true, force: true });
  });
});
