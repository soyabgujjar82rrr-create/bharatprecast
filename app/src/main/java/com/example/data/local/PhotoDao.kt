package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DismissedPhoto
import com.example.data.model.GalleryPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM gallery_photos WHERE userId = :userId ORDER BY timestamp DESC")
    fun getPhotosForUser(userId: String): Flow<List<GalleryPhoto>>

    @Query("SELECT * FROM gallery_photos WHERE userId = :userId AND isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoritePhotos(userId: String): Flow<List<GalleryPhoto>>

    @Query("SELECT * FROM gallery_photos WHERE userId = :userId AND isUploaded = 1 ORDER BY timestamp DESC")
    fun getUploadedPhotos(userId: String): Flow<List<GalleryPhoto>>

    @Query("SELECT * FROM gallery_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): GalleryPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: GalleryPhoto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<GalleryPhoto>)

    @Update
    suspend fun updatePhoto(photo: GalleryPhoto)

    @Delete
    suspend fun deletePhoto(photo: GalleryPhoto)

    @Query("DELETE FROM gallery_photos WHERE id = :id")
    suspend fun deletePhotoById(id: String)

    @Query("SELECT COUNT(*) FROM gallery_photos WHERE userId = :userId")
    fun getPhotoCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM gallery_photos WHERE userId = :userId AND isFavorite = 1")
    fun getFavoriteCount(userId: String): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM gallery_photos WHERE userId = :userId")
    fun getTotalStorageBytes(userId: String): Flow<Long?>

    @Query("SELECT originalMediaStoreId FROM gallery_photos WHERE userId = :userId AND originalMediaStoreId IS NOT NULL")
    suspend fun getExistingMediaStoreIds(userId: String): List<Long>

    @Query("SELECT mediaStoreId FROM dismissed_photos")
    suspend fun getDismissedMediaStoreIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissedPhoto(dismissed: DismissedPhoto)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDismissedPhotos(dismissed: List<DismissedPhoto>)
}
