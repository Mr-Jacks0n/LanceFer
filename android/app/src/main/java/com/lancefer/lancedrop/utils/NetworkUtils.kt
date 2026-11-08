package com.lancefer.lancedrop.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    data class NetworkInterfaceInfo(
        val interfaceName: String,
        val ipAddress: String,
        val isWifi: Boolean,
        val isHotspot: Boolean,
        val isP2p: Boolean
    )

    /**
     * Récupère la liste de toutes les interfaces réseau IPv4 actives et valides (LAN, Wi-Fi, Hotspot)
     */
    fun getActiveLanInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue

                val name = iface.name.lowercase()
                // Ignorer les interfaces virtuelles, VPN et de tunneling
                if (name.startsWith("dummy") || name.startsWith("sit") || name.startsWith("tun") ||
                    name.startsWith("tap") || name.startsWith("vbox") || name.startsWith("ip6tnl") ||
                    name.contains("tailscale") || name.contains("zerotier") || name.contains("wg")) {
                    continue
                }
                // Ignorer rigoureusement TOUTES les interfaces cellulaires mobiles et traducteurs CLAT
                // (rmnet, v4-rmnet, ccmni, v4-ccmni, pdp, seth, clat, wwan, radio, sipc)
                if (name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") ||
                    name.contains("wwan") || name.contains("radio") || name.contains("clat") ||
                    name.startsWith("v4-") || name.contains("seth") || name.contains("sipc")) {
                    continue
                }

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        // Ignorer loopback, adresses invalides et APIPA (169.254.x.x)
                        if (host != null && !host.startsWith("127.") && !host.startsWith("169.254.") && host != "0.0.0.0") {
                            // Ignorer également les adresses Carrier-Grade NAT (CGNAT 100.64.0.0/10) utilisées par les opérateurs mobiles
                            if (host.startsWith("100.")) {
                                val secondOctet = host.substringAfter("100.").substringBefore(".").toIntOrNull() ?: 0
                                if (secondOctet in 64..127) continue
                            }

                            val isWifi = name.startsWith("wlan") && !name.contains("p2p")
                            val isHotspot = name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("rndis") || host.startsWith("192.168.43.")
                            val isP2p = name.contains("p2p")
                            result.add(NetworkInterfaceInfo(name, host, isWifi, isHotspot, isP2p))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * Récupérer l'adresse IPv4 locale prioritaire de l'appareil Android (Wi-Fi > Hotspot > P2P > Fallback)
     */
    fun getLocalIpAddress(): String {
        val activeInterfaces = getActiveLanInterfaces()

        // 1. Priorité Wi-Fi actif (ex: wlan0)
        val wifiIface = activeInterfaces.firstOrNull { it.isWifi && !it.isHotspot }
        if (wifiIface != null) return wifiIface.ipAddress

        // 2. Priorité Point d'accès / Hotspot mobile (ex: ap0, 192.168.43.1)
        val hotspotIface = activeInterfaces.firstOrNull { it.isHotspot }
        if (hotspotIface != null) return hotspotIface.ipAddress

        // 3. Priorité Wi-Fi Direct (p2p)
        val p2pIface = activeInterfaces.firstOrNull { it.isP2p }
        if (p2pIface != null) return p2pIface.ipAddress

        // 4. Premier LAN actif non cellulaire
        val firstLan = activeInterfaces.firstOrNull()
        if (firstLan != null) return firstLan.ipAddress

        // 5. Fallback ultime si aucune interface LAN n'est détectée
        return "127.0.0.1"
    }

    /**
     * Récupérer le nom (SSID) du réseau Wi-Fi connecté ou du point d'accès actif
     */
    fun getWifiSsid(context: Context): String {
        try {
            // Vérification si le hotspot LanceDrop ou le SoftAP est actif
            val hotspotState = HotspotManager.getInstance(context).state.value
            if (hotspotState is HotspotState.Active) {
                return "Point d'accès : ${hotspotState.ssid}"
            }

            val activeLan = getActiveLanInterfaces()
            val hotspotIface = activeLan.firstOrNull { it.isHotspot }
            if (hotspotIface != null) {
                return "Point d'accès mobile (${hotspotIface.ipAddress})"
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            if (network != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wifiManager.connectionInfo
                    val ssid = info.ssid?.replace("\"", "") ?: "Wi-Fi"
                    return if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) ssid else "Wi-Fi Connecté"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Réseau Local Déconnecté"
    }

    /**
     * Récupère l'ensemble exhaustif de toutes les adresses IP locales de l'appareil
     * sur l'intégralité des interfaces réseau actives (Wi-Fi, Ethernet, Hotspot, VPN, Loopback, P2P).
     */
    fun getAllLocalIpAddresses(): Set<String> {
        val addresses = mutableSetOf("127.0.0.1", "::1", "0.0.0.0", "localhost")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return addresses
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val inetAddrs = iface.inetAddresses
                while (inetAddrs.hasMoreElements()) {
                    val addr = inetAddrs.nextElement()
                    val host = addr.hostAddress ?: continue
                    val cleanHost = if (host.contains("%")) host.substringBefore("%") else host
                    addresses.add(cleanHost.trim().lowercase())
                    if (addr is Inet4Address) {
                        addr.hostAddress?.trim()?.let { if (it.isNotEmpty()) addresses.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        addresses.remove("")
        return addresses
    }

    /**
     * Vérifie si une adresse IP correspond à l'appareil local.
     */
    fun isSelfIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return true
        val cleanIp = (if (ip.contains("%")) ip.substringBefore("%") else ip).trim().lowercase()
        if (cleanIp == "127.0.0.1" || cleanIp == "0.0.0.0" || cleanIp == "::1" || cleanIp == "localhost" || cleanIp.startsWith("127.")) {
            return true
        }
        return getAllLocalIpAddresses().contains(cleanIp)
    }
}
