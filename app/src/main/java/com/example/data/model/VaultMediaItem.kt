package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VaultMediaType {
    PHOTO, VIDEO, AUDIO
}

@Entity(tableName = "vault_media_items")
data class VaultMediaItem(
    @PrimaryKey
    val id: String,
    val mediaType: VaultMediaType,
    val originalName: String,
    val privateFilePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val importedAt: Long = System.currentTimeMillis(),
    val originalUriString: String? = null,
    val sourceHash: String? = null,
    val width: Int = 0,
    val height: Int = 0
)
