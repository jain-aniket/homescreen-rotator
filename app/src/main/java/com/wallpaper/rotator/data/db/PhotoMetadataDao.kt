package com.wallpaper.rotator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoMetadataDao {
    @Insert
    suspend fun insert(photo: PhotoMetadata): Long

    @Update
    suspend fun update(photo: PhotoMetadata)

    @Delete
    suspend fun delete(photo: PhotoMetadata)

    @Query("SELECT * FROM photo_metadata WHERE photoId = :id")
    suspend fun getById(id: Long): PhotoMetadata?

    @Query("SELECT * FROM photo_metadata ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PhotoMetadata>>

    @Query("SELECT * FROM photo_metadata WHERE isEnabled = 1 ORDER BY createdAt ASC")
    fun getEnabledPhotos(): Flow<List<PhotoMetadata>>

    @Query("SELECT * FROM photo_metadata WHERE isEnabled = 1 ORDER BY createdAt ASC")
    suspend fun getEnabledPhotosList(): List<PhotoMetadata>

    @Query("SELECT COUNT(*) FROM photo_metadata WHERE isEnabled = 1")
    suspend fun getEnabledCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM photo_metadata WHERE sourceUri = :uri LIMIT 1)")
    suspend fun existsBySourceUri(uri: String): Boolean

    @Query("DELETE FROM photo_metadata WHERE photoId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE photo_metadata SET isEnabled = :enabled WHERE photoId IN (:ids)")
    suspend fun updateEnabled(ids: List<Long>, enabled: Boolean)
}
