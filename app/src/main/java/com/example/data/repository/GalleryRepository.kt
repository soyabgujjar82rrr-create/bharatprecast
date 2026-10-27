package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.data.firebase.FirebaseManager
import com.example.data.local.AppDatabase
import com.example.data.local.PhotoDao
import com.example.data.model.DismissedPhoto
import com.example.data.model.GalleryPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GalleryRepository(
    private val context: Context,
    private val photoDao: PhotoDao = AppDatabase.getInstance(context).photoDao(),
    val firebaseManager: FirebaseManager = FirebaseManager(context)
) {
    private val photosDirectory: File by lazy {
        File(context.filesDir, "mygallery_photos").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getPhotos(userId: String): Flow<List<GalleryPhoto>> = photoDao.getPhotosForUser(userId)

    fun getFavoritePhotos(userId: String): Flow<List<GalleryPhoto>> = photoDao.getFavoritePhotos(userId)

    fun getUploadedPhotos(userId: String): Flow<List<GalleryPhoto>> = photoDao.getUploadedPhotos(userId)

    fun getPhotoCount(userId: String): Flow<Int> = photoDao.getPhotoCount(userId)

    fun getFavoriteCount(userId: String): Flow<Int> = photoDao.getFavoriteCount(userId)

    fun getTotalStorageBytes(userId: String): Flow<Long?> = photoDao.getTotalStorageBytes(userId)

    /**
     * Requirement 1: "Photo permanently बनी रहे"
     * Copies the image file permanently into MyGallery's internal private app directory.
     * This guarantees the photo remains safe permanently even across reboots or cache clears.
     */
    private fun savePermanentlyToInternalStorage(sourceUri: Uri, baseFileName: String): Pair<Uri, Long> {
        val sanitized = baseFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val uniqueName = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}_$sanitized"
        val destFile = File(photosDirectory, uniqueName)

        var totalBytes = 0L
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
            }
        } ?: run {
            // Fallback: if opening stream fails, use the original Uri
            return Pair(sourceUri, 0L)
        }

        return Pair(Uri.fromFile(destFile), if (totalBytes > 0) totalBytes else destFile.length())
    }

    suspend fun addLocalPhoto(
        userId: String,
        uri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        mediaStoreId: Long? = null,
        source: String = "IMPORT"
    ): GalleryPhoto = withContext(Dispatchers.IO) {
        // Save permanently to internal storage so it NEVER disappears
        val (permanentUri, actualSize) = try {
            savePermanentlyToInternalStorage(uri, fileName)
        } catch (e: Exception) {
            Pair(uri, fileSizeBytes)
        }

        val photo = GalleryPhoto(
            id = UUID.randomUUID().toString(),
            userId = userId,
            localUri = permanentUri.toString(),
            fileName = fileName,
            timestamp = System.currentTimeMillis(),
            isFavorite = false,
            isUploaded = false,
            fileSizeBytes = if (actualSize > 0) actualSize else fileSizeBytes,
            originalMediaStoreId = mediaStoreId,
            source = source
        )
        photoDao.insertPhoto(photo)
        photo
    }

    suspend fun updatePhotoFile(photo: GalleryPhoto, newFile: File): GalleryPhoto = withContext(Dispatchers.IO) {
        val updated = photo.copy(
            localUri = Uri.fromFile(newFile).toString(),
            fileSizeBytes = newFile.length(),
            isUploaded = false
        )
        photoDao.updatePhoto(updated)
        updated
    }

    /**
     * Requirement 2: "Camera से ली गई नई photo अपने-आप My Gallery में दिखे"
     * Scans MediaStore for new photos taken by camera, skips existing and deleted ones,
     * and automatically saves them permanently into MyGallery.
     */
    suspend fun syncCameraPhotos(userId: String): Int = withContext(Dispatchers.IO) {
        if (!hasStoragePermission()) {
            return@withContext 0
        }

        val existingIds = photoDao.getExistingMediaStoreIds(userId).toSet()
        val dismissedIds = photoDao.getDismissedMediaStoreIds().toSet()

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val selection = "${MediaStore.Images.Media.SIZE} > 0"
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        var importedCount = 0
        try {
            context.contentResolver.query(collectionUri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var checked = 0
                // Scan the most recent 150 camera/device photos
                while (cursor.moveToNext() && checked < 150) {
                    checked++
                    val mediaStoreId = cursor.getLong(idCol)
                    if (existingIds.contains(mediaStoreId) || dismissedIds.contains(mediaStoreId)) {
                        continue
                    }

                    val name = cursor.getString(nameCol) ?: "camera_photo_$mediaStoreId.jpg"
                    val size = cursor.getLong(sizeCol)
                    val dateAddedSec = cursor.getLong(dateCol)
                    val timestamp = if (dateAddedSec > 0) dateAddedSec * 1000L else System.currentTimeMillis()
                    val sourceUri = ContentUris.withAppendedId(collectionUri, mediaStoreId)

                    // Save permanently to internal app storage
                    val (permanentUri, actualSize) = try {
                        savePermanentlyToInternalStorage(sourceUri, name)
                    } catch (e: Exception) {
                        Pair(sourceUri, size)
                    }

                    val photo = GalleryPhoto(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        localUri = permanentUri.toString(),
                        fileName = name,
                        timestamp = timestamp,
                        isFavorite = false,
                        isUploaded = false,
                        fileSizeBytes = if (actualSize > 0) actualSize else size,
                        originalMediaStoreId = mediaStoreId,
                        source = "CAMERA"
                    )
                    photoDao.insertPhoto(photo)
                    importedCount++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        importedCount
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requirement 2: Observes camera/storage photo changes in real-time.
     * When camera takes a new picture, Android notifies this observer immediately!
     */
    fun createCameraContentObserver(
        scope: CoroutineScope,
        getUserId: () -> String?,
        onSyncComplete: (Int) -> Unit
    ): ContentObserver {
        return object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val userId = getUserId() ?: return
                scope.launch {
                    val count = syncCameraPhotos(userId)
                    if (count > 0) {
                        onSyncComplete(count)
                    }
                }
            }
        }
    }

    suspend fun uploadPhotoToCloud(photo: GalleryPhoto): Result<GalleryPhoto> = withContext(Dispatchers.IO) {
        if (!firebaseManager.isFirebaseInitialized) {
            return@withContext Result.failure(Exception("Firebase is not initialized. Please add google-services.json."))
        }

        try {
            val uri = Uri.parse(photo.localUri)
            val uploadResult = firebaseManager.uploadPhoto(
                userId = photo.userId,
                photoId = photo.id,
                fileUri = uri
            )

            uploadResult.mapCatching { (downloadUrl, storagePath) ->
                val updatedPhoto = photo.copy(
                    remoteUrl = downloadUrl,
                    storagePath = storagePath,
                    isUploaded = true
                )
                photoDao.updatePhoto(updatedPhoto)
                updatedPhoto
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(photo: GalleryPhoto) = withContext(Dispatchers.IO) {
        val updated = photo.copy(isFavorite = !photo.isFavorite)
        photoDao.updatePhoto(updated)
    }

    /**
     * Requirement 3: "MyGallery से हटाने पर original Gallery photo delete न हो"
     * Removes the photo from MyGallery Room DB & app internal storage.
     * Adds the MediaStore ID to dismissed_photos so auto-sync never re-adds it.
     * DOES NOT delete the original phone/camera photo from device gallery.
     */
    suspend fun deletePhoto(photo: GalleryPhoto) = withContext(Dispatchers.IO) {
        // 1. Mark MediaStore ID as dismissed so auto-sync will never re-import it
        photo.originalMediaStoreId?.let { mediaId ->
            photoDao.insertDismissedPhoto(DismissedPhoto(mediaStoreId = mediaId))
        }

        // 2. Delete internal private file copy from MyGallery
        try {
            val uri = Uri.parse(photo.localUri)
            if (uri.scheme == "file") {
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists() && file.parentFile == photosDirectory) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore file deletion errors
        }

        // 3. Delete from Firebase Storage if uploaded
        photo.storagePath?.let { path ->
            try {
                firebaseManager.deletePhoto(path)
            } catch (e: Exception) {
                // Continue with local deletion even if remote delete fails
            }
        }

        // 4. Remove record from MyGallery Room database
        photoDao.deletePhoto(photo)

        // NOTE: We deliberately DO NOT touch contentResolver.delete() on the MediaStore!
        // The user's original phone gallery / camera photo remains 100% safe and intact!
    }
}
