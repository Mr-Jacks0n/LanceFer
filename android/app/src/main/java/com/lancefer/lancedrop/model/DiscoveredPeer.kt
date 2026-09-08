package com.lancefer.lancedrop.model

/**
 * Représente un ordinateur ou terminal découvert sur le réseau local.
 */
data class DiscoveredPeer(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 42420,
    val osType: OsType = OsType.WINDOWS,
    val isTrusted: Boolean = false,
    val isOnline: Boolean = true
)

enum class OsType {
    WINDOWS,
    LINUX,
    MACOS,
    ANDROID,
    UNKNOWN
}
