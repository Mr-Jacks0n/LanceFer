package com.lancefer.lancedrop.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Représente un dossier racine partagé du PC distant.
 */
data class RemoteShare(
    val id: String,
    val name: String,
    val path: String = "",
    val itemCount: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("path", path)
            put("itemCount", itemCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): RemoteShare {
            return RemoteShare(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                path = json.optString("path", ""),
                itemCount = json.optInt("itemCount", 0)
            )
        }

        fun listFromJson(jsonArray: JSONArray): List<RemoteShare> {
            val list = mutableListOf<RemoteShare>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }

        fun listFromJsonString(jsonStr: String): List<RemoteShare> {
            return listFromJson(JSONArray(jsonStr))
        }
    }
}

/**
 * Élément du fil d'Ariane pour la navigation hiérarchique.
 */
data class BreadcrumbItem(
    val name: String,
    val subpath: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("subpath", subpath)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): BreadcrumbItem {
            return BreadcrumbItem(
                name = json.optString("name", ""),
                subpath = json.optString("subpath", "")
            )
        }

        fun listFromJson(jsonArray: JSONArray): List<BreadcrumbItem> {
            val list = mutableListOf<BreadcrumbItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }

        /**
         * Construit une liste de fil d'Ariane à partir d'un nom de partage et d'un sous-chemin relatif.
         */
        fun buildBreadcrumbs(shareName: String, subpath: String): List<BreadcrumbItem> {
            val clean = subpath.trim('/', '\\').replace('\\', '/')
            val rootName = shareName.ifBlank { "Root" }
            val list = mutableListOf(BreadcrumbItem(name = rootName, subpath = ""))
            if (clean.isEmpty()) return list

            val segments = clean.split('/').filter { it.isNotEmpty() }
            var accPath = ""
            for (segment in segments) {
                accPath = if (accPath.isEmpty()) segment else "$accPath/$segment"
                list.add(BreadcrumbItem(name = segment, subpath = accPath))
            }
            return list
        }
    }
}

/**
 * Informations de pagination pour les répertoires volumineux.
 */
data class PaginationInfo(
    val page: Int = 1,
    val limit: Int = 50,
    val total: Int = 0,
    val totalPages: Int = 1
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("page", page)
            put("limit", limit)
            put("total", total)
            put("totalPages", totalPages)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PaginationInfo {
            return PaginationInfo(
                page = json.optInt("page", 1),
                limit = json.optInt("limit", 50),
                total = json.optInt("total", 0),
                totalPages = json.optInt("totalPages", 1)
            )
        }
    }
}

/**
 * Fichier ou sous-dossier distant dans un répertoire partagé.
 */
data class RemoteFileItem(
    val name: String,
    val subpath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: String = "",
    val mtime: Long = 0L,
    val hasThumbnail: Boolean = false
) {
    val extension: String get() = if (name.contains('.')) name.substringAfterLast('.').lowercase(Locale.ROOT) else ""

    val isVideo: Boolean get() = mimeType.startsWith("video/") || extension in setOf(
        "mp4", "mkv", "mov", "webm", "avi", "3gp", "ts", "flv", "m4v", "wmv"
    )

    val isAudio: Boolean get() = mimeType.startsWith("audio/") || extension in setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma", "aiff"
    )

    val isImage: Boolean get() = mimeType.startsWith("image/") || extension in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg", "ico", "tiff"
    )

    val formattedSize: String get() {
        if (isDirectory) return "Dossier"
        if (size <= 0L) return "0 Ko"
        val kb = size.toDouble() / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f Go", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f Mo", mb)
            else -> String.format(Locale.US, "%.0f Ko", kb.coerceAtLeast(1.0))
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("subpath", subpath)
            put("isDirectory", isDirectory)
            put("size", size)
            put("mimeType", mimeType)
            put("mtime", mtime)
            put("hasThumbnail", hasThumbnail)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): RemoteFileItem {
            return RemoteFileItem(
                name = json.optString("name", ""),
                subpath = json.optString("subpath", ""),
                isDirectory = json.optBoolean("isDirectory", false),
                size = json.optLong("size", 0L),
                mimeType = json.optString("mimeType", ""),
                mtime = json.optLong("mtime", 0L),
                hasThumbnail = json.optBoolean("hasThumbnail", false)
            )
        }

        fun listFromJson(jsonArray: JSONArray): List<RemoteFileItem> {
            val list = mutableListOf<RemoteFileItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null) {
                    list.add(fromJson(obj))
                }
            }
            return list
        }
    }
}

/**
 * Contenu d'un répertoire distant avec fil d'Ariane, éléments et métadonnées de pagination.
 */
data class RemoteDirectoryListing(
    val shareId: String,
    val shareName: String,
    val currentPath: String,
    val breadcrumbs: List<BreadcrumbItem> = emptyList(),
    val items: List<RemoteFileItem> = emptyList(),
    val pagination: PaginationInfo = PaginationInfo()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("shareId", shareId)
            put("shareName", shareName)
            put("currentPath", currentPath)

            val bcArray = JSONArray()
            breadcrumbs.forEach { bcArray.put(it.toJson()) }
            put("breadcrumbs", bcArray)

            val itemsArray = JSONArray()
            items.forEach { itemsArray.put(it.toJson()) }
            put("items", itemsArray)

            put("pagination", pagination.toJson())
        }
    }

    companion object {
        fun fromJson(json: JSONObject): RemoteDirectoryListing {
            val shareId = json.optString("shareId", "")
            val shareName = json.optString("shareName", "")
            val currentPath = json.optString("currentPath", "")

            val bcArray = json.optJSONArray("breadcrumbs")
            val breadcrumbs = if (bcArray != null) {
                BreadcrumbItem.listFromJson(bcArray)
            } else {
                BreadcrumbItem.buildBreadcrumbs(shareName, currentPath)
            }

            val itemsArray = json.optJSONArray("items")
            val items = if (itemsArray != null) {
                RemoteFileItem.listFromJson(itemsArray)
            } else {
                emptyList()
            }

            val paginationObj = json.optJSONObject("pagination")
            val pagination = if (paginationObj != null) {
                PaginationInfo.fromJson(paginationObj)
            } else {
                PaginationInfo(total = items.size)
            }

            return RemoteDirectoryListing(
                shareId = shareId,
                shareName = shareName,
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                items = items,
                pagination = pagination
            )
        }

        fun fromJsonString(jsonStr: String): RemoteDirectoryListing {
            return fromJson(JSONObject(jsonStr))
        }
    }
}
