package com.lancefer.lancedrop.core

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.lancefer.lancedrop.model.TransferState
import com.lancefer.lancedrop.utils.SoundEffectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.ServerSocket
import java.security.MessageDigest

class SocketTransferServer(
    private val context: Context,
    private val port: Int = 42420,
    var conflictResolution: String = "RENAME", // "PROMPT", "RENAME", "OVERWRITE", "SKIP"
    private val onConflictPrompt: (suspend (fileName: String) -> String)? = null,
    var sessionKey: String? = null,
    private val onProgress: (TransferState.Transferring) -> Unit,
    private val onCompleted: (
        fileName: String,
        size: Long,
        checksum: String,
        uri: Uri?,
        durationMs: Long,
        avgSpeed: Double,
        peakSpeed: Double,
        isDirectory: Boolean
    ) -> Unit,
    private val onError: (String) -> Unit,
    var onTransferPrompt: (suspend (peerName: String, fileName: String, size: Long, isDirectory: Boolean) -> Boolean)? = null
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    companion object {
        fun sanitizeRelativePath(rawFileName: String): Pair<String, String> {
            val normalized = rawFileName.replace('\\', '/')
            val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
            if (segments.isEmpty()) {
                return Pair("", "file_${System.currentTimeMillis()}")
            }
            val safeSegments = segments.map { it.replace("[<>:\"/\\\\|?*\\x00-\\x1F]".toRegex(), "_") }
            val relativeSubDir = if (safeSegments.size > 1) safeSegments.dropLast(1).joinToString("/") else ""
            val safeFileName = safeSegments.last()
            return Pair(relativeSubDir, safeFileName)
        }

        fun resolveUniqueName(fileName: String, existsPredicate: (String) -> Boolean): String {
            val dotIndex = fileName.lastIndexOf('.')
            val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""
            var counter = 1
            var candidate = "$baseName ($counter)$extension"
            while (existsPredicate(candidate)) {
                counter++
                candidate = "$baseName ($counter)$extension"
            }
            return candidate
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(port))
            }
            isRunning = true

            while (isRunning) {
                val client = serverSocket?.accept() ?: break
                CoroutineScope(Dispatchers.IO).launch {
                    handleIncomingTransfer(client)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                SoundEffectManager.playErrorAlert()
                onError(e.message ?: "Erreur serveur TCP")
            }
        }
    }

    private suspend fun handleIncomingTransfer(socket: java.net.Socket) = withContext(Dispatchers.IO) {
        val input = java.io.BufferedInputStream(socket.getInputStream(), 64 * 1024)
        val out = java.io.BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
        var targetOutputStream: OutputStream? = null
        var downloadUri: Uri? = null

        try {
            // Boucle de réception multi-fichiers pour le streaming de dossiers récursifs
            while (isRunning && !socket.isClosed) {
                val (propType, propPayload) = try {
                    FdspProtocol.readFrame(input)
                } catch (e: java.io.EOFException) {
                    // Fin propre du flux par l'émetteur
                    break
                } catch (e: Exception) {
                    break
                }

                if (propType == FdspProtocol.TYPE_CLIPBOARD_SHARE) {
                    try {
                        val clipText = String(propPayload, Charsets.UTF_8)
                        withContext(Dispatchers.Main) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("LanceDrop Clipboard", clipText)
                            clipboard?.setPrimaryClip(clip)
                        }
                    } catch (_: Exception) {}
                    continue
                }

                if (propType != FdspProtocol.TYPE_TRANSFER_PROPOSAL) break

                val proposal = JSONObject(String(propPayload, Charsets.UTF_8))
                val rawFileName = proposal.getString("relative_path")
                val expectedSize = proposal.getLong("size")
                val expectedSha256 = proposal.getString("sha256")
                val transferId = proposal.getString("transfer_id")

                // Sanitisation anti-path-traversal tout en préservant l'arborescence des dossiers
                val (relativeSubDir, safeFileName) = sanitizeRelativePath(rawFileName)
                val fullRelativePath = if (relativeSubDir.isNotEmpty()) "$relativeSubDir/$safeFileName" else safeFileName

                // Interception tactile AirDrop / Quick Share de confirmation de réception
                if (onTransferPrompt != null) {
                    val senderName = socket.inetAddress?.hostName?.takeIf { it.isNotBlank() }
                        ?: socket.inetAddress?.hostAddress
                        ?: "PC"
                    val isDir = relativeSubDir.isNotEmpty()
                    val accepted = onTransferPrompt?.invoke(senderName, safeFileName, expectedSize, isDir) ?: true
                    if (!accepted) {
                        val rej = JSONObject().apply {
                            put("accepted", false)
                            put("reason", "USER_DECLINED")
                            put("transfer_id", transferId)
                        }
                        FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_DECISION, rej.toString().toByteArray(Charsets.UTF_8))
                        continue
                    }
                }

                // Gestion des conflits
                val exists = fileAlreadyExists(safeFileName, relativeSubDir)
                var currentResolution = conflictResolution

                if (exists) {
                    if (currentResolution.equals("PROMPT", ignoreCase = true) && onConflictPrompt != null) {
                        currentResolution = onConflictPrompt.invoke(safeFileName)
                    }

                    if (currentResolution.equals("SKIP", ignoreCase = true)) {
                        val decision = JSONObject().apply {
                            put("accepted", false)
                            put("reason", "FILE_EXISTS_SKIPPED")
                            put("transfer_id", transferId)
                        }
                        FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_DECISION, decision.toString().toByteArray(Charsets.UTF_8))
                        continue
                    } else if (currentResolution.equals("OVERWRITE", ignoreCase = true)) {
                        deleteExistingFile(safeFileName, relativeSubDir)
                    }
                }

                var finalFileName = safeFileName
                if (exists && currentResolution.equals("RENAME", ignoreCase = true)) {
                    finalFileName = resolveUniqueName(safeFileName) { candidate ->
                        fileAlreadyExists(candidate, relativeSubDir)
                    }
                }

                val isEncrypted = proposal.optBoolean("encrypted", false)

                // 1b. Contrôle préalable d'espace disque disponible
                if (expectedSize > 0) {
                    try {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val statFs = StatFs(downloadsDir.absolutePath)
                        val availableBytes = statFs.availableBytes
                        if (availableBytes < expectedSize) {
                            val rej = JSONObject().apply {
                                put("accepted", false)
                                put("reason", "INSUFFICIENT_STORAGE")
                                put("available", availableBytes)
                                put("required", expectedSize)
                                put("transfer_id", transferId)
                            }
                            FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_DECISION, rej.toString().toByteArray(Charsets.UTF_8))
                            SoundEffectManager.playErrorAlert()
                            onError("Espace de stockage insuffisant pour recevoir $safeFileName")
                            break
                        }
                    } catch (_: Exception) {}
                }

                // 2. Création du flux de destination via MediaStore.Downloads
                val uri = createDownloadUri(finalFileName, relativeSubDir)
                downloadUri = uri
                val rawTarget = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Impossible d'ouvrir le stockage MediaStore pour $finalFileName")
                targetOutputStream = java.io.BufferedOutputStream(rawTarget, 64 * 1024)

                // 3. Réponse TRANSFER_DECISION acceptée
                val decision = JSONObject().apply {
                    put("accepted", true)
                    put("transfer_id", transferId)
                }
                FdspProtocol.writeFrame(out, FdspProtocol.TYPE_TRANSFER_DECISION, decision.toString().toByteArray(Charsets.UTF_8))

                // 4. Boucle de réception des CHUNK_DATA et émission des CHUNK_ACK
                val digest = MessageDigest.getInstance("SHA-256")
                var totalReceived = 0L
                val startTime = System.currentTimeMillis()
                var peakSpeed = 0.0

                var receivedComplete = false
                while (totalReceived < expectedSize) {
                    val (frameType, framePayload) = FdspProtocol.readFrame(input)
                    if (frameType == FdspProtocol.TYPE_TRANSFER_COMPLETE) {
                        receivedComplete = true
                        break
                    }
                    if (frameType != FdspProtocol.TYPE_CHUNK_DATA) continue

                    val (chunkIdx, _, chunkData) = FdspProtocol.decodeChunkPayload(framePayload)
                    val rawData = if (isEncrypted && !sessionKey.isNullOrBlank()) {
                        com.lancefer.lancedrop.data.CryptoStreamManager.decryptChunk(chunkData, sessionKey!!, chunkIdx)
                    } else {
                        chunkData
                    }
                    targetOutputStream.write(rawData)
                    digest.update(rawData)
                    totalReceived += rawData.size

                    // Émission de CHUNK_ACK
                    val ack = FdspProtocol.encodeAckPayload(0, chunkIdx, chunkData.size.toLong())
                    FdspProtocol.writeFrame(out, FdspProtocol.TYPE_CHUNK_ACK, ack)

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsed > 0) totalReceived / elapsed else 0.0
                    if (speed > peakSpeed) peakSpeed = speed
                    val progress = if (expectedSize > 0) totalReceived.toFloat() / expectedSize else 1f

                    onProgress(
                        TransferState.Transferring(
                            fileName = fullRelativePath,
                            totalBytes = expectedSize,
                            transferredBytes = totalReceived,
                            speedBytesPerSec = speed,
                            progressPercentage = progress,
                            remainingSeconds = if (speed > 0) ((expectedSize - totalReceived) / speed).toLong() else 0L,
                            peerName = socket.inetAddress?.hostAddress ?: "PC"
                        )
                    )
                }

                if (!receivedComplete) {
                    val (completeType, _) = FdspProtocol.readFrame(input)
                    if (completeType != FdspProtocol.TYPE_TRANSFER_COMPLETE) {
                        throw IllegalStateException("Trame attendue TRANSFER_COMPLETE, reçu $completeType")
                    }
                }

                // 5. Validation Checksum finale
                targetOutputStream.flush()
                try { targetOutputStream.close() } catch (_: Exception) {}
                targetOutputStream = null

                val computedHash = digest.digest().joinToString("") { "%02x".format(it) }

                if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                    FdspProtocol.writeFrame(out, FdspProtocol.TYPE_ERROR_ABORT, "Checksum mismatch".toByteArray(Charsets.UTF_8))
                    SoundEffectManager.playErrorAlert()
                    throw IllegalStateException("Échec de vérification SHA-256 finale")
                }

                // Acquittement CHECKSUM_VERIFIED
                val verifiedJson = JSONObject().apply {
                    put("verified", true)
                    put("transfer_id", transferId)
                    put("sha256", computedHash)
                }
                FdspProtocol.writeFrame(out, FdspProtocol.TYPE_CHECKSUM_VERIFIED, verifiedJson.toString().toByteArray(Charsets.UTF_8))

                val durationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val avgSpeed = if (durationMs > 0) (expectedSize.toDouble() / (durationMs / 1000.0)) else 0.0

                SoundEffectManager.playSuccessChime()

                onCompleted(
                    fullRelativePath,
                    expectedSize,
                    computedHash,
                    downloadUri,
                    durationMs,
                    avgSpeed,
                    peakSpeed,
                    relativeSubDir.isNotEmpty()
                )
            }
        } catch (e: Exception) {
            SoundEffectManager.playErrorAlert()
            onError(e.message ?: "Erreur pendant la réception")
        } finally {
            try { targetOutputStream?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun fileAlreadyExists(fileName: String, subDir: String): Boolean {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteExistingFile(fileName: String, subDir: String) {
        try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    context.contentResolver.delete(uri, null, null)
                }
            }
        } catch (_: Exception) {}
    }

    private fun createDownloadUri(fileName: String, subDir: String): Uri {
        val targetRelativePath = if (subDir.isNotEmpty()) {
            Environment.DIRECTORY_DOWNLOADS + "/LanceDrop/" + subDir
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/LanceDrop"
        }
        val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
        val detectedMime = if (ext.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, detectedMime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            }
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Impossible d'insérer l'URI de téléchargement")
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
