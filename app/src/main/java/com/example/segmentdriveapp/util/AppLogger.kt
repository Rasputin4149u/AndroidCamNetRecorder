package com.example.segmentdriveapp.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    const val LogFileName = "Cam_SOS_Recorder.log"

    @Volatile
    private var LogFilePath: String = ""

    private val tsFormat = SimpleDateFormat("[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]", Locale.US)
    private val fileGuard = Any()

    fun Initialize(context: Context) {
        val DocumentsFolder = File(context.filesDir, "CamSOS")
        if (!DocumentsFolder.exists()) {
            DocumentsFolder.mkdirs()
        }

        val LogFile = File(DocumentsFolder, LogFileName)
        LogFilePath = LogFile.absolutePath

        if (!LogFile.exists()) {
            LogFile.createNewFile()
        }
		Log.d("AppLogger.kt", "In Init App Logger")
		
		Log.d("AppLogger.kt", "obj=[${context?.toString() ?: "null"}]")
		Log.d("AppLogger.kt", LogFilePath)
		try {
			File(LogFilePath).appendText("We Reached AppLogger.kt" + System.lineSeparator())
		} catch (AppendMarkerWriteError: Throwable) {
			Log.e(
				"AppLogger",
				"Initialize | failed to write direct marker path=[$LogFilePath]",
				AppendMarkerWriteError
			)
		}
		d("AppLogger", "Logger initialized logFilePath=[$LogFilePath]")
    }

    fun GetLogFilePath(): String {
        return LogFilePath
    }

    fun d(tag: String, message: String) {
        val LogLine = BuildLogLine("DEBUG", message)
        Log.d(tag, LogLine)
        AppendToFile(LogLine)
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        val ThrowableText = if (tr == null) {
            ""
        } else {
            val StringWriterInstance = StringWriter()
            tr.printStackTrace(PrintWriter(StringWriterInstance))
            " | throwable=[${StringWriterInstance}]"
        }

        val LogLine = BuildLogLine("ERROR", "$message$ThrowableText")
        Log.e(tag, LogLine, tr)
        AppendToFile(LogLine)
    }

    private fun BuildLogLine(level: String, message: String): String {
        val Stack = Throwable().stackTrace
        val CallerFrame = Stack.firstOrNull {
            !it.className.contains("AppLogger") &&
                !it.className.startsWith("java.lang.Thread")
        }

        val Timestamp = tsFormat.format(Date())
        val CallerFunction = CallerFrame?.methodName ?: "unknownMethod"
        val CallerLine = CallerFrame?.lineNumber ?: -1

        return "$Timestamp [$level] [$CallerFunction:$CallerLine] $message"
    }

    private fun AppendToFile(logLine: String) {
        synchronized(fileGuard) {
            try {
                if (LogFilePath.isBlank()) {
                    Log.e("AppLogger", "Log file path is blank; skipping file append")
                    return
                }

                File(LogFilePath).appendText(logLine + System.lineSeparator())
            } catch (AppendError: Throwable) {
                Log.e("AppLogger", "Failed to append to log file", AppendError)
            }
        }
    }
}
