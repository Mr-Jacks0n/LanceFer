package com.lancefer.lancedrop.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SocketTransferPromptTest {

    @Test
    fun testTransferProposalDeclineFrameStructure() {
        // Simule la décision générée lorsque l'utilisateur refuse le transfert
        val transferId = "tr-12345"
        val declineJson = JSONObject().apply {
            put("accepted", false)
            put("reason", "USER_DECLINED")
            put("transfer_id", transferId)
        }

        val out = ByteArrayOutputStream()
        FdspProtocol.writeFrame(
            out,
            FdspProtocol.TYPE_TRANSFER_DECISION,
            declineJson.toString().toByteArray(Charsets.UTF_8)
        )

        val input = ByteArrayInputStream(out.toByteArray())
        val (frameType, payload) = FdspProtocol.readFrame(input)

        assertEquals(FdspProtocol.TYPE_TRANSFER_DECISION, frameType)
        val parsed = JSONObject(String(payload, Charsets.UTF_8))
        assertFalse(parsed.getBoolean("accepted"))
        assertEquals("USER_DECLINED", parsed.getString("reason"))
        assertEquals(transferId, parsed.getString("transfer_id"))
    }

    @Test
    fun testTransferProposalAcceptFrameStructure() {
        val transferId = "tr-67890"
        val acceptJson = JSONObject().apply {
            put("accepted", true)
            put("transfer_id", transferId)
        }

        val out = ByteArrayOutputStream()
        FdspProtocol.writeFrame(
            out,
            FdspProtocol.TYPE_TRANSFER_DECISION,
            acceptJson.toString().toByteArray(Charsets.UTF_8)
        )

        val input = ByteArrayInputStream(out.toByteArray())
        val (frameType, payload) = FdspProtocol.readFrame(input)

        assertEquals(FdspProtocol.TYPE_TRANSFER_DECISION, frameType)
        val parsed = JSONObject(String(payload, Charsets.UTF_8))
        assertTrue(parsed.getBoolean("accepted"))
        assertEquals(transferId, parsed.getString("transfer_id"))
    }

    @Test
    fun testProposalSanitizationExtractsSafeNameAndDirectory() {
        val proposal1 = "photos/summer/beach.jpg"
        val (subDir1, file1) = SocketTransferServer.sanitizeRelativePath(proposal1)
        assertEquals("photos/summer", subDir1)
        assertEquals("beach.jpg", file1)
        assertTrue(subDir1.isNotEmpty()) // isDirectory = true

        val proposal2 = "single_file.mp4"
        val (subDir2, file2) = SocketTransferServer.sanitizeRelativePath(proposal2)
        assertEquals("", subDir2)
        assertEquals("single_file.mp4", file2)
        assertFalse(subDir2.isNotEmpty()) // isDirectory = false
    }
}
