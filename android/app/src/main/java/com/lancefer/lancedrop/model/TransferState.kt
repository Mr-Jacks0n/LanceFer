package com.lancefer.lancedrop.model

/**
 * État courant d'un transfert de fichier
 */
sealed class TransferState {
    data object Idle : TransferState()
    
    data class Transferring(
        val fileName: String,
        val totalBytes: Long,
        val transferredBytes: Long,
        val speedBytesPerSec: Double,
        val progressPercentage: Float,
        val remainingSeconds: Long,
        val peerName: String,
        val isPaused: Boolean = false
    ) : TransferState()

    data class Completed(
        val fileName: String,
        val totalBytes: Long,
        val sha256Checksum: String,
        val peerName: String
    ) : TransferState()

    data class Error(
        val message: String,
        val canResume: Boolean = true
    ) : TransferState()
}
