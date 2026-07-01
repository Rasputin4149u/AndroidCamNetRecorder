package com.example.segmentdriveapp.data

import android.content.Context
import com.example.segmentdriveapp.model.UploadState
import com.example.segmentdriveapp.util.AppLogger

class SegmentRepository private constructor(context: Context) {
    private val dao = AppDatabase.get(context).segmentDao()

    suspend fun createActiveSegment(
        sessionId: String,
        segmentIndex: Int,
        path: String,
        durationMs: Long,
        quality: String
    ): Long {
        AppLogger.d(TAG, "Creating ACTIVE segment index=[$segmentIndex] path=[$path]")
        return dao.insert(
            SegmentEntity(
                sessionId = sessionId,
                segmentIndex = segmentIndex,
                localPath = path,
                createdAtUtcMs = System.currentTimeMillis(),
                sealedAtUtcMs = null,
                targetDurationMs = durationMs,
                videoQuality = quality,
                uploadState = UploadState.ACTIVE
            )
        )
    }

    suspend fun markSealedPendingUpload(id: Long) {
        val existing = dao.getById(id) ?: return
        AppLogger.d(TAG, "Marking segment SEALED_PENDING_UPLOAD id=[$id]")
        dao.update(
            existing.copy(
                sealedAtUtcMs = System.currentTimeMillis(),
                uploadState = UploadState.SEALED_PENDING_UPLOAD,
                warningText = null
            )
        )
    }

    suspend fun markIncomplete(id: Long, warning: String) {
        val existing = dao.getById(id) ?: return
        AppLogger.d(TAG, "Marking segment INCOMPLETE id=[$id] warning=[$warning]")
        dao.update(existing.copy(uploadState = UploadState.INCOMPLETE, warningText = warning))
    }

    suspend fun markUploading(id: Long) {
        val existing = dao.getById(id) ?: return
        AppLogger.d(TAG, "Marking segment UPLOADING id=[$id]")
        dao.update(existing.copy(uploadState = UploadState.UPLOADING, warningText = null))
    }

    suspend fun markUploaded(id: Long, driveFileId: String) {
        val existing = dao.getById(id) ?: return
        AppLogger.d(TAG, "Marking segment UPLOADED id=[$id] driveFileId=[$driveFileId]")
        dao.update(existing.copy(uploadState = UploadState.UPLOADED, driveFileId = driveFileId, warningText = null))
    }

    suspend fun markFailed(id: Long, warning: String) {
        val existing = dao.getById(id) ?: return
        AppLogger.d(TAG, "Marking segment FAILED id=[$id] warning=[$warning]")
        dao.update(existing.copy(uploadState = UploadState.FAILED, warningText = warning))
    }

    suspend fun getPendingUploads(): List<SegmentEntity> {
        return dao.getByStates(listOf(UploadState.SEALED_PENDING_UPLOAD, UploadState.FAILED))
    }

    suspend fun getAll(): List<SegmentEntity> = dao.getAll()

    suspend fun getById(id: Long): SegmentEntity? = dao.getById(id)

    companion object {
        private const val TAG = "SegmentRepository"

        @Volatile
        private var INSTANCE: SegmentRepository? = null

        fun get(context: Context): SegmentRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SegmentRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
