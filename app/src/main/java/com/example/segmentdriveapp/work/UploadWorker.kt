package com.example.segmentdriveapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.segmentdriveapp.data.SegmentRepository
import com.example.segmentdriveapp.drive.DriveAuthManager
import com.example.segmentdriveapp.drive.DriveUploadRepository
import com.example.segmentdriveapp.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val repo = SegmentRepository.get(appContext)
    private val auth = DriveAuthManager(appContext)
    private val drive = DriveUploadRepository.get(appContext)

    override suspend fun doWork(): Result {
        val SegmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        AppLogger.d(TAG, "doWork | input segmentId=[$SegmentId]")

        if (SegmentId <= 0L) {
            AppLogger.d(TAG, "doWork | output Result.failure invalid segment id")
            return Result.failure()
        }

        val SegmentEntityObject = repo.getById(SegmentId)
        if (SegmentEntityObject == null) {
            AppLogger.d(TAG, "doWork | output Result.failure missing SegmentEntity for segmentId=[$SegmentId]")
            return Result.failure()
        }

        val AccessToken = auth.getCachedAccessToken()
        AppLogger.d(TAG, "doWork | input cachedAccessToken=[$AccessToken]")

        if (AccessToken.isNullOrBlank()) {
            repo.markFailed(SegmentId, "Drive authorization expired. Re-open app to sign in again.")
            AppLogger.d(TAG, "doWork | output Result.failure missing access token segmentId=[$SegmentId]")
            return Result.failure()
        }

        val SegmentFilePath = SegmentEntityObject.localPath
        val SegmentFile = File(SegmentFilePath)
        val SegmentFileName = SegmentFile.name
        val SegmentFileSizeBytes = SegmentFile.length()
        val SegmentFileExtensionName = SegmentFile.extension
        val SegmentCreatedAtText = SimpleDateFormat(
            "[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]",
            Locale.US
        ).format(Date(SegmentEntityObject.createdAtUtcMs))

        AppLogger.d(
            TAG,
            "doWork | input segmentIndex=[${SegmentEntityObject.segmentIndex}] uploadState=[${SegmentEntityObject.uploadState}] fileName=[$SegmentFileName] filePath=[$SegmentFilePath] fileSizeBytes=[$SegmentFileSizeBytes] fileExtensionName=[$SegmentFileExtensionName] createdAt=[$SegmentCreatedAtText]"
        )

        return try {
            repo.markUploading(SegmentId)
            AppLogger.d(TAG, "doWork | output repo.markUploading done segmentId=[$SegmentId]")

            if (!SegmentFile.exists()) {
                repo.markFailed(SegmentId, "Segment file missing on disk")
                AppLogger.d(TAG, "doWork | output Result.failure file missing fileName=[$SegmentFileName] filePath=[$SegmentFilePath]")
                return Result.failure()
            }

            AppLogger.d(
                TAG,
                "doWork | output DriveUploadRepository.uploadFile requested accessToken=[$AccessToken] fileName=[$SegmentFileName] fileSizeBytes=[$SegmentFileSizeBytes] fileExtensionName=[$SegmentFileExtensionName]"
            )
            val UploadResultObject = drive.uploadFile(AccessToken, SegmentFile)
            repo.markUploaded(SegmentId, UploadResultObject.fileId)
            AppLogger.d(
                TAG,
                "doWork | output repo.markUploaded done segmentId=[$SegmentId] driveFileId=[${UploadResultObject.fileId}] sessionUri=[${UploadResultObject.sessionUri}]"
            )
            AppLogger.d(TAG, "doWork | output Result.success segmentId=[$SegmentId]")
            Result.success()
        } catch (UploadError: Throwable) {
            repo.markFailed(SegmentId, UploadError.message ?: "Unknown upload error")
            AppLogger.e(TAG, "doWork | output Result.retry upload failed segmentId=[$SegmentId]", UploadError)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val KEY_SEGMENT_ID = "segment_id"

        fun enqueue(context: Context, segmentId: Long) {
            AppLogger.d(TAG, "enqueue | input segmentId=[$segmentId]")

            val ConstraintsObject = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            AppLogger.d(TAG, "enqueue | output Constraints.Builder requiredNetworkType=[CONNECTED]")

            val UploadRequestObject = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(ConstraintsObject)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(Data.Builder().putLong(KEY_SEGMENT_ID, segmentId).build())
                .build()

            AppLogger.d(
                TAG,
                "enqueue | output OneTimeWorkRequest built workId=[${UploadRequestObject.id}] segmentId=[$segmentId]"
            )

            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload-segment-$segmentId",
                ExistingWorkPolicy.KEEP,
                UploadRequestObject
            )

            AppLogger.d(
                TAG,
                "enqueue | output WorkManager.enqueueUniqueWork uniqueName=[upload-segment-$segmentId] existingWorkPolicy=[KEEP] workId=[${UploadRequestObject.id}]"
            )
        }
    }
}
