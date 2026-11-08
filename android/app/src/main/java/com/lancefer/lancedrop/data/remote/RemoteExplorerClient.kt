package com.lancefer.lancedrop.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Exception spécifique pour les erreurs de l'explorateur distant HTTP.
 */
class RemoteExplorerException(
    val statusCode: Int,
    message: String,
    val errorBody: String = ""
) : Exception(message)

/**
 * Client HTTP pour la communication avec le serveur de streaming et d'exploration LanceDrop Desktop.
 */
class RemoteExplorerClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Encode une chaîne pour l'inclusion dans une URL (RFC 3986).
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    /**
     * Construit l'URL complète de streaming multimédia avec paramètre de jeton optionnel.
     */
    fun buildStreamUrl(host: String, port: Int, shareId: String, path: String, token: String = ""): String {
        val cleanHost = host.trim()
        val encShareId = urlEncode(shareId)
        val encPath = urlEncode(path)
        val baseUrl = "http://$cleanHost:$port/api/shares/$encShareId/stream?path=$encPath"
        return if (token.isNotBlank()) {
            val encToken = urlEncode(token)
            "$baseUrl&token=$encToken"
        } else {
            baseUrl
        }
    }

    /**
     * Construit l'URL de récupération de la vignette d'un fichier image ou vidéo.
     */
    fun buildThumbnailUrl(host: String, port: Int, shareId: String, path: String, token: String = "", size: Int = 256): String {
        val cleanHost = host.trim()
        val encShareId = urlEncode(shareId)
        val encPath = urlEncode(path)
        val baseUrl = "http://$cleanHost:$port/api/shares/$encShareId/thumbnail?path=$encPath&size=$size"
        return if (token.isNotBlank()) {
            val encToken = urlEncode(token)
            "$baseUrl&token=$encToken"
        } else {
            baseUrl
        }
    }

    /**
     * Ajoute les en-têtes d'authentification standard LanceDrop à une requête OkHttp.
     */
    private fun applyAuthHeaders(builder: Request.Builder, token: String): Request.Builder {
        if (token.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
            builder.addHeader("X-Session-Token", token)
            builder.addHeader("X-Session-PIN", token)
        }
        return builder
    }

    /**
     * Vérifie la connectivité et l'état de santé du serveur distant.
     */
    suspend fun checkHealth(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val url = "http://${host.trim()}:$port/api/health"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Récupère la liste des dossiers partagés autorisés sur le PC.
     */
    suspend fun fetchShares(host: String, port: Int, token: String = ""): List<RemoteShare> = withContext(Dispatchers.IO) {
        fetchSharesSync(host, port, token)
    }

    fun fetchSharesSync(host: String, port: Int, token: String = ""): List<RemoteShare> {
        val url = "http://${host.trim()}:$port/api/shares"
        val request = applyAuthHeaders(Request.Builder().url(url).get(), token).build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(bodyString, response.code, response.message)
                throw RemoteExplorerException(response.code, errorMsg, bodyString)
            }
            return RemoteShare.listFromJson(JSONArray(bodyString))
        }
    }

    /**
     * Explore le contenu d'un dossier partagé avec pagination et tri.
     */
    suspend fun browseDirectory(
        host: String,
        port: Int,
        shareId: String,
        subpath: String = "",
        page: Int = 1,
        limit: Int = 50,
        sort: String = "name",
        token: String = ""
    ): RemoteDirectoryListing = withContext(Dispatchers.IO) {
        browseDirectorySync(host, port, shareId, subpath, page, limit, sort, token)
    }

    fun browseDirectorySync(
        host: String,
        port: Int,
        shareId: String,
        subpath: String = "",
        page: Int = 1,
        limit: Int = 50,
        sort: String = "name",
        token: String = ""
    ): RemoteDirectoryListing {
        val cleanHost = host.trim()
        val encShareId = urlEncode(shareId)
        val encSubpath = urlEncode(subpath)
        val encSort = urlEncode(sort)
        val url = "http://$cleanHost:$port/api/shares/$encShareId/browse?subpath=$encSubpath&page=$page&limit=$limit&sort=$encSort"

        val request = applyAuthHeaders(Request.Builder().url(url).get(), token).build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(bodyString, response.code, response.message)
                throw RemoteExplorerException(response.code, errorMsg, bodyString)
            }
            return RemoteDirectoryListing.fromJsonString(bodyString)
        }
    }

    /**
     * Télécharge un fichier complet en flux continu (stream) vers le flux de destination.
     * @return Nombre total d'octets écrits.
     */
    suspend fun downloadFile(
        streamUrl: String,
        token: String = "",
        outputStream: OutputStream,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val request = applyAuthHeaders(Request.Builder().url(streamUrl).get(), token).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val bodyString = response.body?.string().orEmpty()
                val errorMsg = parseErrorMessage(bodyString, response.code, response.message)
                throw RemoteExplorerException(response.code, errorMsg, bodyString)
            }

            val body = response.body ?: throw RemoteExplorerException(500, "Corps de réponse vide")
            val totalBytes = body.contentLength()
            var bytesCopied = 0L
            val buffer = ByteArray(64 * 1024)

            body.byteStream().use { input ->
                var read = input.read(buffer)
                while (read != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesCopied += read
                    onProgress?.invoke(bytesCopied, totalBytes)
                    read = input.read(buffer)
                }
                outputStream.flush()
            }
            bytesCopied
        }
    }

    private fun parseErrorMessage(bodyString: String, statusCode: Int, defaultMessage: String): String {
        return try {
            val json = JSONObject(bodyString)
            json.optString("error", json.optString("message", "HTTP $statusCode: $defaultMessage"))
        } catch (_: Exception) {
            if (bodyString.isNotBlank()) bodyString else "HTTP $statusCode: $defaultMessage"
        }
    }
}
