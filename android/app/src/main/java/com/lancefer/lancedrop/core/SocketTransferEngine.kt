package com.lancefer.lancedrop.core

import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import kotlin.math.ceil

data class TransferSendResult(
    val sha256: String,
    val durationMs: Long,
    val averageSpeedBytesPerSec: Double,
    val peakSpeedBytesPerSec: Double,
    val totalBytes: Long
)

object SocketTransferEngine {
    private const val CHUNK_SIZE = 1024 * 1024

    suspend fun sendStreamFdspResult(
        targetIp: String,
        targetPort: Int,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        precomputedSha256: String? = null,
        transferIdOverride: String? = null,
        sessionKey: String? = null,
        onProgress: (TransferState.Transferring) -> Unit
    ): TransferSendResult = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(targetIp, targetPort), 6000)

        val out = java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
        val input = java.io.BufferedInputStream(socket.getInputStream(), 64 * 1024)
        val bufferedInput = java.io.BufferedInputStream(inputStream, 64 * 1024)
        val transferId = transferIdOverride ?: ("tr-" + System.currentTimeMillis() + "-" + (1000..9999).random())
        val totalChunks = if (fileSize <= 0) 1 else ceil(fileSize.toDouble() / CHUNK_SIZE).toInt()
        val finalSha256 = precomputedSha256 ?: ""

        val startTime = System.currentTimeMillis()
        var peakSpeed = 0.0

        try {
            val proposal = JSONObject().apply {
                put("transfer_id", transferId)
                put("file_id", "f-001")
                put("relative_path", fileName)
                put("size", fileSize)
                put("chunk_size", CHUNK_SIZE)
                put("total_chunks", totalChunks)
                put("sha256", finalSha256)
                if (!sessionKey.isNullOrBlank()) {
                    put("encrypted", true)
                }
            }
            FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_PROPOSAL, proposal.toString().toByteArray(Charsets.UTF_8))

            val (decType, decPayload) = FdspProtocol.readFrame(input)
            if (decType == FdspProtocol.TYPE_TRANSFER_DECISION) {
                val decJson = JSONObject(String(decPayload, Charsets.UTF_8))
                if (!decJson.optBoolean("accepted", true)) {
                    val reason = decJson.optString("reason", "UNKNOWN")
                    SoundEffectManager.playErrorAlert()
                    if (reason == "INSUFFICIENT_STORAGE") {
                        throw IllegalStateException("Espace disque insuffisant sur l'appareil récepteur")
                    }
                    throw IllegalStateException("Refus du récepteur : $reason")
                }
            } else if (decType == FdspProtocol.TYPE_ERROR_ABORT) {
                SoundEffectManager.playErrorAlert()
                throw IllegalStateException("Refus du récepteur : " + String(decPayload, Charsets.UTF_8))
            }

            val buffer = ByteArray(CHUNK_SIZE)
            var totalSent = 0L
            val runningDigest = MessageDigest.getInstance("SHA-256")

            for (chunkIdx in 0 until totalChunks) {
                val offset = chunkIdx.toLong() * CHUNK_SIZE
                val toRead = ((fileSize - offset).coerceAtMost(CHUNK_SIZE.toLong())).toInt()
                var readCount = 0
                while (readCount < toRead) {
                    val r = bufferedInput.read(buffer, readCount, toRead - readCount)
                    if (r == -1) break
                    readCount += r
                }
                if (readCount <= 0) break

                val chunkData = if (readCount == buffer.size) buffer else buffer.copyOf(readCount)
                runningDigest.update(chunkData)
                val dataToSend = if (!sessionKey.isNullOrBlank()) {
                    com.lancefer.lancedrop.data.CryptoStreamManager.encryptChunk(chunkData, sessionKey, chunkIdx)
                } else {
                    chunkData
                }
                val payload = FdspProtocol.encodeChunkPayload(0, chunkIdx, offset, dataToSend)
                FdspProtocol.writeFrame(out, FdspProtocol.TYPE_CHUNK_DATA, payload)

                val (ackType, ackPayload) = FdspProtocol.readFrame(input)
                if (ackType != FdspProtocol.TYPE_CHUNK_ACK) {
                    SoundEffectManager.playErrorAlert()
                    throw IllegalStateException("Attendu CHUNK_ACK, reçu " + ackType)
                }
                val (_, _, written) = FdspProtocol.decodeAckPayload(ackPayload)
                totalSent += written

                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                val speed = if (elapsed > 0) totalSent / elapsed else 0.0
                if (speed > peakSpeed) peakSpeed = speed
                val percent = if (fileSize > 0) totalSent.toFloat() / fileSize else 1f
                val remaining = if (speed > 0) ((fileSize - totalSent) / speed).toLong() else 0L

                onProgress(
                    TransferState.Transferring(
                        fileName = fileName,
                        totalBytes = fileSize,
                        transferredBytes = totalSent,
                        speedBytesPerSec = speed,
                        progressPercentage = percent,
                        remainingSeconds = remaining,
                        peerName = targetIp
                    )
                )
            }

            val computedSha = runningDigest.digest().joinToString("") { "%02x".format(it) }
            val complete = JSONObject().apply {
                put("transfer_id", transferId)
                put("file_id", "f-001")
                put("sha256", if (finalSha256.isNotEmpty()) finalSha256 else computedSha)
            }
            FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_COMPLETE, complete.toString().toByteArray(Charsets.UTF_8))

            val (confirmType, _) = FdspProtocol.readFrame(input)
            if (confirmType != FdspProtocol.TYPE_CHECKSUM_VERIFIED) {
                SoundEffectManager.playErrorAlert()
                throw IllegalStateException("Échec de vérification SHA-256 finale")
            }

            val verifiedSha = if (finalSha256.isNotEmpty()) finalSha256 else computedSha
            val durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            val avgSpeed = (totalSent.toDouble() / (durationMs / 1000.0))

            SoundEffectManager.playSuccessChime()

            return@withContext TransferSendResult(
                sha256 = verifiedSha,
                durationMs = durationMs,
                averageSpeedBytesPerSec = avgSpeed,
                peakSpeedBytesPerSec = peakSpeed,
                totalBytes = totalSent
            )
        } catch (e: Exception) {
            SoundEffectManager.playErrorAlert()
            throw e
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    suspend fun sendStreamFdsp(
        targetIp: String,
        targetPort: Int,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        precomputedSha256: String? = null,
        onProgress: (TransferState.Transferring) -> Unit
    ): String {
        return sendStreamFdspResult(
            targetIp = targetIp,
            targetPort = targetPort,
            fileName = fileName,
            fileSize = fileSize,
            inputStream = inputStream,
            precomputedSha256 = precomputedSha256,
            transferIdOverride = null,
            onProgress = onProgress
        ).sha256
    }

    suspend fun sendStream(
        targetIp: String,
        targetPort: Int,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        onProgress: (TransferState.Transferring) -> Unit
    ): String = sendStreamFdsp(targetIp, targetPort, fileName, fileSize, inputStream, null, onProgress)
}
