package com.example.segmentdriveapp.data

import androidx.room.TypeConverter
import com.example.segmentdriveapp.model.UploadState

class Converters {
    @TypeConverter
    fun fromUploadState(value: UploadState): String = value.name

    @TypeConverter
    fun toUploadState(value: String): UploadState = UploadState.valueOf(value)
}
