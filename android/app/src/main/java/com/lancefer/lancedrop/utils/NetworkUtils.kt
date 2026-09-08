package com.lancefer.lancedrop.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Récupérer l'adresse IPv4 locale réelle de l'appareil Android (LAN Wi-Fi / Hotspot)
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * Récupérer le nom (SSID) du réseau Wi-Fi connecté
     */
    fun getWifiSsid(context: Context): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "Hors-Ligne"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Hors-Ligne"

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val ssid = info.ssid?.replace("\"", "") ?: "Wi-Fi"
                return if (ssid != "<unknown ssid>" && ssid.isNotEmpty()) ssid else "Wi-Fi Connecté"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Réseau Local"
    }
}
