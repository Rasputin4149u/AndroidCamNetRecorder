package com.example.segmentdriveapp.ui

import android.content.Context
import com.example.segmentdriveapp.data.SegmentRepository
import com.example.segmentdriveapp.util.AppLogger
import com.example.segmentdriveapp.work.UploadWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecoveryBootstrap(private val context: Context) {
    private val repository = SegmentRepository.get(context)

    suspend fun resumePendingUploads(onStatus: (String) -> Unit) {
        val PendingSegmentEntityArray = repository.getPendingUploads()
        AppLogger.d(TAG, "resumePendingUploads | input pendingCount=[${PendingSegmentEntityArray.size}]")

        if (PendingSegmentEntityArray.isEmpty()) {
            AppLogger.d(TAG, "resumePendingUploads | output no pending uploads")
            onStatus("Recovery scan: no pending uploads")
            return
        }

        val PendingSegmentNameArray = PendingSegmentEntityArray.map { File(it.localPath).name }
        AppLogger.d(TAG, "resumePendingUploads | input pendingFileNameArray=[$PendingSegmentNameArray]")

        var RequeuedCount = 0
        var MissingCount = 0

        PendingSegmentEntityArray.forEach { PendingSegmentEntity ->
            val SegmentFilePath = PendingSegmentEntity.localPath
            val SegmentFile = File(SegmentFilePath)
            val SegmentFileName = SegmentFile.name

            AppLogger.d(
                TAG,
                "resumePendingUploads | input segmentId=[${PendingSegmentEntity.id}] segmentIndex=[${PendingSegmentEntity.segmentIndex}] uploadState=[${PendingSegmentEntity.uploadState}] fileName=[$SegmentFileName] filePath=[$SegmentFilePath]"
            )

            if (!SegmentFile.exists()) {
                MissingCount += 1
                AppLogger.d(
                    TAG,
                    "resumePendingUploads | output File.exists false fileName=[$SegmentFileName] filePath=[$SegmentFilePath]"
                )
                repository.markFailed(PendingSegmentEntity.id, "Segment file missing on disk during recovery scan")
                return@forEach
            }

            val SegmentFileSizeBytes = SegmentFile.length()
            val SegmentFileExtensionName = SegmentFile.extension
            val SegmentFileCreatedAtText = SimpleDateFormat(
                "[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]",
                Locale.US
            ).format(Date(SegmentFile.lastModified()))

            AppLogger.d(
                TAG,
                "resumePendingUploads | input existingFileName=[$SegmentFileName] fileSizeBytes=[$SegmentFileSizeBytes] fileExtensionName=[$SegmentFileExtensionName] fileCreatedAt=[$SegmentFileCreatedAtText]"
            )

            UploadWorker.enqueue(context, PendingSegmentEntity.id)
            AppLogger.d(
                TAG,
                "resumePendingUploads | output UploadWorker.enqueue requested segmentId=[${PendingSegmentEntity.id}] fileName=[$SegmentFileName]"
            )
            RequeuedCount += 1
        }

        AppLogger.d(
            TAG,
            "resumePendingUploads | output requeuedCount=[$RequeuedCount] missingCount=[$MissingCount]"
        )
        onStatus("Recovery scan: re-queued $RequeuedCount pending segment(s); missing files: $MissingCount")
    }

    companion object {
        private const val TAG = "RecoveryBootstrap"
    }
}
