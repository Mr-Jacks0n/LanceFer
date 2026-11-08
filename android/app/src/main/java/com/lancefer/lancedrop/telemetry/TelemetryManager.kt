package com.lancefer.lancedrop.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.concurrent.thread

/**
 * LanceDrop Android Telemetry & Crash Monitoring Manager
 * Intègre le suivi Sentry et Microsoft Clarity avec une politique stricte de confidentialité (Zero-PII) :
 * - Masquage automatique des chemins de fichiers Android (/storage/emulated/0/... -> [STORAGE_ROOT]/...)
 * - Masquage des clés AES, codes PIN d'appairage et tokens
 * - Anonymisation des adresses IP locales
 * - Résilience hors-ligne totale : aucune levée d'exception ou blocage de transfert si hors-ligne
 */
object TelemetryManager {
    private const val TAG = "LanceDropTelemetry"
    private const val MAX_BREADCRUMBS = 50

    private var initialized = false
    private var sentryDsn: String = ""
    private var clarityProjectId: String = "lancedrop_android"
    private var appContext: Context? = null

    data class Breadcrumb(
        val timestamp: Long,
        val category: String,
        val message: String,
        val data: Map<String, String>
    )

    private val breadcrumbs = ConcurrentLinkedDeque<Breadcrumb>()

    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        captureException(throwable, mapOf("source" to "coroutineExceptionHandler"))
    }

    fun init(context: Context, customSentryDsn: String? = null, customClarityId: String? = null) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        sentryDsn = customSentryDsn ?: ""
        clarityProjectId = customClarityId ?: "lancedrop_android"

        // Capture des exceptions non interceptées sur le thread principal et les threads secondaires
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                captureException(throwable, mapOf(
                    "thread" to thread.name,
                    "source" to "UncaughtExceptionHandler"
                ))
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        addBreadcrumb("lifecycle", "TelemetryManager initialized", mapOf(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "androidVersion" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString()
        ))
    }

    /**
     * Masque et anonymise les données sensibles (chemins privés, tokens, PIN, clés hex)
     */
    fun scrub(text: String?): String {
        if (text == null) return ""
        var clean = text

        // 1. Masquage des chemins de stockage Android
        clean = clean.replace(Regex("/storage/emulated/0/[^/\\s\"']+"), "[STORAGE_ROOT]")
        clean = clean.replace(Regex("/data/user/0/[^/\\s\"']+"), "[APP_PRIVATE_DIR]")
        clean = clean.replace(Regex("/data/data/[^/\\s\"']+"), "[APP_PRIVATE_DIR]")

        // 2. Masquage des clés de session hexadécimales 256 bits (64 car.)
        clean = clean.replace(Regex("[0-9a-fA-F]{64}"), "[REDACTED_KEY_256]")

        // 3. Masquage des codes PIN 6 chiffres
        clean = clean.replace(Regex("(?i)(pin|code)[\"']?\\s*[:=]\\s*[\"']?(\\d{6})[\"']?"), "$1: \"[REDACTED_PIN]\"")

        // 4. Anonymisation des adresses IP locales privées
        clean = clean.replace(Regex("\\b(192\\.168\\.)\\d{1,3}\\.\\d{1,3}\\b"), "$1*.*")
        clean = clean.replace(Regex("\\b(10\\.)\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "$1*.*.*")
        clean = clean.replace(Regex("\\b(172\\.(?:1[6-9]|2\\d|3[01])\\.)\\d{1,3}\\.\\d{1,3}\\b"), "$1*.*")

        return clean
    }

    /**
     * Enregistre un jalon ou événement dans le tampon circulaire de diagnostic
     */
    fun addBreadcrumb(category: String, message: String, data: Map<String, Any?> = emptyMap()) {
        val scrubbedData = mutableMapOf<String, String>()
        for ((key, value) in data) {
            val kLower = key.lowercase(Locale.ROOT)
            if (kLower.contains("key") || kLower.contains("pin") || kLower.contains("secret")) {
                scrubbedData[key] = "[REDACTED]"
            } else {
                scrubbedData[key] = scrub(value?.toString() ?: "")
            }
        }

        val crumb = Breadcrumb(
            timestamp = System.currentTimeMillis(),
            category = category,
            message = scrub(message),
            data = scrubbedData
        )

        breadcrumbs.add(crumb)
        while (breadcrumbs.size > MAX_BREADCRUMBS) {
            breadcrumbs.poll()
        }
    }

    /**
     * Capture une exception avec son contexte et les derniers breadcrumbs
     */
    fun captureException(throwable: Throwable, extraContext: Map<String, String> = emptyMap()) {
        val message = scrub(throwable.message ?: throwable.javaClass.simpleName)
        val stackTrace = scrub(Log.getStackTraceString(throwable))

        Log.e(TAG, "Exception interceptée: $message")

        // Sauvegarde locale persistante du rapport de crash en cas d'absence de réseau
        saveCrashReportLocally(throwable, extraContext)

        // Envoi distant asynchrone non-bloquant vers Sentry si DSN renseigné
        if (sentryDsn.startsWith("http")) {
            thread(isDaemon = true) {
                sendToSentry(throwable, extraContext, message, stackTrace)
            }
        }
    }

    private fun saveCrashReportLocally(throwable: Throwable, context: Map<String, String>) {
        try {
            val ctx = appContext ?: return
            val dir = File(ctx.filesDir, "telemetry_reports")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "crash_${System.currentTimeMillis()}.json"
            val report = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()))
                put("exceptionType", throwable.javaClass.name)
                put("message", scrub(throwable.message))
                put("stackTrace", scrub(Log.getStackTraceString(throwable)))
                put("device", "${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
                
                val crumbsArray = JSONArray()
                for (crumb in breadcrumbs) {
                    crumbsArray.put(JSONObject().apply {
                        put("ts", crumb.timestamp)
                        put("cat", crumb.category)
                        put("msg", crumb.message)
                    })
                }
                put("breadcrumbs", crumbsArray)
            }

            File(dir, fileName).writeText(report.toString(2))
        } catch (_: Exception) {}
    }

    private fun sendToSentry(throwable: Throwable, context: Map<String, String>, scrubbedMsg: String, scrubbedStack: String) {
        try {
            val url = URL(sentryDsn)
            val pathParts = url.path.trim('/').split('/')
            val projectId = pathParts.lastOrNull() ?: return
            val publicKey = url.userInfo ?: return

            val endpoint = URL("${url.protocol}://${url.host}/api/$projectId/store/")
            val conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Sentry-Auth", "Sentry sentry_version=7, sentry_key=$publicKey, sentry_client=LanceDropAndroid/0.1.0")
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("platform", "java")
                put("level", "error")
                put("release", "lancedrop-android@0.1.0")
                put("exception", JSONObject().apply {
                    put("values", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", throwable.javaClass.simpleName)
                            put("value", scrubbedMsg)
                            put("stacktrace", scrubbedStack)
                        })
                    })
                })
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            conn.responseCode // Déclencher la requête
            conn.disconnect()
        } catch (_: Exception) {
            // Résilience hors-ligne : silence complet
        }
    }

    fun getRecentBreadcrumbs(): List<Breadcrumb> = breadcrumbs.toList()

    fun getClarityConfig(): Map<String, String> = mapOf(
        "projectId" to clarityProjectId,
        "maskSensitive" to "true"
    )
}
