package com.example.tvplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/**
 * جستجوی ویدیوهای موجود روی دستگاه از طریق MediaStore
 * (بدون نیاز به دسترسی مستقیم به فایل‌سیستم)
 */
object LocalVideoScanner {

    fun scan(context: Context): List<VideoItem> {
        val result = mutableListOf<VideoItem>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "ویدیو بدون نام"
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                result.add(
                    VideoItem(
                        title = name,
                        url = contentUri.toString(),
                        isLocal = true,
                        durationMs = duration
                    )
                )
            }
        }

        return result
    }
}
