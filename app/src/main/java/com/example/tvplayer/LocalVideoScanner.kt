package com.example.tvplayer

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Finds videos from all MediaStore external volumes, including removable/USB
 * storage when Android has indexed that storage.
 */
object LocalVideoScanner {

    fun scan(context: Context): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val seen = HashSet<String>()

        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf("external")
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        for (volume in volumes) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(volume)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    if (idCol < 0 || nameCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val uriString = uri.toString()
                        if (!seen.add(uriString)) continue

                        val name = cursor.getString(nameCol).orEmpty().ifEmpty { "ویدیو بدون نام" }
                        val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) {
                            cursor.getLong(durationCol)
                        } else {
                            0L
                        }

                        result.add(
                            VideoItem(
                                title = name,
                                url = uriString,
                                isLocal = true,
                                durationMs = duration
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                // A volume can disappear or become unavailable while scanning.
            } catch (_: Exception) {
                // Ignore a broken/unavailable volume and continue with the others.
            }
        }

        return result
    }
}
