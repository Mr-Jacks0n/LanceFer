package com.lancefer.lancedrop.core

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FdspProtocol {
    val MAGIC_BYTES = byteArrayOf(0x46.toByte(), 0x44.toByte()) // 'FD'
    const val PROTOCOL_VERSION: Byte = 0x01
    const val HEADER_SIZE = 8

    const val TYPE_CLIPBOARD_SHARE: Byte = 0x05
    const val TYPE_TRANSFER_PROPOSAL: Byte = 0x10
    const val TYPE_TRANSFER_DECISION: Byte = 0x11
    const val TYPE_TRANSFER_RESUME_REQ: Byte = 0x12
    const val TYPE_CHUNK_DATA: Byte = 0x20
    const val TYPE_CHUNK_ACK: Byte = 0x21
    const val TYPE_TRANSFER_COMPLETE: Byte = 0x30
    const val TYPE_CHECKSUM_VERIFIED: Byte = 0x31
    const val TYPE_ERROR_ABORT: Byte = 0xFF.toByte()

    fun writeFrame(out: OutputStream, messageType: Byte, payload: ByteArray = ByteArray(0)) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.put(MAGIC_BYTES)
        header.put(PROTOCOL_VERSION)
        header.put(messageType)
        header.putInt(payload.size)
        out.write(header.array())
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    fun readFrame(input: InputStream): Pair<Byte, ByteArray> {
        val header = ByteArray(HEADER_SIZE)
        var totalRead = 0
        while (totalRead < HEADER_SIZE) {
            val count = input.read(header, totalRead, HEADER_SIZE - totalRead)
            if (count == -1) {
                if (totalRead == 0) throw java.io.EOFException("Fin de flux FDSP")
                throw IllegalStateException("Socket fermé prématurément lors de la lecture du header FDSP")
            }
            totalRead += count
        }
        if (header[0] != MAGIC_BYTES[0] || header[1] != MAGIC_BYTES[1]) {
            throw IllegalArgumentException("Magic bytes invalides : ${header[0]}, ${header[1]} (attendu FD)")
        }
        val type = header[3]
        val payloadLen = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val payload = ByteArray(payloadLen)
        var readBytes = 0
        while (readBytes < payloadLen) {
            val count = input.read(payload, readBytes, payloadLen - readBytes)
            if (count == -1) throw IllegalStateException("Socket fermé avant lecture complète du payload")
            readBytes += count
        }
        return Pair(type, payload)
    }

    fun encodeChunkPayload(fileIndex: Int, chunkIndex: Int, offset: Long, data: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        header.putShort(fileIndex.toShort())
        header.putShort(0.toShort())
        header.putInt(chunkIndex)
        header.putLong(offset)
        val result = ByteArray(16 + data.size)
        System.arraycopy(header.array(), 0, result, 0, 16)
        System.arraycopy(data, 0, result, 16, data.size)
        return result
    }

    fun decodeChunkPayload(payload: ByteArray): Triple<Int, Long, ByteArray> {
        if (payload.size < 16) throw IllegalArgumentException("Payload CHUNK_DATA trop court (< 16 octets)")
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        buf.short // fileIndex
        buf.short // reserved
        val chunkIndex = buf.int
        val offset = buf.long
        val data = ByteArray(payload.size - 16)
        System.arraycopy(payload, 16, data, 0, data.size)
        return Triple(chunkIndex, offset, data)
    }

    fun encodeAckPayload(fileIndex: Int, chunkIndex: Int, bytesWritten: Long): ByteArray {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(fileIndex.toShort())
        buf.putShort(0.toShort())
        buf.putInt(chunkIndex)
        buf.putLong(bytesWritten)
        return buf.array()
    }

    fun decodeAckPayload(payload: ByteArray): Triple<Int, Int, Long> {
        if (payload.size < 16) throw IllegalArgumentException("Payload CHUNK_ACK invalide (< 16 octets)")
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val fileIndex = buf.short.toInt() and 0xFFFF
        buf.short
        val chunkIndex = buf.int
        val bytesWritten = buf.long
        return Triple(fileIndex, chunkIndex, bytesWritten)
    }
}
