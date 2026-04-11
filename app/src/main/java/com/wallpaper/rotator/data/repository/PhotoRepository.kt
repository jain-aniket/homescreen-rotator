package com.wallpaper.rotator.data.repository

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.wallpaper.rotator.data.db.CropMethod
import com.wallpaper.rotator.data.db.PhotoMetadata
import com.wallpaper.rotator.data.db.PhotoMetadataDao
import com.wallpaper.rotator.data.db.SubjectType
import com.wallpaper.rotator.util.ThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoRepository(
    private val dao: PhotoMetadataDao,
    private val context: Context
) {
    private val photosDir: File
        get() = File(context.filesDir, "photos").also { it.mkdirs() }

    fun getAllPhotos(): Flow<List<PhotoMetadata>> = dao.getAll()

    fun getEnabledPhotos(): Flow<List<PhotoMetadata>> = dao.getEnabledPhotos()

    suspend fun getPhotoById(id: Long): PhotoMetadata? = dao.getById(id)

    suspend fun existsImportedSourceUri(uriKey: String): Boolean = dao.existsBySourceUri(uriKey)

    suspend fun importPhoto(
        sourceUri: Uri,
        cropBox: RectF,
        subjectType: SubjectType,
        cropMethod: CropMethod
    ): Long = withContext(Dispatchers.IO) {
        val filename = "${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, filename)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open URI: $sourceUri")

        val metadata = PhotoMetadata(
            sourceUri = sourceUri.toString(),
            filePath = destFile.absolutePath,
            cropX = cropBox.left,
            cropY = cropBox.top,
            cropWidth = cropBox.width(),
            cropHeight = cropBox.height(),
            detectedSubjectType = subjectType,
            cropMethod = cropMethod
        )
        val id = dao.insert(metadata)
        ThumbnailGenerator.writeThumbnail(context, metadata.copy(photoId = id))
        id
    }

    suspend fun updatePhoto(photo: PhotoMetadata) = dao.update(photo)

    suspend fun regenerateThumbnail(photo: PhotoMetadata) = withContext(Dispatchers.IO) {
        ThumbnailGenerator.writeThumbnail(context, photo)
    }

    suspend fun deletePhotos(ids: List<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            dao.getById(id)?.let { photo ->
                File(photo.filePath).delete()
            }
            ThumbnailGenerator.thumbnailFile(context, id).delete()
        }
        dao.deleteByIds(ids)
    }

    suspend fun toggleEnabled(ids: List<Long>, enabled: Boolean) {
        dao.updateEnabled(ids, enabled)
    }

    suspend fun getNextForRotation(lastIndex: Int): Pair<PhotoMetadata, Int>? {
        val photos = dao.getEnabledPhotosList()
        if (photos.isEmpty()) return null
        val nextIndex = (lastIndex + 1) % photos.size
        return Pair(photos[nextIndex], nextIndex)
    }

    suspend fun getEnabledCount(): Int = dao.getEnabledCount()
}
