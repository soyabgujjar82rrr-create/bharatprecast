package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.model.VaultMediaItem
import com.example.data.model.VaultMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

class VaultRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val vaultDao = db.vaultDao()

    // Concurrency guard to prevent importing the same URI simultaneously
    private val importingUris = Collections.synchronizedSet(mutableSetOf<String>())

    fun getPhotos(): Flow<List<VaultMediaItem>> = vaultDao.getItemsByType(VaultMediaType.PHOTO)
        .map { list -> list.distinctBy { it.sourceHash ?: it.id } }

    fun getVideos(): Flow<List<VaultMediaItem>> = vaultDao.getItemsByType(VaultMediaType.VIDEO)
        .map { list -> list.distinctBy { it.sourceHash ?: it.id } }

    fun getAudio(): Flow<List<VaultMediaItem>> = vaultDao.getItemsByType(VaultMediaType.AUDIO)
        .map { list -> list.distinctBy { it.sourceHash ?: it.id } }

    fun getAllItems(): Flow<List<VaultMediaItem>> = vaultDao.getAllItems()
        .map { list -> list.distinctBy { it.sourceHash ?: it.id } }

    fun getTotalVaultSize(): Flow<Long?> = vaultDao.getTotalVaultSizeBytes()

    /**
     * Securely imports a public media file into internal app-private storage.
     * Prevents duplicate imports using URI and SHA-256 hash detection.
     * Verifies the private file copy before removing the public MediaStore copy.
     */
    suspend fun importMedia(uri: Uri, type: VaultMediaType): Result<VaultMediaItem> = withContext(Dispatchers.IO) {
        val uriString = uri.toString()

        // 1. Prevent concurrent duplicate imports of the same URI
        if (!importingUris.add(uriString)) {
            // Already being imported right now, check if DB already has it
            vaultDao.getItemByOriginalUri(uriString)?.let { return@withContext Result.success(it) }
        }

        try {
            val contentResolver = context.contentResolver

            // 2. Pre-check: Check if this specific URI has already been imported
            val existingByUri = vaultDao.getItemByOriginalUri(uriString)
            if (existingByUri != null && File(existingByUri.privateFilePath).exists()) {
                return@withContext Result.success(existingByUri)
            }

            // 3. Resolve original display name & size
            var originalName = "media_${System.currentTimeMillis()}"
            var expectedSize = -1L
            if (uri.scheme == "file") {
                val localFile = File(uri.path ?: uri.schemeSpecificPart)
                if (localFile.exists()) {
                    originalName = localFile.name
                    expectedSize = localFile.length()
                }
            } else {
                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) {
                                cursor.getString(nameIndex)?.let { originalName = it }
                            }
                            if (sizeIndex != -1) {
                                expectedSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // If query fails, fall back to last path segment
                    uri.lastPathSegment?.let { originalName = it }
                }
            }

            val mimeType = (if (uri.scheme == "content") contentResolver.getType(uri) else null) ?: when (type) {
                VaultMediaType.PHOTO -> "image/jpeg"
                VaultMediaType.VIDEO -> "video/mp4"
                VaultMediaType.AUDIO -> "audio/mpeg"
            }

            // 4. Prepare app-private vault directory
            val subDirName = when (type) {
                VaultMediaType.PHOTO -> "photos"
                VaultMediaType.VIDEO -> "videos"
                VaultMediaType.AUDIO -> "audio"
            }
            val vaultDir = File(context.filesDir, "vault/$subDirName").apply { mkdirs() }

            // Write to a temporary file first while computing SHA-256 hash
            val tempFile = File(vaultDir, "temp_import_${UUID.randomUUID()}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")

            try {
                val inputStream = if (uri.scheme == "file") {
                    val filePath = uri.path ?: uri.schemeSpecificPart
                    FileInputStream(File(filePath))
                } else {
                    contentResolver.openInputStream(uri)
                }

                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                } ?: run {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("Unable to read source media stream"))
                }
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext Result.failure(e)
            }

            val computedHash = digest.digest().joinToString("") { "%02x".format(it) }

            // 5. Check if the media with this exact SHA-256 hash is ALREADY in the vault
            val existingByHash = vaultDao.getItemBySourceHash(computedHash)
            if (existingByHash != null && File(existingByHash.privateFilePath).exists()) {
                tempFile.delete() // Clean up temporary copy
                // Also attempt to remove original public item if still lingering
                try { contentResolver.delete(uri, null, null) } catch (e: Exception) { /* ignore */ }
                return@withContext Result.success(existingByHash)
            }

            // 6. Name and size duplicate fallback check
            if (expectedSize > 0L) {
                val existingByNameSize = vaultDao.getItemByNameAndSize(originalName, expectedSize)
                if (existingByNameSize != null && File(existingByNameSize.privateFilePath).exists()) {
                    tempFile.delete()
                    try { contentResolver.delete(uri, null, null) } catch (e: Exception) { /* ignore */ }
                    return@withContext Result.success(existingByNameSize)
                }
            }

            // 7. Move temporary file to final private vault destination
            val fileExt = originalName.substringAfterLast('.', "")
            val cleanExt = if (fileExt.isNotBlank()) ".$fileExt" else when (type) {
                VaultMediaType.PHOTO -> ".jpg"
                VaultMediaType.VIDEO -> ".mp4"
                VaultMediaType.AUDIO -> ".mp3"
            }
            val privateFile = File(vaultDir, "vault_${UUID.randomUUID()}$cleanExt")
            val moved = tempFile.renameTo(privateFile)
            if (!moved) {
                tempFile.copyTo(privateFile, overwrite = true)
                tempFile.delete()
            }

            // 8. STRICT VERIFICATION: Verify private file exists, is non-empty, and can be opened
            if (!privateFile.exists() || privateFile.length() <= 0L) {
                privateFile.delete()
                return@withContext Result.failure(Exception("Private file verification failed (zero size)"))
            }

            var canRead = false
            try {
                FileInputStream(privateFile).use { fis ->
                    canRead = (fis.read() != -1 || privateFile.length() == 0L)
                }
            } catch (e: Exception) {
                canRead = false
            }

            if (!canRead) {
                privateFile.delete()
                return@withContext Result.failure(Exception("Private file verification failed (unreadable)"))
            }

            // 9. Extract media metadata
            var width = 0
            var height = 0
            var durationMs = 0L

            if (type == VaultMediaType.PHOTO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(privateFile.absolutePath, options)
                    width = options.outWidth
                    height = options.outHeight
                } catch (e: Exception) {
                    // Ignore dimension extraction failure
                }
            } else {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(privateFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                        durationMs = it.toLongOrNull() ?: 0L
                    }
                    if (type == VaultMediaType.VIDEO) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let {
                            width = it.toIntOrNull() ?: 0
                        }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let {
                            height = it.toIntOrNull() ?: 0
                        }
                    }
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore duration extraction failure
                }
            }

            // 10. Only AFTER private copy is verified, delete the original public MediaStore/shared copy
            if (uri.scheme == "file") {
                try {
                    val localFile = File(uri.path ?: uri.schemeSpecificPart)
                    if (localFile.exists()) localFile.delete()
                } catch (e: Exception) { /* ignore */ }
            } else {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    // Scoped storage / security handling
                }
            }

            // 11. Insert record into Room database
            val item = VaultMediaItem(
                id = UUID.randomUUID().toString(),
                mediaType = type,
                originalName = originalName,
                privateFilePath = privateFile.absolutePath,
                mimeType = mimeType,
                sizeBytes = privateFile.length(),
                durationMs = durationMs,
                importedAt = System.currentTimeMillis(),
                originalUriString = uriString,
                sourceHash = computedHash,
                width = width,
                height = height
            )
            vaultDao.insertItem(item)

            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            importingUris.remove(uriString)
        }
    }

    /**
     * Restores a private vault file back into appropriate public MediaStore storage.
     * Verifies the restored public copy before deleting the private copy.
     */
    suspend fun restoreMedia(item: VaultMediaItem): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val privateFile = File(item.privateFilePath)
            if (!privateFile.exists() || privateFile.length() <= 0L) {
                return@withContext Result.failure(Exception("Private media file not found or corrupted on disk"))
            }

            val contentResolver = context.contentResolver
            val (collectionUri, relativeSubdir) = when (item.mediaType) {
                VaultMediaType.PHOTO -> Pair(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_PICTURES + "/Restored"
                )
                VaultMediaType.VIDEO -> Pair(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MOVIES + "/Restored"
                )
                VaultMediaType.AUDIO -> Pair(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    Environment.DIRECTORY_MUSIC + "/Restored"
                )
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubdir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val publicUri = contentResolver.insert(collectionUri, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore destination"))

            // Copy bytes from private internal storage to public MediaStore
            FileInputStream(privateFile).use { input ->
                contentResolver.openOutputStream(publicUri)?.use { output ->
                    input.copyTo(output)
                } ?: throw Exception("Failed to open public destination output stream")
            }

            // Release pending flag on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(publicUri, contentValues, null, null)
            }

            // Verify restored public copy
            val publicDescriptor = contentResolver.openFileDescriptor(publicUri, "r")
            val publicSize = publicDescriptor?.statSize ?: -1L
            publicDescriptor?.close()

            if (publicSize <= 0L && privateFile.length() > 0L) {
                contentResolver.delete(publicUri, null, null)
                return@withContext Result.failure(Exception("Restored file verification failed"))
            }

            // Safely delete private copy now that public copy is verified
            privateFile.delete()
            vaultDao.deleteItem(item)

            // Trigger MediaScanner so Gallery/Google Photos immediately sees restored file
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val publicPath = "${Environment.getExternalStorageDirectory()}/$relativeSubdir/${item.originalName}"
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publicPath),
                        arrayOf(item.mimeType),
                        null
                    )
                }
            } catch (e: Exception) {
                // Ignore scanner warning
            }

            Result.success(publicUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch restores multiple private vault items.
     */
    suspend fun batchRestoreMedia(items: List<VaultMediaItem>): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        items.forEach { item ->
            if (restoreMedia(item).isSuccess) {
                restoredCount++
            }
        }
        restoredCount
    }

    /**
     * Permanently deletes a private media file from the device.
     */
    suspend fun deletePermanently(item: VaultMediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(item.privateFilePath)
            if (file.exists()) {
                file.delete()
            }
            vaultDao.deleteItem(item)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch deletes multiple private vault items.
     */
    suspend fun batchDeletePermanently(items: List<VaultMediaItem>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        items.forEach { item ->
            val file = File(item.privateFilePath)
            if (file.exists()) {
                file.delete()
            }
            vaultDao.deleteItem(item)
            deletedCount++
        }
        deletedCount
    }

    /**
     * Generates secure FileProvider content URIs only AFTER authentication has succeeded.
     */
    fun getShareableContentUris(items: List<VaultMediaItem>): List<Uri> {
        return items.mapNotNull { item ->
            val file = File(item.privateFilePath)
            if (file.exists() && file.length() > 0L) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else null
        }
    }

    /**
     * Shares authenticated private media files to WhatsApp or Android Sharesheet.
     * Grants temporary read permission only to authorized targets.
     */
    fun shareAuthenticatedMedia(
        items: List<VaultMediaItem>,
        targetPackage: String? = null
    ): Boolean {
        if (items.isEmpty()) return false
        val uris = getShareableContentUris(items)
        if (uris.isEmpty()) return false

        val commonMimeType = if (items.size == 1) {
            items.first().mimeType
        } else {
            val allPhotos = items.all { it.mediaType == VaultMediaType.PHOTO }
            val allVideos = items.all { it.mediaType == VaultMediaType.VIDEO }
            val allAudio = items.all { it.mediaType == VaultMediaType.AUDIO }
            when {
                allPhotos -> "image/*"
                allVideos -> "video/*"
                allAudio -> "audio/*"
                else -> "*/*"
            }
        }

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = commonMimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        // Specifically grant permissions to WhatsApp if explicitly targeted or present
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        if (targetPackage != null) {
            shareIntent.setPackage(targetPackage)
            uris.forEach { uri ->
                try {
                    context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { /* ignore */ }
            }
        } else {
            whatsappPackages.forEach { pkg ->
                uris.forEach { uri ->
                    try {
                        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }

        val chooserTitle = if (items.size == 1) "Share ${items.first().originalName}" else "Share ${items.size} Media Files"
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
