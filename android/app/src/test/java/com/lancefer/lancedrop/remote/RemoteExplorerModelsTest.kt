package com.lancefer.lancedrop.remote

import com.lancefer.lancedrop.data.remote.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteExplorerModelsTest {

    @Test
    fun testRemoteShareJsonSerializationAndDeserialization() {
        val json = JSONObject().apply {
            put("id", "share-101")
            put("name", "Films & Séries")
            put("path", "D:\\Media\\Movies")
            put("itemCount", 42)
        }

        val share = RemoteShare.fromJson(json)
        assertEquals("share-101", share.id)
        assertEquals("Films & Séries", share.name)
        assertEquals("D:\\Media\\Movies", share.path)
        assertEquals(42, share.itemCount)

        val serialized = share.toJson()
        assertEquals("share-101", serialized.getString("id"))
        assertEquals("Films & Séries", serialized.getString("name"))
        assertEquals("D:\\Media\\Movies", serialized.getString("path"))
        assertEquals(42, serialized.getInt("itemCount"))
    }

    @Test
    fun testRemoteShareListFromJsonArray() {
        val jsonArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "s1")
                put("name", "Musique")
                put("itemCount", 120)
            })
            put(JSONObject().apply {
                put("id", "s2")
                put("name", "Documents")
                put("itemCount", 15)
            })
        }

        val list = RemoteShare.listFromJson(jsonArray)
        assertEquals(2, list.size)
        assertEquals("s1", list[0].id)
        assertEquals("Musique", list[0].name)
        assertEquals(120, list[0].itemCount)
        assertEquals("s2", list[1].id)
        assertEquals("Documents", list[1].name)
    }

    @Test
    fun testBreadcrumbItemBuildBreadcrumbs() {
        // Racine seule
        val rootBc = BreadcrumbItem.buildBreadcrumbs("Vidéos", "")
        assertEquals(1, rootBc.size)
        assertEquals("Vidéos", rootBc[0].name)
        assertEquals("", rootBc[0].subpath)

        // Dossier niveau 1
        val level1 = BreadcrumbItem.buildBreadcrumbs("Vidéos", "Films")
        assertEquals(2, level1.size)
        assertEquals("Vidéos", level1[0].name)
        assertEquals("", level1[0].subpath)
        assertEquals("Films", level1[1].name)
        assertEquals("Films", level1[1].subpath)

        // Sous-dossiers imbriqués avec séparateurs variés
        val nested = BreadcrumbItem.buildBreadcrumbs("Partage", "Cinema/2026/Action/")
        assertEquals(4, nested.size)
        assertEquals("Partage", nested[0].name)
        assertEquals("", nested[0].subpath)
        assertEquals("Cinema", nested[1].name)
        assertEquals("Cinema", nested[1].subpath)
        assertEquals("2026", nested[2].name)
        assertEquals("Cinema/2026", nested[2].subpath)
        assertEquals("Action", nested[3].name)
        assertEquals("Cinema/2026/Action", nested[3].subpath)
    }

    @Test
    fun testRemoteFileItemMimeTypeClassification() {
        // Vidéos
        val videoMp4 = RemoteFileItem(name = "movie.mp4", subpath = "movie.mp4", isDirectory = false, mimeType = "video/mp4")
        assertTrue(videoMp4.isVideo)
        assertFalse(videoMp4.isAudio)
        assertFalse(videoMp4.isImage)

        val videoMkv = RemoteFileItem(name = "clip.mkv", subpath = "clip.mkv", isDirectory = false)
        assertTrue(videoMkv.isVideo)

        // Audios
        val audioMp3 = RemoteFileItem(name = "song.mp3", subpath = "song.mp3", isDirectory = false, mimeType = "audio/mpeg")
        assertTrue(audioMp3.isAudio)
        assertFalse(audioMp3.isVideo)
        assertFalse(audioMp3.isImage)

        val audioFlac = RemoteFileItem(name = "track.flac", subpath = "track.flac", isDirectory = false)
        assertTrue(audioFlac.isAudio)

        // Images
        val imageJpg = RemoteFileItem(name = "photo.jpg", subpath = "photo.jpg", isDirectory = false, mimeType = "image/jpeg")
        assertTrue(imageJpg.isImage)
        assertFalse(imageJpg.isVideo)
        assertFalse(imageJpg.isAudio)

        val imagePng = RemoteFileItem(name = "screenshot.png", subpath = "screenshot.png", isDirectory = false)
        assertTrue(imagePng.isImage)

        // Fichier générique
        val docPdf = RemoteFileItem(name = "rapport.pdf", subpath = "rapport.pdf", isDirectory = false, mimeType = "application/pdf")
        assertFalse(docPdf.isVideo)
        assertFalse(docPdf.isAudio)
        assertFalse(docPdf.isImage)

        // Dossier
        val folder = RemoteFileItem(name = "Documents", subpath = "Documents", isDirectory = true)
        assertFalse(folder.isVideo)
        assertFalse(folder.isAudio)
        assertFalse(folder.isImage)
    }

    @Test
    fun testRemoteFileItemFormattedSize() {
        val folder = RemoteFileItem(name = "Dossier", subpath = "d", isDirectory = true, size = 0L)
        assertEquals("Dossier", folder.formattedSize)

        val emptyFile = RemoteFileItem(name = "empty.txt", subpath = "e.txt", isDirectory = false, size = 0L)
        assertEquals("0 Ko", emptyFile.formattedSize)

        val smallFile = RemoteFileItem(name = "small.txt", subpath = "s.txt", isDirectory = false, size = 512L)
        assertEquals("1 Ko", smallFile.formattedSize)

        val mbFile = RemoteFileItem(name = "music.mp3", subpath = "m.mp3", isDirectory = false, size = 15 * 1024 * 1024L)
        assertEquals("15.0 Mo", mbFile.formattedSize)

        val gbFile = RemoteFileItem(name = "video.mp4", subpath = "v.mp4", isDirectory = false, size = (2.5 * 1024 * 1024 * 1024).toLong())
        assertEquals("2.50 Go", gbFile.formattedSize)
    }

    @Test
    fun testRemoteDirectoryListingFullJsonRoundTrip() {
        val jsonString = """
        {
            "shareId": "share-movies",
            "shareName": "Films",
            "currentPath": "2026",
            "breadcrumbs": [
                { "name": "Films", "subpath": "" },
                { "name": "2026", "subpath": "2026" }
            ],
            "items": [
                {
                    "name": "teaser.mp4",
                    "subpath": "2026/teaser.mp4",
                    "isDirectory": false,
                    "size": 52428800,
                    "mimeType": "video/mp4",
                    "mtime": 1757482800000,
                    "hasThumbnail": true
                },
                {
                    "name": "Bonus",
                    "subpath": "2026/Bonus",
                    "isDirectory": true,
                    "size": 0,
                    "mimeType": "",
                    "mtime": 1757482900000,
                    "hasThumbnail": false
                }
            ],
            "pagination": {
                "page": 1,
                "limit": 50,
                "total": 2,
                "totalPages": 1
            }
        }
        """.trimIndent()

        val listing = RemoteDirectoryListing.fromJsonString(jsonString)
        assertEquals("share-movies", listing.shareId)
        assertEquals("Films", listing.shareName)
        assertEquals("2026", listing.currentPath)

        assertEquals(2, listing.breadcrumbs.size)
        assertEquals("Films", listing.breadcrumbs[0].name)
        assertEquals("", listing.breadcrumbs[0].subpath)
        assertEquals("2026", listing.breadcrumbs[1].name)
        assertEquals("2026", listing.breadcrumbs[1].subpath)

        assertEquals(2, listing.items.size)
        val fileItem = listing.items[0]
        assertEquals("teaser.mp4", fileItem.name)
        assertEquals("2026/teaser.mp4", fileItem.subpath)
        assertFalse(fileItem.isDirectory)
        assertEquals(52428800L, fileItem.size)
        assertEquals("video/mp4", fileItem.mimeType)
        assertTrue(fileItem.hasThumbnail)
        assertTrue(fileItem.isVideo)

        val folderItem = listing.items[1]
        assertEquals("Bonus", folderItem.name)
        assertTrue(folderItem.isDirectory)

        assertEquals(1, listing.pagination.page)
        assertEquals(50, listing.pagination.limit)
        assertEquals(2, listing.pagination.total)
        assertEquals(1, listing.pagination.totalPages)

        // Vérification de la re-sérialisation
        val serialized = listing.toJson()
        assertEquals("share-movies", serialized.getString("shareId"))
        assertEquals(2, serialized.getJSONArray("items").length())
    }

    @Test
    fun testMissingJsonFieldsFallback() {
        val sparseJson = JSONObject().apply {
            put("shareId", "share-sparse")
            put("shareName", "Sparse")
            put("currentPath", "folder/sub")
            // breadcrumbs, items, pagination manquants
        }

        val listing = RemoteDirectoryListing.fromJson(sparseJson)
        assertEquals("share-sparse", listing.shareId)
        assertEquals("Sparse", listing.shareName)
        assertEquals("folder/sub", listing.currentPath)

        // Breadcrumbs synthétisés automatiquement
        assertEquals(3, listing.breadcrumbs.size)
        assertEquals("Sparse", listing.breadcrumbs[0].name)
        assertEquals("folder", listing.breadcrumbs[1].name)
        assertEquals("sub", listing.breadcrumbs[2].name)

        // Items vides par défaut
        assertTrue(listing.items.isEmpty())

        // Pagination par défaut
        assertEquals(1, listing.pagination.page)
        assertEquals(50, listing.pagination.limit)
        assertEquals(0, listing.pagination.total)
    }

    @Test
    fun testSpecialCharactersInFilenames() {
        val specialName = "Vidéo spéciale & cool #1 [2026] + test.mkv"
        val item = RemoteFileItem(
            name = specialName,
            subpath = "dossier/$specialName",
            isDirectory = false,
            size = 1048576L,
            mimeType = "video/x-matroska"
        )
        assertEquals("mkv", item.extension)
        assertTrue(item.isVideo)
        assertEquals("1.0 Mo", item.formattedSize)

        val json = item.toJson()
        val deserialized = RemoteFileItem.fromJson(json)
        assertEquals(specialName, deserialized.name)
        assertEquals("dossier/$specialName", deserialized.subpath)
        assertTrue(deserialized.isVideo)
    }
}
