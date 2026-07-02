package com.example.segmentdriveapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.segmentdriveapp.databinding.ActivityMainBinding
import com.example.segmentdriveapp.drive.DriveAuthManager
import com.example.segmentdriveapp.util.AppLogger
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var captureCoordinator: CaptureCoordinator
    private lateinit var driveAuthManager: DriveAuthManager
    private lateinit var recoveryBootstrap: RecoveryBootstrap

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val authResult = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)
            driveAuthManager.consumeAuthorizationResult(
                authResult,
                onReady = {
                    appendStatus("Drive authorization ready")
                    lifecycleScope.launch {
                        recoveryBootstrap.resumePendingUploads(::appendStatus)
                    }
                },
                onError = { appendStatus(it) }
            )
        } catch (e: ApiException) {
            AppLogger.e(TAG, "authorizationLauncher | failed", e)
            appendStatus("Drive authorization failed: ${e.message}")
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = REQUIRED_PERMISSIONS.all { grants[it] == true }
        if (allGranted) {
            appendStatus("Permissions granted")
            lifecycleScope.launch {
                captureCoordinator.bindCamera()
                updateControlState()
            }
        } else {
            appendStatus("Required permissions were denied")
            updateControlState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyBottomInsets()
        AppLogger.d(TAG, "onCreate | activity created")

        driveAuthManager = DriveAuthManager(this)
        recoveryBootstrap = RecoveryBootstrap(this)
        captureCoordinator = CaptureCoordinator(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            appScope = lifecycleScope,
            onStatus = { appendStatus(it) },
            onRecordingStateChanged = { updateControlState() }
        )

        binding.authorizeButton.setOnClickListener {
            driveAuthManager.authorize(
                activity = this,
                launcher = authorizationLauncher,
                onReady = {
                    appendStatus("Drive already authorized")
                    lifecycleScope.launch {
                        recoveryBootstrap.resumePendingUploads(::appendStatus)
                    }
                },
                onError = { appendStatus(it) }
            )
        }

        binding.startButton.setOnClickListener {
            captureCoordinator.startRecording()
            updateControlState()
        }

        binding.stopButton.setOnClickListener {
            captureCoordinator.stopRecording()
            updateControlState()
        }

        updateControlState()
        requestMissingPermissionsIfNeeded()
        lifecycleScope.launch {
            val cached = driveAuthManager.getCachedAccessToken()
            if (cached.isNullOrBlank()) {
                appendStatus("Drive warning: sign-in required before uploads can work")
            } else {
                appendStatus("Drive token found in local cache")
                recoveryBootstrap.resumePendingUploads(::appendStatus)
            }
        }
    }

        override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && captureCoordinator.isRecording) {
            AppLogger.d(TAG, "onKeyUp | Volume Up intercepted as Stop trigger")
            appendStatus("Volume Up pressed -> stopping recording")
            captureCoordinator.stopRecording()
            updateControlState()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun applyBottomInsets() {
        val originalLeft = binding.buttonRow.paddingLeft
        val originalTop = binding.buttonRow.paddingTop
        val originalRight = binding.buttonRow.paddingRight
        val originalBottom = binding.buttonRow.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonRow) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                originalLeft,
                originalTop,
                originalRight,
                originalBottom + systemBars.bottom
            )
            insets
        }

        ViewCompat.requestApplyInsets(binding.buttonRow)
    }

    private fun requestMissingPermissionsIfNeeded() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            lifecycleScope.launch {
                captureCoordinator.bindCamera()
                updateControlState()
            }
            return
        }
        permissionsLauncher.launch(missing.toTypedArray())
    }

    private fun updateControlState() {
        val canStart = captureCoordinator.isCameraReady && !captureCoordinator.isRecording
        val canStop = captureCoordinator.isRecording
        binding.startButton.isEnabled = canStart
        binding.stopButton.isEnabled = canStop
        AppLogger.d(
            TAG,
            "updateControlState | isCameraReady=[${captureCoordinator.isCameraReady}] | isRecording=[${captureCoordinator.isRecording}] | canStart=[$canStart] | canStop=[$canStop]"
        )
    }

    private fun appendStatus(message: String) {
        val oldText = binding.statusTextView.text?.toString().orEmpty()
        binding.statusTextView.text = buildString {
            append(oldText)
            if (oldText.isNotBlank()) append("\n")
            append(message)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
