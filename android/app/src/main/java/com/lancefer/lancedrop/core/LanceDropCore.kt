package com.lancefer.lancedrop.core

/**
 * LanceDrop Core - Client et Moteur de Transfert Local pour Android
 */
object LanceDropCore {
    const val PROTOCOL_VERSION: Byte = 1
    const val DEFAULT_TRANSFER_PORT: Int = 42420
    const val DEFAULT_DISCOVERY_PORT: Int = 42424
    const val CHUNK_SIZE: Int = 1024 * 1024 // 1 Mo par chunk streaming
}
