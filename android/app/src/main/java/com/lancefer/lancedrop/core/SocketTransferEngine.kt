package com.lancefer.lancedrop.core

import com.lancefer.lancedrop.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Moteur de transfert binaire TCP conforme au protocole LDSP v1
 */
object SocketTransferEngine {

    private const val BUFFER_SIZE = 1_048_576 // 1 Mo par chunk streaming

    suspend fun sendStream(
        targetIp: String,
        targetPort: Int,
        fileName: String,
        fileSize: Long,
        inputStream: InputStream,
        onProgress: (TransferState.Transferring) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val socket = Socket()
        socket.connect(InetSocketAddress(targetIp, targetPort), 5000)

        val outputStream: OutputStream = socket.getOutputStream()

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var totalTransferred = 0L
        val startTime = System.currentTimeMillis()

        try {
            val header = "LDSP\u0001".toByteArray(Charsets.UTF_8)
            outputStream.write(header)
            outputStream.flush()

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                outputStream.flush()

                digest.update(buffer, 0, bytesRead)
                totalTransferred += bytesRead

                val elapsedTimeSec = (System.currentTimeMillis() - startTime) / 1000.0
                val speed = if (elapsedTimeSec > 0) totalTransferred / elapsedTimeSec else 0.0
                val progress = if (fileSize > 0) totalTransferred.toFloat() / fileSize else 0f
                val remainingSec = if (speed > 0) ((fileSize - totalTransferred) / speed).toLong() else 0L

                onProgress(
                    TransferState.Transferring(
                        fileName = fileName,
                        totalBytes = fileSize,
                        transferredBytes = totalTransferred,
                        speedBytesPerSec = speed,
                        progressPercentage = progress,
                        remainingSeconds = remainingSec,
                        peerName = targetIp
                    )
                )
            }

            val sha256Checksum = digest.digest().joinToString("") { "%02x".format(it) }
            return@withContext sha256Checksum
        } finally {
            inputStream.close()
            socket.close()
        }
    }
}
