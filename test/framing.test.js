import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeFrame, FrameParser } from '../src/protocol/framing.js';
import { MESSAGE_TYPES, MAGIC_BYTES, PROTOCOL_VERSION } from '../src/protocol/constants.js';

test('Framing: encodeFrame génère un en-tête conforme', () => {
  const payload = Buffer.from('test-payload', 'utf8');
  const frame = encodeFrame(MESSAGE_TYPES.HELLO_REQUEST, payload);

  assert.equal(frame[0], MAGIC_BYTES[0]);
  assert.equal(frame[1], MAGIC_BYTES[1]);
  assert.equal(frame[2], PROTOCOL_VERSION);
  assert.equal(frame[3], MESSAGE_TYPES.HELLO_REQUEST);
  assert.equal(frame.readUInt32BE(4), payload.length);
  assert.deepEqual(frame.subarray(8), payload);
});

test('Framing: FrameParser décode une trame complète', (_, done) => {
  const parser = new FrameParser();
  const payload = Buffer.from('hello-world', 'utf8');
  const frame = encodeFrame(MESSAGE_TYPES.TRANSFER_PROPOSAL, payload);

  parser.on('frame', ({ type, payload: receivedPayload }) => {
    assert.equal(type, MESSAGE_TYPES.TRANSFER_PROPOSAL);
    assert.deepEqual(receivedPayload, payload);
    done();
  });

  parser.push(frame);
});

test('Framing: FrameParser gère la fragmentation réseau octet par octet', (_, done) => {
  const parser = new FrameParser();
  const payload = Buffer.from('fragmented-streaming-data', 'utf8');
  const frame = encodeFrame(MESSAGE_TYPES.CHUNK_DATA, payload);

  parser.on('frame', ({ type, payload: receivedPayload }) => {
    assert.equal(type, MESSAGE_TYPES.CHUNK_DATA);
    assert.deepEqual(receivedPayload, payload);
    done();
  });

  // Injection octet par octet pour simuler une fragmentation extrême
  for (let i = 0; i < frame.length; i++) {
    parser.push(frame.subarray(i, i + 1));
  }
});

test('Framing: FrameParser rejette les magic bytes corrompus', (_, done) => {
  const parser = new FrameParser();
  const corruptBuffer = Buffer.from([0x99, 0x99, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00]);

  parser.on('error', err => {
    assert.match(err.message, /signature magique introuvable/);
    done();
  });

  parser.push(corruptBuffer);
});
