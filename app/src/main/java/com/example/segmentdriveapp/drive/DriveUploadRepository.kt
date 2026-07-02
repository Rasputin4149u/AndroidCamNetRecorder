package com.example.segmentdriveapp.drive

import android.content.Context
import com.example.segmentdriveapp.BuildConfig
import com.example.segmentdriveapp.util.AppLogger
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveUploadRepository private constructor(private val context: Context) {
    private val client = OkHttpClient.Builder().build()

    fun uploadFile(accessToken: String, file: File, mimeType: String = "video/mp4"): UploadResult {
        val CloudDir = BuildConfig.DRIVE_FOLDER_ID
        val FileName = file.name
        val FilePath = file.absolutePath
        val FileSizeBytes = file.length()
        val FileExtensionName = file.extension
        val FileCreatedAtText = SimpleDateFormat(
            "[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]",
            Locale.US
        ).format(Date(file.lastModified()))

        AppLogger.d(
            TAG,
            "uploadFile | input accessToken=[$accessToken] cloud_dir=[$CloudDir] fileName=[$FileName] filePath=[$FilePath] fileSizeBytes=[$FileSizeBytes] fileExtensionName=[$FileExtensionName] fileCreatedAt=[$FileCreatedAtText] mimeType=[$mimeType]"
        )

        val SessionUri = startResumableSession(accessToken, file, mimeType)
        AppLogger.d(TAG, "uploadFile | output startResumableSession sessionUri=[$SessionUri]")

        val PutRequestObject = Request.Builder()
            .url(SessionUri)
            .put(file.asRequestBody(mimeType.toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", mimeType)
            .build()

        AppLogger.d(
            TAG,
            "uploadFile | output OkHttp PUT request built url=[$SessionUri] authorizationHeader=[Bearer $accessToken] contentType=[$mimeType]"
        )

        client.newCall(PutRequestObject).execute().use { PutResponseObject ->
            val PutResponseCode = PutResponseObject.code
            val PutResponseBody = PutResponseObject.body?.string().orEmpty()

            AppLogger.d(
                TAG,
                "uploadFile | output OkHttp PUT response code=[$PutResponseCode] body=[$PutResponseBody]"
            )

            if (!PutResponseObject.isSuccessful) {
                AppLogger.e(
                    TAG,
                    "uploadFile | PUT failed code=[$PutResponseCode] body=[$PutResponseBody]"
                )
                throw IllegalStateException("Drive upload failed with HTTP $PutResponseCode")
            }

            val DriveFileId = JSONObject(PutResponseBody).optString("id")
            AppLogger.d(TAG, "uploadFile | output parsed driveFileId=[$DriveFileId]")
            return UploadResult(fileId = DriveFileId, sessionUri = SessionUri)
        }
    }

    private fun startResumableSession(accessToken: String, file: File, mimeType: String): String {
        val CloudDir = BuildConfig.DRIVE_FOLDER_ID
        val FileName = file.name
        val FilePath = file.absolutePath
        val FileSizeBytes = file.length()
        val FileExtensionName = file.extension
        val FileCreatedAtText = SimpleDateFormat(
            "[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]",
            Locale.US
        ).format(Date(file.lastModified()))

        AppLogger.d(
            TAG,
            "startResumableSession | input accessToken=[$accessToken] cloud_dir=[$CloudDir] fileName=[$FileName] filePath=[$FilePath] fileSizeBytes=[$FileSizeBytes] fileExtensionName=[$FileExtensionName] fileCreatedAt=[$FileCreatedAtText] mimeType=[$mimeType]"
        )

        val MetadataObject = DriveFileMetadata(
            name = file.name,
            parents = listOf(CloudDir)
        )
        val MetadataJson = Json.encodeToString(MetadataObject)

        AppLogger.d(TAG, "startResumableSession | input metadataJson=[$MetadataJson]")

        val SessionStartRequestObject = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .post(MetadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", file.length().toString())
            .build()

        AppLogger.d(
            TAG,
            "startResumableSession | output OkHttp POST request built url=[https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable] authorizationHeader=[Bearer $accessToken] xUploadContentType=[$mimeType] xUploadContentLength=[${file.length()}]"
        )

        client.newCall(SessionStartRequestObject).execute().use { SessionStartResponseObject ->
            val SessionStartResponseCode = SessionStartResponseObject.code
            val SessionStartResponseBody = SessionStartResponseObject.body?.string().orEmpty()
            val SessionLocation = SessionStartResponseObject.header("Location")

            AppLogger.d(
                TAG,
                "startResumableSession | output OkHttp POST response code=[$SessionStartResponseCode] body=[$SessionStartResponseBody] location=[$SessionLocation]"
            )

            if (!SessionStartResponseObject.isSuccessful) {
                AppLogger.e(
                    TAG,
                    "startResumableSession | POST failed code=[$SessionStartResponseCode] body=[$SessionStartResponseBody]"
                )
                throw IllegalStateException("Failed to start Drive resumable upload: HTTP $SessionStartResponseCode")
            }

            if (SessionLocation.isNullOrBlank()) {
                AppLogger.e(TAG, "startResumableSession | location header missing after successful POST")
                throw IllegalStateException("Drive resumable upload did not return a session URI")
            }

            AppLogger.d(TAG, "startResumableSession | output sessionUri=[$SessionLocation]")
            return SessionLocation
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
