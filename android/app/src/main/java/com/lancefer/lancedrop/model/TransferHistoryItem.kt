package com.lancefer.lancedrop.model

import android.net.Uri

data class TransferHistoryItem(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val peerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = false,
    val sha256Checksum: String? = null,
    val fileUri: Uri? = null,
    val filePath: String? = null,
    val durationMs: Long = 0L,
    val averageSpeedBytesPerSec: Double = 0.0,
    val peakSpeedBytesPerSec: Double = 0.0,
    val status: String = "COMPLETED",
    val isDirectory: Boolean = false
)
