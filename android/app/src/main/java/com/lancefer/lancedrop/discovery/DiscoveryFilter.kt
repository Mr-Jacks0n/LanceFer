package com.lancefer.lancedrop.discovery

import com.lancefer.lancedrop.model.DiscoveredPeer

/**
 * Filtre pur Kotlin découplé du SDK Android pour éliminer rigoureusement l'auto-détection
 * de l'appareil local à travers toutes les interfaces (Wi-Fi, Ethernet, Hotspot, VPN, Loopback).
 * Conçu sans dépendance Android non mockable pour une exécution déterministe sur JVM JUnit 4.
 */
class DiscoveryFilter(
    val localDeviceId: String,
    val localDeviceNames: Set<String> = emptySet(),
    val localAddresses: Set<String> = emptySet()
) {

    /**
     * Vérifie si les identifiants ou adresses réseau correspondent à l'appareil local lui-même.
     */
    fun isSelf(senderDeviceId: String?, remoteAddress: String?, remoteName: String? = null): Boolean {
        // 1. Rejet par identifiant de terminal unique (deviceId)
        if (!senderDeviceId.isNullOrBlank() && senderDeviceId == localDeviceId) {
            return true
        }

        // 2. Rejet par adresse IP (Loopback, IP locale déclarée, multi-interfaces)
        if (!remoteAddress.isNullOrBlank()) {
            val cleanAddress = (if (remoteAddress.contains("%")) remoteAddress.substringBefore("%") else remoteAddress)
                .trim()
                .lowercase()

            if (cleanAddress == "127.0.0.1" ||
                cleanAddress == "::1" ||
                cleanAddress == "0.0.0.0" ||
                cleanAddress == "localhost" ||
                cleanAddress.startsWith("127.")
            ) {
                return true
            }

            val matchedLocalIp = localAddresses.any { localAddr ->
                val cleanLocal = (if (localAddr.contains("%")) localAddr.substringBefore("%") else localAddr)
                    .trim()
                    .lowercase()
                cleanLocal == cleanAddress
            }
            if (matchedLocalIp) {
                return true
            }
        }

        // 3. Rejet par nom d'appareil ou préfixe de service mDNS local auto-annoncé
        if (!remoteName.isNullOrBlank()) {
            val cleanName = remoteName.trim()
            if (cleanName.startsWith("LanceDrop-Android")) {
                return true
            }
            val matchedName = localDeviceNames.any { it.equals(cleanName, ignoreCase = true) }
            if (matchedName) {
                return true
            }
        }

        return false
    }

    /**
     * Vérifie si un DiscoveredPeer reçu est légitime et ne représente pas l'appareil local.
     */
    fun shouldAcceptPeer(peer: DiscoveredPeer): Boolean {
        return !isSelf(
            senderDeviceId = peer.id,
            remoteAddress = peer.ipAddress,
            remoteName = peer.name
        )
    }

    /**
     * Filtre une liste de pairs pour purger toute occurrence de l'appareil local.
     */
    fun filterPeers(peers: List<DiscoveredPeer>): List<DiscoveredPeer> {
        return peers.filter { shouldAcceptPeer(it) }
    }
}
