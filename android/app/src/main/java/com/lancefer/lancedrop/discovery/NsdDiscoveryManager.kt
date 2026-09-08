package com.lancefer.lancedrop.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NsdDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val TAG = "NsdDiscoveryManager"
        const val SERVICE_TYPE = "_lancedrop._tcp."
        const val SERVICE_NAME_PREFIX = "LanceDrop-Android"
    }

    fun startDiscovery() {
        acquireMulticastLock()
        stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Découverte mDNS démarrée : $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service trouvé : ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("lancedrop") || serviceInfo.serviceType.contains("fastdrop")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service perdu : ${serviceInfo.serviceName}")
                _peers.update { currentPeers ->
                    currentPeers.filterNot { it.name == serviceInfo.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Découverte mDNS arrêtée : $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Échec démarrage découverte : $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Échec arrêt découverte : $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lancement discoverServices", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Échec résolution service : $errorCode")
            }

            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service résolu : ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")
                val hostAddress = serviceInfo.host?.hostAddress ?: return

                val osType = when {
                    serviceInfo.serviceName.contains("Win", ignoreCase = true) -> OsType.WINDOWS
                    serviceInfo.serviceName.contains("Mac", ignoreCase = true) -> OsType.MACOS
                    serviceInfo.serviceName.contains("Linux", ignoreCase = true) -> OsType.LINUX
                    else -> OsType.WINDOWS
                }

                val peer = DiscoveredPeer(
                    id = serviceInfo.serviceName,
                    name = serviceInfo.serviceName,
                    ipAddress = hostAddress,
                    port = serviceInfo.port,
                    osType = osType,
                    isTrusted = false,
                )

                _peers.update { current ->
                    val filtered = current.filterNot { it.id == peer.id }
                    filtered + peer
                }
            }
        }

        try {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur resolveService", e)
        }
    }

    fun registerLocalService(port: Int = 42420) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX-${Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.RegistrationListener {
                    override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Échec enregistrement service : $errorCode")
                    }

                    override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Échec désenregistrement service : $errorCode")
                    }

                    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Service Android enregistré sous : ${serviceInfo.serviceName}")
                    }

                    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Service Android désenregistré : ${serviceInfo.serviceName}")
                    }
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur registerService", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur stopServiceDiscovery", e)
            }
            discoveryListener = null
        }
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("LanceDropNsdLock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}
