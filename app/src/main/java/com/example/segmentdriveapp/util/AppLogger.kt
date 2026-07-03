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

    fun InitiateLogFile{
		
		val LogFile = File("/CamSOS", LogFileName)
		LogFilePath = LogFile.absolutePath
		if (!LogFile.exists()) {
            LogFile.createNewFile()
        }
	}
	fun Initialize(context: Context) {
        
        InitiateLogFile
		Log.d("AppLogger.kt", "In Init App Logger")
		
		Log.d("AppLogger.kt", "obj=[${context?.toString() ?: "null"}]")
		Log.d("AppLogger.kt", LogFilePath)
		FileWrite("We Reached AppLogger.kt" + System.lineSeparator())
		d("AppLogger", "Logger initialized logFilePath=[$LogFilePath]")
    }

    fun GetLogFilePath(): String {
        return LogFilePath
    }
	
	fun FileWrite(message: String) {
		try {
			File(LogFilePath).appendText(message)
			val Message = "write:" + message + " path=[$LogFilePath] -------Pass"
			Log.d("AppLogger", Message)
		} catch (AppendMarkerWriteError: Throwable) {
			val Message = "write:" + message + " path=[$LogFilePath] -------Fail"
			Log.e("AppLogger", Message, AppendMarkerWriteError)
		}
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
        InitiateLogFile
		synchronized(fileGuard) {
            
			try {
                if (LogFilePath.isBlank()) {
                    Log.e("AppLogger", "Log file path is blank; skipping file append")
                    return
                }
				FileWrite(logLine + System.lineSeparator())
            } catch (AppendError: Throwable) {
                Log.e("AppLogger", "Failed to append to log file", AppendError)
            }
        }
    }
}
