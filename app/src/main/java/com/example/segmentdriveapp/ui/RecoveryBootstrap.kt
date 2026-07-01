package com.example.segmentdriveapp.ui

import android.content.Context
import com.example.segmentdriveapp.data.SegmentRepository
import com.example.segmentdriveapp.util.AppLogger
import com.example.segmentdriveapp.work.UploadWorker
import java.io.File

class RecoveryBootstrap(private val context: Context) {
    private val repository = SegmentRepository.get(context)

    suspend fun resumePendingUploads(onStatus: (String) -> Unit) {
        val pending = repository.getPendingUploads()
        AppLogger.d(TAG, "resumePendingUploads | pendingCount=[${pending.size}]")
        if (pending.isEmpty()) {
            onStatus("Recovery scan: no pending uploads")
            return
        }

        var requeuedCount = 0
        var missingCount = 0
        pending.forEach { entity ->
            val file = File(entity.localPath)
            if (!file.exists()) {
                missingCount += 1
                AppLogger.d(TAG, "resumePendingUploads | missing file for id=[${entity.id}] path=[${entity.localPath}]")
                repository.markFailed(entity.id, "Segment file missing on disk during recovery scan")
                return@forEach
            }
            AppLogger.d(TAG, "resumePendingUploads | re-enqueueing id=[${entity.id}] index=[${entity.segmentIndex}] state=[${entity.uploadState}]")
            UploadWorker.enqueue(context, entity.id)
            requeuedCount += 1
        }
        onStatus("Recovery scan: re-queued $requeuedCount pending segment(s); missing files: $missingCount")
    }

    companion object {
        private const val TAG = "RecoveryBootstrap"
    }
}
