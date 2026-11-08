package com.lancefer.lancedrop.data

import com.lancefer.lancedrop.model.TransferHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEntityTest {

    @Test
    fun testHistoryEntityFidelityAndMapping() {
        val entity = HistoryEntity(
            id = "tr-test-12345",
            fileName = "documents/annual_report.pdf",
            fileSize = 10485760L,
            peerName = "Desktop-PC",
            timestamp = 1773000000000L,
            isIncoming = true,
            sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            filePath = "/storage/emulated/0/Download/LanceDrop/documents/annual_report.pdf",
            fileUriString = null,
            durationMs = 2500L,
            averageSpeedBytesPerSec = 4194304.0,
            peakSpeedBytesPerSec = 5242880.0,
            status = "COMPLETED",
            isDirectory = true
        )

        val item = entity.toTransferHistoryItem()

        assertEquals("tr-test-12345", item.id)
        assertEquals("documents/annual_report.pdf", item.fileName)
        assertEquals(10485760L, item.fileSize)
        assertEquals("Desktop-PC", item.peerName)
        assertEquals(1773000000000L, item.timestamp)
        assertTrue(item.isIncoming)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", item.sha256Checksum)
        assertEquals(2500L, item.durationMs)
        assertEquals(4194304.0, item.averageSpeedBytesPerSec, 0.001)
        assertEquals(5242880.0, item.peakSpeedBytesPerSec, 0.001)
        assertEquals("COMPLETED", item.status)
        assertTrue(item.isDirectory)

        val roundTrip = HistoryEntity.fromTransferHistoryItem(item)
        assertEquals(entity.id, roundTrip.id)
        assertEquals(entity.fileName, roundTrip.fileName)
        assertEquals(entity.fileSize, roundTrip.fileSize)
        assertEquals(entity.peerName, roundTrip.peerName)
        assertEquals(entity.sha256Checksum, roundTrip.sha256Checksum)
        assertEquals(entity.durationMs, roundTrip.durationMs)
        assertEquals(entity.averageSpeedBytesPerSec, roundTrip.averageSpeedBytesPerSec, 0.001)
        assertEquals(entity.peakSpeedBytesPerSec, roundTrip.peakSpeedBytesPerSec, 0.001)
        assertEquals(entity.status, roundTrip.status)
        assertEquals(entity.isDirectory, roundTrip.isDirectory)
    }

    @Test
    fun testTransferHistoryItemDefaults() {
        val minimalItem = TransferHistoryItem(
            id = "min-1",
            fileName = "notes.txt",
            fileSize = 512L,
            peerName = "Peer1"
        )

        assertEquals("min-1", minimalItem.id)
        assertEquals("notes.txt", minimalItem.fileName)
        assertEquals(512L, minimalItem.fileSize)
        assertEquals("Peer1", minimalItem.peerName)
        assertFalse(minimalItem.isIncoming)
        assertNull(minimalItem.sha256Checksum)
        assertNull(minimalItem.fileUri)
        assertNull(minimalItem.filePath)
        assertEquals(0L, minimalItem.durationMs)
        assertEquals(0.0, minimalItem.averageSpeedBytesPerSec, 0.001)
        assertEquals(0.0, minimalItem.peakSpeedBytesPerSec, 0.001)
        assertEquals("COMPLETED", minimalItem.status)
        assertFalse(minimalItem.isDirectory)
    }

    @Test
    fun testInterruptedStatusRepresentation() {
        val interruptedItem = TransferHistoryItem(
            id = "crash-recover-01",
            fileName = "large_archive.zip",
            fileSize = 1073741824L,
            peerName = "Station-Work",
            status = "INTERRUPTED"
        )

        assertEquals("INTERRUPTED", interruptedItem.status)
        val entity = HistoryEntity.fromTransferHistoryItem(interruptedItem)
        assertEquals("INTERRUPTED", entity.status)
    }
}
