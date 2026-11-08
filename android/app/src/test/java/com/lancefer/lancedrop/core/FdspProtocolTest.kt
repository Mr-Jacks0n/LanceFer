package com.lancefer.lancedrop.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FdspProtocolTest {

    @Test
    fun testFrameWriteAndRead() {
        val payload = "{\"status\":\"ok\"}".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_DECISION, payload)

        val input = ByteArrayInputStream(out.toByteArray())
        val (type, readPayload) = FdspProtocol.readFrame(input)

        assertEquals(FdspProtocol.TYPE_TRANSFER_DECISION, type)
        assertArrayEquals(payload, readPayload)
    }

    @Test
    fun testChunkEncodeDecode() {
        val testData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val chunkIdx = 42
        val offset = 42000L

        val encoded = FdspProtocol.encodeChunkPayload(0, chunkIdx, offset, testData)
        val (decodedIdx, decodedOffset, decodedData) = FdspProtocol.decodeChunkPayload(encoded)

        assertEquals(chunkIdx, decodedIdx)
        assertEquals(offset, decodedOffset)
        assertArrayEquals(testData, decodedData)
    }

    @Test
    fun testAckEncodeDecode() {
        val chunkIdx = 7
        val bytesWritten = 1048576L

        val encoded = FdspProtocol.encodeAckPayload(0, chunkIdx, bytesWritten)
        val (fileIdx, decodedIdx, decodedWritten) = FdspProtocol.decodeAckPayload(encoded)

        assertEquals(0, fileIdx)
        assertEquals(chunkIdx, decodedIdx)
        assertEquals(bytesWritten, decodedWritten)
    }

    @Test(expected = java.io.EOFException::class)
    fun testCleanEofThrowsEofException() {
        val emptyInput = ByteArrayInputStream(ByteArray(0))
        FdspProtocol.readFrame(emptyInput)
    }

    @Test(expected = IllegalStateException::class)
    fun testPrematureHeaderCloseThrowsIllegalState() {
        val truncatedHeader = byteArrayOf(0x46.toByte(), 0x44.toByte()) // Only 2 bytes instead of 8
        val truncatedInput = ByteArrayInputStream(truncatedHeader)
        FdspProtocol.readFrame(truncatedInput)
    }
}
