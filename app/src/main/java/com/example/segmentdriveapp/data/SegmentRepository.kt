package com.example.segmentdriveapp.data

import android.content.Context
import com.example.segmentdriveapp.model.UploadState
import com.example.segmentdriveapp.util.AppLogger
import java.io.File

class SegmentRepository private constructor(context: Context) {
    private val AppContext = context.applicationContext
    private val dao = AppDatabase.get(AppContext).segmentDao()
    private val ProjectFolder = AppLogger.Initialize(AppContext)

    init {
        AppLogger.d(TAG, "init | AppLogger initialized for SegmentRepository projectFolder=[${ProjectFolder.absolutePath}]")
    }

    suspend fun createActiveSegment(
        sessionId: String,
        segmentIndex: Int,
        path: String,
        durationMs: Long,
        quality: String
    ): Long {
        AppLogger.d(
            TAG,
            "createActiveSegment | input sessionId=[$sessionId] segmentIndex=[$segmentIndex] path=[$path] durationMs=[$durationMs] quality=[$quality]"
        )

        val InsertedSegmentId = dao.insert(
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

        AppLogger.d(TAG, "createActiveSegment | output dao.insert insertedSegmentId=[$InsertedSegmentId]")
        return InsertedSegmentId
    }

    suspend fun markSealedPendingUpload(id: Long) {
        val ExistingSegmentEntity = dao.getById(id)
        if (ExistingSegmentEntity == null) {
            AppLogger.d(TAG, "markSealedPendingUpload | output dao.getById returned null id=[$id]")
            return
        }

        val SegmentFileName = File(ExistingSegmentEntity.localPath).name
        AppLogger.d(
            TAG,
            "markSealedPendingUpload | input id=[$id] oldState=[${ExistingSegmentEntity.uploadState}] fileName=[$SegmentFileName] filePath=[${ExistingSegmentEntity.localPath}]"
        )

        val UpdatedSegmentEntity = ExistingSegmentEntity.copy(
            sealedAtUtcMs = System.currentTimeMillis(),
            uploadState = UploadState.SEALED_PENDING_UPLOAD,
            warningText = null
        )

        dao.update(UpdatedSegmentEntity)
        AppLogger.d(
            TAG,
            "markSealedPendingUpload | output dao.update newState=[${UpdatedSegmentEntity.uploadState}] sealedAtUtcMs=[${UpdatedSegmentEntity.sealedAtUtcMs}]"
        )
    }

    suspend fun markIncomplete(id: Long, warning: String) {
        val ExistingSegmentEntity = dao.getById(id)
        if (ExistingSegmentEntity == null) {
            AppLogger.d(TAG, "markIncomplete | output dao.getById returned null id=[$id]")
            return
        }

        val SegmentFileName = File(ExistingSegmentEntity.localPath).name
        AppLogger.d(
            TAG,
            "markIncomplete | input id=[$id] oldState=[${ExistingSegmentEntity.uploadState}] fileName=[$SegmentFileName] warning=[$warning]"
        )

        val UpdatedSegmentEntity = ExistingSegmentEntity.copy(
            uploadState = UploadState.INCOMPLETE,
            warningText = warning
        )

        dao.update(UpdatedSegmentEntity)
        AppLogger.d(
            TAG,
            "markIncomplete | output dao.update newState=[${UpdatedSegmentEntity.uploadState}] warning=[${UpdatedSegmentEntity.warningText}]"
        )
    }

    suspend fun markUploading(id: Long) {
        val ExistingSegmentEntity = dao.getById(id)
        if (ExistingSegmentEntity == null) {
            AppLogger.d(TAG, "markUploading | output dao.getById returned null id=[$id]")
            return
        }

        val SegmentFileName = File(ExistingSegmentEntity.localPath).name
        AppLogger.d(
            TAG,
            "markUploading | input id=[$id] oldState=[${ExistingSegmentEntity.uploadState}] fileName=[$SegmentFileName] filePath=[${ExistingSegmentEntity.localPath}]"
        )

        val UpdatedSegmentEntity = ExistingSegmentEntity.copy(
            uploadState = UploadState.UPLOADING,
            warningText = null
        )

        dao.update(UpdatedSegmentEntity)
        AppLogger.d(
            TAG,
            "markUploading | output dao.update newState=[${UpdatedSegmentEntity.uploadState}]"
        )
    }

    suspend fun markUploaded(id: Long, driveFileId: String) {
        val ExistingSegmentEntity = dao.getById(id)
        if (ExistingSegmentEntity == null) {
            AppLogger.d(TAG, "markUploaded | output dao.getById returned null id=[$id]")
            return
        }

        val SegmentFileName = File(ExistingSegmentEntity.localPath).name
        AppLogger.d(
            TAG,
            "markUploaded | input id=[$id] oldState=[${ExistingSegmentEntity.uploadState}] fileName=[$SegmentFileName] driveFileId=[$driveFileId]"
        )

        val UpdatedSegmentEntity = ExistingSegmentEntity.copy(
            uploadState = UploadState.UPLOADED,
            driveFileId = driveFileId,
            warningText = null
        )

        dao.update(UpdatedSegmentEntity)
        AppLogger.d(
            TAG,
            "markUploaded | output dao.update newState=[${UpdatedSegmentEntity.uploadState}] driveFileId=[${UpdatedSegmentEntity.driveFileId}]"
        )
    }

    suspend fun markFailed(id: Long, warning: String) {
        val ExistingSegmentEntity = dao.getById(id)
        if (ExistingSegmentEntity == null) {
            AppLogger.d(TAG, "markFailed | output dao.getById returned null id=[$id]")
            return
        }

        val SegmentFileName = File(ExistingSegmentEntity.localPath).name
        AppLogger.d(
            TAG,
            "markFailed | input id=[$id] oldState=[${ExistingSegmentEntity.uploadState}] fileName=[$SegmentFileName] warning=[$warning]"
        )

        val UpdatedSegmentEntity = ExistingSegmentEntity.copy(
            uploadState = UploadState.FAILED,
            warningText = warning
        )

        dao.update(UpdatedSegmentEntity)
        AppLogger.d(
            TAG,
            "markFailed | output dao.update newState=[${UpdatedSegmentEntity.uploadState}] warning=[${UpdatedSegmentEntity.warningText}]"
        )
    }

    suspend fun getPendingUploads(): List<SegmentEntity> {
        val PendingStateArray = listOf(UploadState.SEALED_PENDING_UPLOAD, UploadState.FAILED)
        AppLogger.d(TAG, "getPendingUploads | input stateArray=[$PendingStateArray]")

        val PendingSegmentEntityArray = dao.getByStates(PendingStateArray)
        val PendingSegmentFileNameArray = PendingSegmentEntityArray.map { File(it.localPath).name }

        AppLogger.d(
            TAG,
            "getPendingUploads | output pendingCount=[${PendingSegmentEntityArray.size}] pendingFileNameArray=[$PendingSegmentFileNameArray]"
        )
        return PendingSegmentEntityArray
    }

    suspend fun getAll(): List<SegmentEntity> {
        val SegmentEntityArray = dao.getAll()
        AppLogger.d(TAG, "getAll | output count=[${SegmentEntityArray.size}]")
        return SegmentEntityArray
    }

    suspend fun getById(id: Long): SegmentEntity? {
        AppLogger.d(TAG, "getById | input id=[$id]")
        val SegmentEntityObject = dao.getById(id)

        if (SegmentEntityObject == null) {
            AppLogger.d(TAG, "getById | output null id=[$id]")
            return null
        }

        val SegmentFileName = File(SegmentEntityObject.localPath).name
        AppLogger.d(
            TAG,
            "getById | output id=[${SegmentEntityObject.id}] segmentIndex=[${SegmentEntityObject.segmentIndex}] uploadState=[${SegmentEntityObject.uploadState}] fileName=[$SegmentFileName] filePath=[${SegmentEntityObject.localPath}]"
        )
        return SegmentEntityObject
    }

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
