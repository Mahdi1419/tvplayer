package com.example.tvplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.Locale

/** Scans MediaStore and user-authorized USB/storage folders through SAF. */
object LocalVideoScanner {

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts",
        "mts", "flv", "wmv", "3gp", "mpeg", "mpg", "vob", "ogv"
    )

    fun scan(context: Context, treeUris: List<Uri> = emptyList()): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val seen = HashSet<String>()
        scanMediaStore(context, result, seen)
        for (treeUri in treeUris) {
            try {
                scanTree(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), result, seen)
            } catch (_: Exception) {
                // Ignore stale permissions.
            }
        }
        return result
    }

    private fun scanMediaStore(
        context: Context,
        result: MutableList<VideoItem>,
        seen: MutableSet<String>
    ) {
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
                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
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
                        val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0L
                        result.add(VideoItem(name, uriString, true, duration))
                    }
                }
            } catch (_: Exception) {
                // Continue with other volumes.
            }
        }
    }

    private fun scanTree(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        result: MutableList<VideoItem>,
        seen: MutableSet<String>
    ) {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idCol < 0 || nameCol < 0 || mimeCol < 0) return@use

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol).orEmpty()
                    val mime = cursor.getString(mimeCol).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanTree(context, treeUri, documentId, result, seen)
                    } else if (isVideo(name, mime)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        val uriString = documentUri.toString()
                        if (seen.add(uriString)) {
                            result.add(VideoItem(name.ifBlank { "ویدیو بدون نام" }, uriString, true, 0L))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // USB can be unplugged or the provider may reject a folder.
        }
    }

    private fun isVideo(name: String, mime: String): Boolean {
        if (mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in videoExtensions
    }
}
