package com.example.segmentdriveapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.segmentdriveapp.model.UploadState

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val segmentIndex: Int,
    val localPath: String,
    val createdAtUtcMs: Long,
    val sealedAtUtcMs: Long?,
    val targetDurationMs: Long,
    val videoQuality: String,
    val uploadState: UploadState,
    val driveFileId: String? = null,
    val warningText: String? = null
)
