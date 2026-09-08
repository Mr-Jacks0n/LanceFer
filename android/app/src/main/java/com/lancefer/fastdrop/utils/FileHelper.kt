package com.lancefer.fastdrop.utils

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

    /**
     * Extraire les métadonnées (Nom et Taille) d'un URI SAF (content://)
     */
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

    /**
     * Ouvrir un fichier reçu via l'application système appropriée (Galerie, Lecteur, etc.)
     */
    fun openFile(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir le fichier", Toast.LENGTH_SHORT).show()
        }
    }
}
