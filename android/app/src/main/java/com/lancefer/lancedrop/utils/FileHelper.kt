package com.lancefer.lancedrop.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long
)

object FileHelper {

    fun getFileInfoFromUri(context: Context, uri: Uri): SelectedFile? {
        var name = "Fichier"
        var size = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "Fichier"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }

        return SelectedFile(uri, name, size)
    }

    fun getFileName(context: Context, uri: Uri): String {
        return getFileInfoFromUri(context, uri)?.name ?: "Fichier"
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        return getFileInfoFromUri(context, uri)?.size ?: 0L
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 o"
        return when {
            bytes < 1024L -> "$bytes o"
            bytes < 1024L * 1024L -> String.format(java.util.Locale.FRANCE, "%.1f Ko", bytes.toDouble() / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.FRANCE, "%.1f Mo", bytes.toDouble() / (1024.0 * 1024.0))
            else -> String.format(java.util.Locale.FRANCE, "%.2f Go", bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun openFile(context: Context, uri: Uri?, fallbackPath: String? = null, explicitFileName: String? = null) {
        try {
            var targetUri: Uri? = uri
            var fileName: String = explicitFileName ?: ""

            if (fileName.isEmpty() && targetUri != null) {
                fileName = getFileName(context, targetUri)
            }

            if (targetUri != null && targetUri.scheme == "file") {
                val file = java.io.File(targetUri.path ?: "")
                if (file.exists()) {
                    if (fileName.isEmpty()) fileName = file.name
                    targetUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            } else if (!fallbackPath.isNullOrEmpty()) {
                val file = java.io.File(fallbackPath)
                if (file.exists()) {
                    if (fileName.isEmpty()) fileName = file.name
                    targetUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }

            // Fallback physique : vérifier si le fichier existe dans Téléchargements/LanceDrop
            if (targetUri == null && fileName.isNotEmpty()) {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val directFile = java.io.File(downloadsDir, "LanceDrop/$fileName")
                if (directFile.exists()) {
                    targetUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        directFile
                    )
                }
            }

            if (targetUri == null) {
                Toast.makeText(context, "Fichier introuvable sur l'appareil", Toast.LENGTH_SHORT).show()
                return
            }

            val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
            var mime = try { context.contentResolver.getType(targetUri) } catch (_: Exception) { null }

            if (mime.isNullOrBlank() || mime == "application/octet-stream" || mime == "*/*") {
                val mapMime = if (ext.isNotEmpty()) {
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                } else null

                mime = when {
                    !mapMime.isNullOrBlank() -> mapMime
                    ext == "pdf" -> "application/pdf"
                    ext == "apk" -> "application/vnd.android.package-archive"
                    ext in listOf("doc", "docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    ext in listOf("xls", "xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ext in listOf("ppt", "pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    ext in listOf("txt", "log", "json", "md", "csv", "xml", "js", "html", "css", "ts", "kt", "py") -> "text/plain"
                    ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp") -> "image/*"
                    ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp") -> "video/*"
                    ext in listOf("mp3", "wav", "aac", "m4a", "flac", "ogg") -> "audio/*"
                    ext in listOf("zip", "rar", "7z", "tar", "gz") -> "application/zip"
                    else -> "*/*"
                }
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, mime)
                clipData = android.content.ClipData.newRawUri(fileName, targetUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Accorder expressément les droits de lecture à toutes les applications susceptibles de traiter l'intention
            val resInfoList = context.packageManager.queryIntentActivities(viewIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(pkgName, targetUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            val chooser = Intent.createChooser(viewIntent, "Ouvrir $fileName avec...").apply {
                clipData = android.content.ClipData.newRawUri(fileName, targetUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Aucune application trouvée pour ouvrir ce fichier", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, uri: Uri?, fallbackPath: String? = null) {
        try {
            var targetUri: Uri? = uri
            var fileName: String = ""

            if (targetUri != null) {
                fileName = getFileName(context, targetUri)
                if (targetUri.scheme == "file") {
                    val file = java.io.File(targetUri.path ?: "")
                    if (file.exists()) {
                        targetUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    }
                }
            } else if (!fallbackPath.isNullOrEmpty()) {
                val file = java.io.File(fallbackPath)
                if (file.exists()) {
                    fileName = file.name
                    targetUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }

            if (targetUri == null) {
                Toast.makeText(context, "Fichier introuvable pour le partage", Toast.LENGTH_SHORT).show()
                return
            }

            val ext = if (fileName.contains('.')) fileName.substringAfterLast('.').lowercase() else ""
            var mime = context.contentResolver.getType(targetUri)
            if (mime.isNullOrBlank() || mime == "application/octet-stream") {
                val mapMime = if (ext.isNotEmpty()) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
                mime = mapMime ?: "*/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, targetUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Partager avec...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Impossible de partager ce fichier", Toast.LENGTH_SHORT).show()
        }
    }
}
