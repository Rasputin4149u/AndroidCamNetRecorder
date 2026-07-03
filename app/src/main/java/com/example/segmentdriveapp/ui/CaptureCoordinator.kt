package com.example.segmentdriveapp.ui

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.segmentdriveapp.data.SegmentRepository
import com.example.segmentdriveapp.util.AppLogger
import com.example.segmentdriveapp.work.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

class CaptureCoordinator(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val appScope: CoroutineScope,
    private val onStatus: (String) -> Unit,
    private val onRecordingStateChanged: (Boolean) -> Unit = {}
) {
    private val repository = SegmentRepository.get(context)
    private val cameraExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var activeSegment: ActiveSegmentHandle? = null
    private var rotationJob: Job? = null
    private var sessionId: String = UUID.randomUUID().toString()
    private var segmentIndex: Int = 0
    private var isCameraBound: Boolean = false
    private var isStartingSegment: Boolean = false
    private var shouldBeRecording: Boolean = false

    val isRecording: Boolean
        get() = activeRecording != null

    val isCameraReady: Boolean
        get() = isCameraBound
	private var ProjectFolder: File? = null

	private fun GetProjectFolder(): File {
		if (ProjectFolder == null) {
			ProjectFolder = AppLogger.Initialize(context.applicationContext)
		}
		return ProjectFolder!!
	}

    suspend fun bindCamera() {
        AppLogger.d(TAG, "bindCamera | entering")
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            videoCapture
        )
        isCameraBound = true
        AppLogger.d(TAG, "bindCamera | completed | quality=[HD]")
        onStatus("Camera ready")
        onRecordingStateChanged(isRecording)
    }

    fun startRecording() {
        AppLogger.d(TAG, "startRecording | requested | isCameraBound=[$isCameraBound] | isRecording=[$isRecording] | isStartingSegment=[$isStartingSegment]")
        if (!isCameraBound) {
            onStatus("Camera not ready")
            return
        }
        if (activeRecording != null || isStartingSegment) {
            onStatus("Recording already active")
            return
        }
        shouldBeRecording = true
        sessionId = UUID.randomUUID().toString()
        segmentIndex = 0
        launchStartNewSegment(isFreshSession = true)
        rotationJob = appScope.launch {
            while (true) {
                delay(SEGMENT_DURATION_MS)
                AppLogger.d(TAG, "rotation timer fired | isRecording=[$isRecording]")
                rotateSegment()
            }
        }
    }

    fun stopRecording() {
        AppLogger.d(TAG, "stopRecording | requested | isRecording=[$isRecording]")
        shouldBeRecording = false
        rotationJob?.cancel()
        rotationJob = null
        val recordingToStop = activeRecording
        activeRecording = null
        activeSegment = null
        recordingToStop?.stop()
        onRecordingStateChanged(false)
    }

    private fun rotateSegment() {
        AppLogger.d(TAG, "rotateSegment | requested | isRecording=[$isRecording] | isStartingSegment=[$isStartingSegment]")
        val recordingToRotate = activeRecording ?: run {
            AppLogger.d(TAG, "rotateSegment | skipped because no active recording")
            return
        }
        if (isStartingSegment) {
            AppLogger.d(TAG, "rotateSegment | skipped because next segment already starting")
            return
        }
        activeRecording = null
        activeSegment = null
        recordingToRotate.stop()
        onRecordingStateChanged(false)
        launchStartNewSegment(isFreshSession = false)
    }

    private fun launchStartNewSegment(isFreshSession: Boolean) {
        if (!shouldBeRecording) {
            AppLogger.d(TAG, "launchStartNewSegment | aborted because shouldBeRecording=[false]")
            isStartingSegment = false
            return
        }
        val vc = videoCapture ?: run {
            onStatus("Camera not ready")
            return
        }
        isStartingSegment = true
        segmentIndex += 1
        val currentSegmentIndex = segmentIndex
        val currentSessionId = if (isFreshSession) sessionId else sessionId
        val file = nextSegmentFile(currentSegmentIndex)
        AppLogger.d(TAG, "launchStartNewSegment | sessionId=[$currentSessionId] | segmentIndex=[$currentSegmentIndex] | path=[${file.absolutePath}]")
        onStatus("Starting segment $currentSegmentIndex -> ${file.name}")

        appScope.launch {
            try {
                val dbId = repository.createActiveSegment(
                    sessionId = currentSessionId,
                    segmentIndex = currentSegmentIndex,
                    path = file.absolutePath,
                    durationMs = SEGMENT_DURATION_MS,
                    quality = "720p"
                )
                if (!shouldBeRecording) {
                    repository.markIncomplete(dbId, "Segment start canceled before recorder start")
                    AppLogger.d(TAG, "launchStartNewSegment | canceled after DB create | dbId=[$dbId]")
                    isStartingSegment = false
                    onRecordingStateChanged(false)
                    return@launch
                }
                val handle = ActiveSegmentHandle(
                    dbId = dbId,
                    sessionId = currentSessionId,
                    segmentIndex = currentSegmentIndex,
                    file = file
                )
                startPreparedRecording(vc, handle)
            } catch (t: Throwable) {
                isStartingSegment = false
                AppLogger.e(TAG, "launchStartNewSegment | failed before recording start", t)
                onStatus("Failed to start segment $currentSegmentIndex: ${t.message}")
                onRecordingStateChanged(false)
            }
        }
    }

    private fun startPreparedRecording(
        videoCapture: VideoCapture<Recorder>,
        handle: ActiveSegmentHandle
    ) {
        if (!shouldBeRecording) {
            AppLogger.d(TAG, "startPreparedRecording | aborted because shouldBeRecording=[false] | dbId=[${handle.dbId}]")
            appScope.launch {
                repository.markIncomplete(handle.dbId, "Segment start canceled before recording began")
            }
            isStartingSegment = false
            onRecordingStateChanged(false)
            return
        }

        val output = FileOutputOptions.Builder(handle.file).build()
        val pending: PendingRecording = videoCapture.output.prepareRecording(context, output).withAudioEnabled()
        activeSegment = handle
        activeRecording = pending.start(cameraExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isStartingSegment = false
                    AppLogger.d(TAG, "onEvent | start | dbId=[${handle.dbId}] | segmentIndex=[${handle.segmentIndex}]")
                    onStatus("Recording segment ${handle.segmentIndex}")
                    onRecordingStateChanged(true)
                }
                is VideoRecordEvent.Finalize -> {
                    handleFinalize(handle, event)
                }
            }
        }
    }

    private fun handleFinalize(handle: ActiveSegmentHandle, event: VideoRecordEvent.Finalize) {
        isStartingSegment = false
        if (event.hasError()) {
            AppLogger.e(
                TAG,
                "handleFinalize | failed | dbId=[${handle.dbId}] | segmentIndex=[${handle.segmentIndex}] | code=[${event.error}] | message=[${event.cause?.message}]",
                event.cause
            )
            onStatus("Segment failed: ${event.cause?.message ?: event.error}")
            appScope.launch {
                repository.markIncomplete(handle.dbId, event.cause?.message ?: "Finalize error ${event.error}")
            }
            return
        }

        AppLogger.d(
            TAG,
            "handleFinalize | sealed | dbId=[${handle.dbId}] | segmentIndex=[${handle.segmentIndex}] | path=[${handle.file.absolutePath}] | size=[${handle.file.length()}]"
        )
        onStatus("Segment sealed -> queued for upload: ${handle.file.name}")
        appScope.launch {
            repository.markSealedPendingUpload(handle.dbId)
            UploadWorker.enqueue(context, handle.dbId)
        }
    }

    private fun nextSegmentFile(index: Int): File {
		val ProjectFolder = GetProjectFolder()
		return File(ProjectFolder, "${sessionId}_segment_${index.toString().padStart(3, '0')}.mp4")
	}

    private data class ActiveSegmentHandle(
        val dbId: Long,
        val sessionId: String,
        val segmentIndex: Int,
        val file: File
    )

    companion object {
        private const val TAG = "CaptureCoordinator"
        const val SEGMENT_DURATION_MS = 5 * 60 * 1000L
    }
}
