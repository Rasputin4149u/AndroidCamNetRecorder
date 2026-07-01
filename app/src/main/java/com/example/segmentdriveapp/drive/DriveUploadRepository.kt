package com.example.segmentdriveapp.drive

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import com.example.segmentdriveapp.BuildConfig
import com.example.segmentdriveapp.util.AppLogger

class DriveUploadRepository private constructor(private val context: Context) {
    private val client = OkHttpClient.Builder().build()

    fun uploadFile(accessToken: String, file: File, mimeType: String = "video/mp4"): UploadResult {
        AppLogger.d(TAG, "Starting Drive resumable upload file=[${file.absolutePath}] size=[${file.length()}]")
        val sessionUri = startResumableSession(accessToken, file, mimeType)
        val putRequest = Request.Builder()
            .url(sessionUri)
            .put(file.asRequestBody(mimeType.toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", mimeType)
            .build()

        client.newCall(putRequest).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "Upload PUT failed code=[${response.code}] body=[${response.body?.string()}]")
                throw IllegalStateException("Drive upload failed with HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val fileId = JSONObject(body).optString("id")
            AppLogger.d(TAG, "Drive upload completed fileId=[$fileId]")
            return UploadResult(fileId = fileId, sessionUri = sessionUri)
        }
    }

    private fun startResumableSession(accessToken: String, file: File, mimeType: String): String {
        val metadata = DriveFileMetadata(
            name = file.name,
            parents = listOf(BuildConfig.DRIVE_FOLDER_ID)
        )
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .post(Json.encodeToString(metadata).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", file.length().toString())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "Resumable session start failed code=[${response.code}] body=[${response.body?.string()}]")
                throw IllegalStateException("Failed to start Drive resumable upload: HTTP ${response.code}")
            }
            val location = response.header("Location")
            if (location.isNullOrBlank()) {
                throw IllegalStateException("Drive resumable upload did not return a session URI")
            }
            AppLogger.d(TAG, "Received Drive resumable session URI")
            return location
        }
    }

    @Serializable
    private data class DriveFileMetadata(
        val name: String,
        val parents: List<String>
    )

    data class UploadResult(
        val fileId: String,
        val sessionUri: String
    )

    companion object {
        private const val TAG = "DriveUploadRepo"

        @Volatile
        private var INSTANCE: DriveUploadRepository? = null

        fun initialize(context: Context) {
            get(context)
        }

        fun get(context: Context): DriveUploadRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DriveUploadRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
