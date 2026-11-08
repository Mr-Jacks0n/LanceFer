package com.lancefer.lancedrop.remote

import com.lancefer.lancedrop.data.remote.RemoteExplorerClient
import com.lancefer.lancedrop.data.remote.RemoteExplorerException
import com.lancefer.lancedrop.ui.remote.formatDurationMs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Modèle pour tester les transitions de la machine à états de lecture vidéo/audio.
 */
enum class PlayerPlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
    ERROR
}

class PlaybackStateMachine {
    var state: PlayerPlaybackState = PlayerPlaybackState.IDLE
        private set
    var currentPositionMs: Long = 0L
        private set
    var durationMs: Long = 0L
        private set
    var errorMessage: String? = null
        private set

    fun prepare(duration: Long) {
        state = PlayerPlaybackState.BUFFERING
        durationMs = duration
    }

    fun onReady() {
        state = PlayerPlaybackState.READY
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs.coerceIn(0L, durationMs)
        state = PlayerPlaybackState.BUFFERING
    }

    fun onEnded() {
        currentPositionMs = durationMs
        state = PlayerPlaybackState.ENDED
    }

    fun onError(message: String) {
        state = PlayerPlaybackState.ERROR
        errorMessage = message
    }
}

/**
 * Helper de génération d'en-tête RFC 7233 Range.
 */
object HttpRangeHelper {
    fun buildRangeHeader(startByte: Long, endByte: Long? = null): String {
        require(startByte >= 0) { "Start byte must be non-negative" }
        return if (endByte != null) {
            require(endByte >= startByte) { "End byte must be greater than or equal to start byte" }
            "bytes=$startByte-$endByte"
        } else {
            "bytes=$startByte-"
        }
    }

    fun buildSuffixRangeHeader(suffixLength: Long): String {
        require(suffixLength > 0) { "Suffix length must be positive" }
        return "bytes=-$suffixLength"
    }

