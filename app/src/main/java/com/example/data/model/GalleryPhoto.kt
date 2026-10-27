package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_photos")
data class GalleryPhoto(
    @PrimaryKey
    val id: String,
    val userId: String,
    val localUri: String,
    val remoteUrl: String? = null,
    val storagePath: String? = null,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isUploaded: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val originalMediaStoreId: Long? = null,
    val source: String = "IMPORT" // "CAMERA", "PICKER", "IMPORT"
)

@Entity(tableName = "dismissed_photos")
data class DismissedPhoto(
    @PrimaryKey
    val mediaStoreId: Long,
    val dismissedAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean = false
)
