package com.example.segmentdriveapp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.segmentdriveapp.data.AppDatabase
import com.example.segmentdriveapp.drive.DriveUploadRepository
import com.example.segmentdriveapp.util.AppLogger

class SegmentDriveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "Application starting")
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()
        )
        AppDatabase.get(this)
        DriveUploadRepository.initialize(this)
        AppLogger.d(TAG, "Application started")
    }

    companion object {
        private const val TAG = "SegmentDriveApp"
    }
}
