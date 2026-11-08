package com.lancefer.lancedrop.data

import android.net.Uri
import com.lancefer.lancedrop.model.TransferHistoryItem

data class HistoryEntity(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val peerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = false,
    val sha256Checksum: String? = null,
    val filePath: String? = null,
    val fileUriString: String? = null,
    val durationMs: Long = 0L,
    val averageSpeedBytesPerSec: Double = 0.0,
    val peakSpeedBytesPerSec: Double = 0.0,
    val status: String = "COMPLETED",
    val isDirectory: Boolean = false
) {
    fun toTransferHistoryItem(): TransferHistoryItem {
        return TransferHistoryItem(
            id = id,
            fileName = fileName,
            fileSize = fileSize,
            peerName = peerName,
            timestamp = timestamp,
            isIncoming = isIncoming,
            sha256Checksum = sha256Checksum,
            fileUri = fileUriString?.let { Uri.parse(it) },
            filePath = filePath,
            durationMs = durationMs,
            averageSpeedBytesPerSec = averageSpeedBytesPerSec,
            peakSpeedBytesPerSec = peakSpeedBytesPerSec,
            status = status,
            isDirectory = isDirectory
        )
    }

    companion object {
        fun fromTransferHistoryItem(item: TransferHistoryItem): HistoryEntity {
            return HistoryEntity(
                id = item.id,
                fileName = item.fileName,
                fileSize = item.fileSize,
                peerName = item.peerName,
                timestamp = item.timestamp,
                isIncoming = item.isIncoming,
                sha256Checksum = item.sha256Checksum,
                filePath = item.filePath,
                fileUriString = item.fileUri?.toString(),
                durationMs = item.durationMs,
                averageSpeedBytesPerSec = item.averageSpeedBytesPerSec,
                peakSpeedBytesPerSec = item.peakSpeedBytesPerSec,
                status = item.status,
                isDirectory = item.isDirectory
            )
        }
    }
}
