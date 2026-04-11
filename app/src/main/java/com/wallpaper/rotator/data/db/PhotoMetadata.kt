package com.wallpaper.rotator.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class SubjectType {
    FACE, FULL_BODY, LANDSCAPE
}

enum class CropMethod {
    AUTO, MANUAL
}

@Entity(
    tableName = "photo_metadata",
    indices = [Index(value = ["sourceUri"])]
)
data class PhotoMetadata(
    @PrimaryKey(autoGenerate = true)
    val photoId: Long = 0,
    /** Original picker URI string ([Uri.toString]); used to skip re-imports of the same media item. */
    val sourceUri: String? = null,
    val filePath: String,
    val cropX: Float,
    val cropY: Float,
    val cropWidth: Float,
    val cropHeight: Float,
    val detectedSubjectType: SubjectType,
    val cropMethod: CropMethod,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromSubjectType(value: SubjectType): String = value.name

    @TypeConverter
    fun toSubjectType(value: String): SubjectType = SubjectType.valueOf(value)

    @TypeConverter
    fun fromCropMethod(value: CropMethod): String = value.name

    @TypeConverter
    fun toCropMethod(value: String): CropMethod = CropMethod.valueOf(value)
}
