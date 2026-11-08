package com.lancefer.lancedrop.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import com.lancefer.lancedrop.model.DeviceInfo
import java.io.File
import java.util.Locale

data class MediaCategoryStats(
    val category: String,
    val count: Int,
    val totalBytes: Long,
    val formattedSize: String
)

object DeviceMetadataHelper {

    /**
     * Extrait l'ensemble des caractéristiques authentiques de l'appareil
     */
    fun getDeviceInfo(context: Context): DeviceInfo {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val deviceCodename = Build.DEVICE
        val board = Build.BOARD
        val hardware = Build.HARDWARE
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val buildNumber = Build.DISPLAY

        // Récupération du vrai nom d'appareil personnalisé défini par l'utilisateur
        val systemDeviceName = try {
            Settings.Global.getString(context.contentResolver, "device_name")
                ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        } catch (_: Exception) {
            null
        }

        val resolvedName = when {
            !systemDeviceName.isNullOrBlank() -> systemDeviceName
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }

        return DeviceInfo(
            deviceName = resolvedName,
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            deviceCodename = deviceCodename,
            board = board,
            hardware = hardware,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            buildNumber = buildNumber
        )
    }

    /**
     * Calcule précisément l'espace de stockage physique réel de l'appareil
     */
    fun getStorageMetrics(): StorageMetrics {
        val targetPath = try {
            val extDir = Environment.getExternalStorageDirectory()
            if (extDir != null && extDir.exists()) extDir.absolutePath else Environment.getDataDirectory().path
        } catch (_: Exception) {
            Environment.getDataDirectory().path
        }

        val stat = StatFs(targetPath)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val freeBytes = stat.availableBlocksLong * blockSize
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        val percent = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f

        fun formatBytes(bytes: Long): String {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.FRANCE, "%.1f Go", gb)
            } else {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                String.format(Locale.FRANCE, "%.0f Mo", mb)
            }
        }

        return StorageMetrics(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            usedPercentage = percent,
            totalFormatted = formatBytes(totalBytes),
            usedFormatted = formatBytes(usedBytes),
            freeFormatted = formatBytes(freeBytes)
        )
    }

    /**
     * Interroge le MediaStore pour obtenir les statistiques réelles des fichiers par catégorie
     */
    fun getMediaStats(context: Context): Map<String, MediaCategoryStats> {
        val results = mutableMapOf<String, MediaCategoryStats>()
        results["Images"] = queryMediaStats(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Images")
        results["Vidéos"] = queryMediaStats(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Vidéos")
        results["Audio"] = queryMediaStats(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Audio")
        results["Documents"] = queryDocumentsStats(context)
        return results
    }

    private fun queryMediaStats(context: Context, uri: android.net.Uri, categoryName: String): MediaCategoryStats {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            var count = 0
            var totalSize = 0L

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    count++
                    if (sizeCol != -1) {
                        totalSize += cursor.getLong(sizeCol)
                    }
                }
            }

            MediaCategoryStats(
                category = categoryName,
                count = count,
                totalBytes = totalSize,
                formattedSize = formatCategorySize(totalSize)
            )
        } catch (_: Exception) {
            MediaCategoryStats(categoryName, 0, 0L, "0 o")
        }
    }

    private fun queryDocumentsStats(context: Context): MediaCategoryStats {
        return try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            val selection = "${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf%", "text/%", "application/msword")

            var count = 0
            var totalSize = 0L

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    count++
                    if (sizeCol != -1) {
                        totalSize += cursor.getLong(sizeCol)
                    }
                }
            }

            MediaCategoryStats(
                category = "Documents",
                count = count,
                totalBytes = totalSize,
                formattedSize = formatCategorySize(totalSize)
            )
        } catch (_: Exception) {
            MediaCategoryStats("Documents", 0, 0L, "0 o")
        }
    }

    private fun formatCategorySize(bytes: Long): String {
        return FileHelper.formatFileSize(bytes)
    }
}

data class StorageMetrics(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val usedPercentage: Float,
    val totalFormatted: String,
    val usedFormatted: String,
    val freeFormatted: String
)
