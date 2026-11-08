package com.lancefer.lancedrop.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.lancefer.lancedrop.ui.screen.MobileFileItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.lancefer.lancedrop.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

object MediaStoreHelper {

    fun getRequiredStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasStoragePermission(context: Context): Boolean {
        val permissions = getRequiredStoragePermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Lit les vrais fichiers multimédias de l'appareil (images, vidéos, audio) via MediaStore
     */
    fun queryRealMediaFiles(context: Context, categoryFilter: String = "Tous"): List<MobileFileItem> {
        val files = mutableListOf<MobileFileItem>()
        val contentResolver = context.contentResolver
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

        // 1. Images
        if (categoryFilter == "Tous" || categoryFilter == "Images") {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                while (it.moveToNext() && files.size < 100) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Image_$id.jpg"
                    val size = it.getLong(sizeCol)
                    val dateSec = it.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeFormatted = com.lancefer.lancedrop.utils.FileHelper.formatFileSize(size)
                    val dateFormatted = dateFormat.format(Date(dateSec * 1000L))

                    files.add(
                        MobileFileItem(
                            id = uri.toString(),
                            name = name,
                            size = sizeFormatted,
                            date = dateFormatted,
                            category = "Images",
                            icon = Icons.Default.Image,
                            iconColor = LanceDropBlue,
                            bgColor = LanceDropBlueLight
                        )
                    )
                }
            }
        }

        // 2. Vidéos
        if (categoryFilter == "Tous" || categoryFilter == "Vidéos") {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED
            )
            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                while (it.moveToNext() && files.size < 150) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Video_$id.mp4"
                    val size = it.getLong(sizeCol)
                    val dateSec = it.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeFormatted = com.lancefer.lancedrop.utils.FileHelper.formatFileSize(size)
                    val dateFormatted = dateFormat.format(Date(dateSec * 1000L))

                    files.add(
                        MobileFileItem(
                            id = uri.toString(),
                            name = name,
                            size = sizeFormatted,
                            date = dateFormatted,
                            category = "Vidéos",
                            icon = Icons.Default.Movie,
                            iconColor = LanceDropPurple,
                            bgColor = LanceDropPurpleLight
                        )
                    )
                }
            }
        }

        // 3. Audio
        if (categoryFilter == "Tous" || categoryFilter == "Audio") {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
            )
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (it.moveToNext() && files.size < 200) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Audio_$id.mp3"
                    val size = it.getLong(sizeCol)
                    val dateSec = it.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val sizeFormatted = com.lancefer.lancedrop.utils.FileHelper.formatFileSize(size)
                    val dateFormatted = dateFormat.format(Date(dateSec * 1000L))

                    files.add(
                        MobileFileItem(
                            id = uri.toString(),
                            name = name,
                            size = sizeFormatted,
                            date = dateFormatted,
                            category = "Audio",
                            icon = Icons.Default.MusicNote,
                            iconColor = LanceDropGreen,
                            bgColor = LanceDropGreenLight
                        )
                    )
                }
            }
        }

        return files
    }
}
