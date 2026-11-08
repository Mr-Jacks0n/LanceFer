package com.lancefer.lancedrop.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class HotspotState {
    object Inactive : HotspotState()
    object Starting : HotspotState()
    data class Active(
        val ssid: String,
        val passphrase: String,
        val gatewayIp: String = "192.168.43.1"
    ) : HotspotState()
    data class Error(val message: String) : HotspotState()
}

/**
 * Gestionnaire du Point d'accès (Local-Only Hotspot) LanceDrop
 * Active automatiquement le SoftAP Wi-Fi du téléphone sans rediriger l'utilisateur vers les Paramètres.
 */
class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Inactive)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    companion object {
        private const val TAG = "HotspotManager"
        @Volatile
        private var INSTANCE: HotspotManager? = null

        fun getInstance(context: Context): HotspotManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HotspotManager(context).also { INSTANCE = it }
            }
        }
    }

    fun startHotspot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _state.value = HotspotState.Error("Le point d'accès automatique requiert Android 8.0+")
            return
        }

        if (wifiManager == null) {
            _state.value = HotspotState.Error("Wi-Fi non disponible sur cet appareil")
            return
        }

        if (reservation != null) {
            return
        }

        _state.value = HotspotState.Starting

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(res)
                    reservation = res

                    var ssid = "LanceDrop-Direct"
                    var password = ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        res.softApConfiguration?.let { config ->
                            ssid = config.ssid ?: ssid
                            password = config.passphrase ?: ""
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        res.wifiConfiguration?.let { config ->
                            ssid = config.SSID ?: ssid
                            password = config.preSharedKey ?: ""
                        }
                    }

                    Log.d(TAG, "Hotspot local activé: SSID=$ssid")
                    _state.value = HotspotState.Active(
                        ssid = ssid,
                        passphrase = password,
                        gatewayIp = "192.168.43.1"
                    )
                }

                override fun onStopped() {
                    super.onStopped()
                    reservation = null
                    _state.value = HotspotState.Inactive
                    Log.d(TAG, "Hotspot arrêté")
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    reservation = null
                    val msg = when (reason) {
                        ERROR_NO_CHANNEL -> "Aucun canal Wi-Fi disponible"
                        ERROR_GENERIC -> "Erreur système lors du démarrage du hotspot"
                        ERROR_INCOMPATIBLE_MODE -> "Mode Wi-Fi incompatible"
                        ERROR_TETHERING_DISALLOWED -> "Partage interdit par l'opérateur"
                        else -> "Échec démarrage hotspot (code $reason)"
                    }
                    _state.value = HotspotState.Error(msg)
                    Log.e(TAG, "Échec hotspot: $msg")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            _state.value = HotspotState.Error("Permission Localisation requise pour activer le point d'accès.")
        } catch (e: Exception) {
            _state.value = HotspotState.Error("Erreur: ${e.message}")
        }
    }

    fun stopHotspot() {
        try {
            reservation?.close()
            reservation = null
            _state.value = HotspotState.Inactive
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt hotspot", e)
        }
    }
}
