package com.example.segmentdriveapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.example.segmentdriveapp.data.SegmentRepository
import com.example.segmentdriveapp.drive.DriveAuthManager
import com.example.segmentdriveapp.drive.DriveUploadRepository
import com.example.segmentdriveapp.util.AppLogger
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val repo = SegmentRepository.get(appContext)
    private val auth = DriveAuthManager(appContext)
    private val drive = DriveUploadRepository.get(appContext)

    override suspend fun doWork(): Result {
        val segmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        AppLogger.d(TAG, "UploadWorker started segmentId=[$segmentId]")
        if (segmentId <= 0L) return Result.failure()

        val entity = repo.getById(segmentId) ?: return Result.failure()
        val accessToken = auth.getCachedAccessToken()
        if (accessToken.isNullOrBlank()) {
            repo.markFailed(segmentId, "Drive authorization expired. Re-open app to sign in again.")
            AppLogger.d(TAG, "Upload blocked due to missing access token")
            return Result.failure()
        }

        return try {
            repo.markUploading(segmentId)
            val file = File(entity.localPath)
            if (!file.exists()) {
                repo.markFailed(segmentId, "Segment file missing on disk")
                return Result.failure()
            }
            val result = drive.uploadFile(accessToken, file)
            repo.markUploaded(segmentId, result.fileId)
            AppLogger.d(TAG, "UploadWorker finished successfully segmentId=[$segmentId]")
            Result.success()
        } catch (t: Throwable) {
            repo.markFailed(segmentId, t.message ?: "Unknown upload error")
            AppLogger.e(TAG, "UploadWorker failed segmentId=[$segmentId]", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val KEY_SEGMENT_ID = "segment_id"

        fun enqueue(context: Context, segmentId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(Data.Builder().putLong(KEY_SEGMENT_ID, segmentId).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload-segment-$segmentId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
