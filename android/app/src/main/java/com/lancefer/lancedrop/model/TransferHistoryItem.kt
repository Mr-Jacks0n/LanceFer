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
    val fileUri: Uri? = null
)
