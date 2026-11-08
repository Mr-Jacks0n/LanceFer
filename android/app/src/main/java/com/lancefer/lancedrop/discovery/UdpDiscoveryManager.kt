package com.lancefer.lancedrop.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import com.lancefer.lancedrop.telemetry.TelemetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * LanceDrop Android — Moteur de Découverte UDP Broadcast (Port 42424)
 * Optimisé pour la performance et l'autonomie de la batterie :
 * - Ramasse-miettes TTL : élague automatiquement les pairs inactifs au bout de 12s
 * - Pause / Reprise : coupe le polling réseau lorsque l'application passe en arrière-plan
 * - Multi-Broadcast : émet sur le broadcast global et sur chaque sous-réseau physique actif
 */
class UdpDiscoveryManager(
    private val context: Context,
    private val port: Int = 42424
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listenJob: Job? = null
    private var beaconJob: Job? = null
    private var cleanupJob: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val peerLastSeenMap = ConcurrentHashMap<String, Long>()

    private val localDeviceId = "android-${Build.MODEL}-${(1000..9999).random()}"
    private val localDeviceName = "LanceDrop-${Build.MODEL}"

    companion object {
        private const val TAG = "UdpDiscoveryManager"
        private const val MAGIC = "FD"
    }

    fun start() {
        stop()
        acquireMulticastLock()

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(java.net.InetSocketAddress(port))
            }
            Log.d(TAG, "Socket UDP d'écoute ouvert sur port $port")
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de bind UDP sur $port : ${e.message}")
            return
        }

        // 1. Coroutine d'écoute des trames entrantes
        listenJob = scope.launch {
            val buffer = ByteArray(2048)
            while (isActive && socket?.isClosed == false) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handlePacket(text, packet.address.hostAddress ?: "")
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "Erreur réception UDP: ${e.message}")
                }
            }
        }

        // 2. Coroutine d'émission périodique du Beacon
        startBeaconJob()

        // 3. Coroutine de ramasse-miettes (TTL Garbage Collector)
        cleanupJob = scope.launch {
            while (isActive) {
                delay(4000)
                val now = System.currentTimeMillis()
                val expiredIds = peerLastSeenMap.filter { now - it.value > 12000 }.keys
                if (expiredIds.isNotEmpty()) {
                    expiredIds.forEach { peerLastSeenMap.remove(it) }
                    _peers.update { current -> current.filterNot { it.id in expiredIds } }
                    Log.d(TAG, "Nettoyage TTL pairs inactifs : $expiredIds")
                }
            }
        }
    }

    private fun startBeaconJob() {
        beaconJob?.cancel()
        beaconJob = scope.launch {
            while (isActive && socket?.isClosed == false) {
                sendBeacon()
                delay(2500)
            }
        }
    }

    /**
     * Suspend l'émission de beacons en arrière-plan pour préserver la batterie
     */
    fun pause() {
        beaconJob?.cancel()
        beaconJob = null
        releaseMulticastLock()
        Log.d(TAG, "Découverte UDP mise en pause (arrière-plan)")
    }

    /**
     * Reprend l'émission de beacons lors du retour au premier plan
     */
    fun resume() {
        if (socket != null && socket?.isClosed == false) {
            acquireMulticastLock()
            startBeaconJob()
            Log.d(TAG, "Découverte UDP reprise (premier-plan)")
        } else {
            start()
        }
    }

    fun stop() {
        listenJob?.cancel()
        beaconJob?.cancel()
        cleanupJob?.cancel()
        listenJob = null
        beaconJob = null
        cleanupJob = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        releaseMulticastLock()
        peerLastSeenMap.clear()
        _peers.value = emptyList()
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast
                    if (bcast != null && !list.contains(bcast)) {
                        list.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun sendBeacon() {
        try {
            val json = JSONObject().apply {
                put("magic", MAGIC)
                put("type", "BEACON")
                put("deviceId", localDeviceId)
                put("deviceName", localDeviceName)
                put("platform", "android")
                put("transferPort", 42420)
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
            }
            val data = json.toString().toByteArray(Charsets.UTF_8)
            val broadcastTargets = getBroadcastAddresses()

            for (targetAddr in broadcastTargets) {
                try {
                    val packet = DatagramPacket(data, data.size, targetAddr, port)
                    socket?.send(packet)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            // Ignorer erreurs temporaires d'envoi broadcast
        }
    }

    private fun handlePacket(jsonString: String, remoteIp: String) {
        try {
            val obj = JSONObject(jsonString)
            if (obj.optString("magic") != MAGIC && obj.optString("app") != "LanceDrop") return

            val senderId = obj.optString("deviceId")
            val senderName = obj.optString("deviceName", obj.optString("name", ""))

            // Rejet strict de l'auto-détection : deviceId, deviceName, ou IP locale
            if (senderId.isEmpty() || senderId == localDeviceId) return
            if (senderName.isNotEmpty() && (senderName == localDeviceName || senderName.startsWith("LanceDrop-Android"))) return
            if (com.lancefer.lancedrop.utils.NetworkUtils.isSelfIp(remoteIp)) return

            val type = obj.optString("type")
            if (type == "BEACON" || type == "PROBE") {
                val name = obj.optString("deviceName", obj.optString("name", "Appareil distant"))
                val platform = obj.optString("platform", "windows").lowercase()
                val transferPort = obj.optInt("transferPort", obj.optInt("port", 42420))

                val osType = when {
                    platform.contains("win") -> OsType.WINDOWS
                    platform.contains("mac") -> OsType.MACOS
                    platform.contains("lin") -> OsType.LINUX
                    else -> OsType.ANDROID
                }

                val peer = DiscoveredPeer(
                    id = senderId,
                    name = name,
                    ipAddress = remoteIp,
                    port = transferPort,
                    osType = osType,
                    isTrusted = false,
                    isOnline = true
                )

                peerLastSeenMap[peer.id] = System.currentTimeMillis()

                _peers.update { current ->
                    val filtered = current.filterNot { it.id == peer.id || it.ipAddress == peer.ipAddress }
                    filtered + peer
                }

                TelemetryManager.addBreadcrumb("discovery", "Peer detected", mapOf(
                    "id" to peer.id,
                    "name" to peer.name,
                    "ip" to remoteIp
                ))
            }
        } catch (_: Exception) {}
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("LanceDropUdpLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