    fun parseContentRange(contentRangeHeader: String): Triple<Long, Long, Long>? {
        // Format attendu: "bytes 0-1048575/52428800"
        val regex = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""")
        val match = regex.find(contentRangeHeader.trim()) ?: return null
        val (start, end, total) = match.destructured
        return Triple(start.toLong(), end.toLong(), total.toLong())
    }
}

class StreamingPlayerInterfaceTest {

    private val client = RemoteExplorerClient()

    @Test
    fun testBuildStreamUrlEncodingAndToken() {
        val urlWithoutToken = client.buildStreamUrl(
            host = "192.168.1.50",
            port = 42421,
            shareId = "share 1",
            path = "Vidéos Vacances/clip 1.mp4"
        )
        assertEquals(
            "http://192.168.1.50:42421/api/shares/share%201/stream?path=Vid%C3%A9os%20Vacances%2Fclip%201.mp4",
            urlWithoutToken
        )

        val urlWithToken = client.buildStreamUrl(
            host = "192.168.1.50",
            port = 42421,
            shareId = "share-1",
            path = "movie.mp4",
            token = "secret_tok_123"
        )
        assertEquals(
            "http://192.168.1.50:42421/api/shares/share-1/stream?path=movie.mp4&token=secret_tok_123",
            urlWithToken
        )
    }

    @Test
    fun testBuildThumbnailUrl() {
        val thumbUrl = client.buildThumbnailUrl(
            host = "10.0.0.2",
            port = 42421,
            shareId = "photos",
            path = "été/plage.jpg",
            token = "pin42",
            size = 512
        )
        assertTrue(thumbUrl.startsWith("http://10.0.0.2:42421/api/shares/photos/thumbnail"))
        assertTrue(thumbUrl.contains("size=512"))
        assertTrue(thumbUrl.contains("token=pin42"))
        assertTrue(thumbUrl.contains("path=%C3%A9t%C3%A9%2Fplage.jpg"))
    }

    @Test
    fun testRangeHeaderGenerationRfc7233() {
        // Premier mégaoctet exact (0 à 1048575 = 1048576 octets)
        val initialRange = HttpRangeHelper.buildRangeHeader(0L, 1048575L)
        assertEquals("bytes=0-1048575", initialRange)

        // Saut temporel (seeking) à 256 Mo dans un fichier volumineux
        val seekRange = HttpRangeHelper.buildRangeHeader(268435456L, 269484031L)
        assertEquals("bytes=268435456-269484031", seekRange)

        // Lecture ouverte jusqu'à la fin du flux
        val openEndRange = HttpRangeHelper.buildRangeHeader(500000000L)
        assertEquals("bytes=500000000-", openEndRange)

        // Suffix range (derniers 1024 octets pour lire les métadonnées MP4 moov atom)
        val suffixRange = HttpRangeHelper.buildSuffixRangeHeader(1024L)
        assertEquals("bytes=-1024", suffixRange)
    }

    @Test
    fun testParseContentRangeHeader() {
        val parsed = HttpRangeHelper.parseContentRange("bytes 0-1048575/52428800")
        assertNotNull(parsed)
        assertEquals(0L, parsed!!.first)
        assertEquals(1048575L, parsed.second)
        assertEquals(52428800L, parsed.third)

        val invalid = HttpRangeHelper.parseContentRange("bytes */52428800")
        assertNull(invalid)
    }

    @Test
    fun testPlaybackStateMachineTransitions() {
        val stateMachine = PlaybackStateMachine()
        assertEquals(PlayerPlaybackState.IDLE, stateMachine.state)

        // Préparation d'une vidéo de 60 secondes (60 000 ms)
        stateMachine.prepare(60000L)
        assertEquals(PlayerPlaybackState.BUFFERING, stateMachine.state)
        assertEquals(60000L, stateMachine.durationMs)

        // Fin de mise en tampon -> prêt
        stateMachine.onReady()
        assertEquals(PlayerPlaybackState.READY, stateMachine.state)

        // Saut temporel (seeking) à 30 secondes
        stateMachine.seekTo(30000L)
        assertEquals(PlayerPlaybackState.BUFFERING, stateMachine.state)
        assertEquals(30000L, stateMachine.currentPositionMs)

        // Reprise de lecture
        stateMachine.onReady()
        assertEquals(PlayerPlaybackState.READY, stateMachine.state)

        // Fin de la vidéo
        stateMachine.onEnded()
        assertEquals(PlayerPlaybackState.ENDED, stateMachine.state)
        assertEquals(60000L, stateMachine.currentPositionMs)

        // Simulation d'erreur réseau
        stateMachine.onError("HTTP 403 Forbidden: Access denied")
        assertEquals(PlayerPlaybackState.ERROR, stateMachine.state)
        assertEquals("HTTP 403 Forbidden: Access denied", stateMachine.errorMessage)
    }

    @Test
    fun testFormatDurationMs() {
        assertEquals("00:00", formatDurationMs(0L))
        assertEquals("00:05", formatDurationMs(5000L))
        assertEquals("01:23", formatDurationMs(83000L))
        assertEquals("12:34", formatDurationMs(754000L))
        assertEquals("01:05:20", formatDurationMs(3920000L))
    }

    @Test
    fun testErrorResponseHandlingAndExceptions() {
        // 401 Unauthorized
        val err401Body = JSONObject().apply {
            put("error", "Unauthorized")
            put("message", "Missing or invalid session credentials")
        }.toString()

        val ex401 = RemoteExplorerException(401, "Unauthorized", err401Body)
        assertEquals(401, ex401.statusCode)
        assertEquals("Unauthorized", ex401.message)
        assertTrue(ex401.errorBody.contains("Missing or invalid"))

        // 403 Forbidden (Attaque Path Traversal ou accès non autorisé)
        val err403Body = JSONObject().apply {
            put("error", "Access denied")
            put("message", "Path outside authorized share boundary")
        }.toString()

        val ex403 = RemoteExplorerException(403, "Access denied", err403Body)
        assertEquals(403, ex403.statusCode)
        assertEquals("Access denied", ex403.message)

        // 404 Not Found
        val ex404 = RemoteExplorerException(404, "Share or directory not found")
        assertEquals(404, ex404.statusCode)

        // 416 Range Not Satisfiable
        val ex416 = RemoteExplorerException(416, "Range Not Satisfiable")
        assertEquals(416, ex416.statusCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeStartByteThrows() {
        HttpRangeHelper.buildRangeHeader(-1L, 100L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEndByteLessThanStartByteThrows() {
        HttpRangeHelper.buildRangeHeader(100L, 50L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeSuffixThrows() {
        HttpRangeHelper.buildSuffixRangeHeader(-10L)
    }
}
