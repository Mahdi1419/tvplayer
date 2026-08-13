package com.example.tvplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.os.storage.StorageManager
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/**
 * Finds local videos from MediaStore and, where Android exposes a readable
 * removable volume, directly from its filesystem. A SAF tree can also be
 * scanned for USB drives that are hidden from MediaStore/scoped storage.
 */
object LocalVideoScanner {

    private val videoExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "mts",
        "flv", "wmv", "3gp", "3g2", "mpeg", "mpg", "vob", "ogv"
    )

    fun scan(context: Context): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val seen = HashSet<String>()

        scanMediaStore(context, result, seen)
        scanReadableStorageVolumes(context, result, seen)

        return result.sortedByDescending { it.durationMs }
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
                        val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) {
                            cursor.getLong(durationCol)
                        } else 0L

                        result.add(VideoItem(name, uriString, true, duration))
                    }
                }
            } catch (_: SecurityException) {
                // Permission can be unavailable on some TV firmwares.
            } catch (_: Exception) {
                // One broken volume must not stop the whole scan.
            }
        }
    }

    private fun scanReadableStorageVolumes(
        context: Context,
        result: MutableList<VideoItem>,
        seen: MutableSet<String>
    ) {
        val roots = LinkedHashSet<String>()
        // Common USB mount point on Android TV (for example /storage/1234-ABCD).
        try {
            File("/storage").listFiles()?.forEach { child ->
                if (child.isDirectory && child.name != "emulated" && child.name != "self") {
                    roots.add(child.absolutePath)
                }
            }
        } catch (_: Exception) {
            // Ignore inaccessible mount points.
        }

        try {
            val storageManager = context.getSystemService(StorageManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                storageManager.storageVolumes.forEach { volume ->
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory else null
                    if (volume.isRemovable && dir != null && dir.exists() && dir.canRead()) {
                        roots.add(dir.absolutePath)
                    }
                }
            }
        } catch (_: Exception) {
            // Some Android TV builds expose incomplete StorageVolume data.
        }

        for (rootPath in roots) {
            try {
                val root = File(rootPath)
                if (!root.exists() || !root.isDirectory || !root.canRead()) continue
                root.walkTopDown()
                    .onEnter { it.canRead() }
                    .filter { it.isFile && isVideoFile(it) }
                    .forEach { file ->
                        val uri = Uri.fromFile(file).toString()
                        if (!seen.add(uri)) return@forEach
                        result.add(
                            VideoItem(
                                title = file.name,
                                url = uri,
                                isLocal = true,
                                durationMs = 0L
                            )
                        )
                    }
            } catch (_: SecurityException) {
                // Scoped storage may block direct filesystem traversal.
            } catch (_: Exception) {
                // Continue with the next volume.
            }
        }
    }

    fun scanTree(context: Context, treeUri: Uri): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val seen = HashSet<String>()
        val resolver = context.contentResolver
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return emptyList()
        }

        val queue = ArrayDeque<String>()
        queue.add(rootId)

        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    if (idCol < 0) return@use

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(idCol) ?: continue
                        val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "ویدیو بدون نام"
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(documentId)
                            continue
                        }

                        if (!mime.startsWith("video/", ignoreCase = true) && !isVideoFileName(name)) continue

                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        val uriString = uri.toString()
                        if (!seen.add(uriString)) continue
                        result.add(VideoItem(name.ifEmpty { "ویدیو بدون نام" }, uriString, true, 0L))
                    }
                }
            } catch (_: Exception) {
                // Skip inaccessible directories.
            }
        }

        return result
    }

    private fun isVideoFile(file: File): Boolean = isVideoFileName(file.name)

    private fun isVideoFileName(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in videoExtensions
    }
}
