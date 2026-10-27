package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.VaultMediaItem
import com.example.data.model.VaultMediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_media_items WHERE mediaType = :type ORDER BY importedAt DESC")
    fun getItemsByType(type: VaultMediaType): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media_items ORDER BY importedAt DESC")
    fun getAllItems(): Flow<List<VaultMediaItem>>

    @Query("SELECT * FROM vault_media_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): VaultMediaItem?

    @Query("SELECT * FROM vault_media_items WHERE originalUriString = :uriString LIMIT 1")
    suspend fun getItemByOriginalUri(uriString: String): VaultMediaItem?

    @Query("SELECT * FROM vault_media_items WHERE sourceHash = :hash LIMIT 1")
    suspend fun getItemBySourceHash(hash: String): VaultMediaItem?

    @Query("SELECT * FROM vault_media_items WHERE originalName = :name AND sizeBytes = :size LIMIT 1")
    suspend fun getItemByNameAndSize(name: String, size: Long): VaultMediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: VaultMediaItem)

    @Delete
    suspend fun deleteItem(item: VaultMediaItem)

    @Delete
    suspend fun deleteItems(items: List<VaultMediaItem>)

    @Query("DELETE FROM vault_media_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("SELECT COUNT(*) FROM vault_media_items WHERE mediaType = :type")
    fun getCountByType(type: VaultMediaType): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM vault_media_items")
    fun getTotalVaultSizeBytes(): Flow<Long?>
}
