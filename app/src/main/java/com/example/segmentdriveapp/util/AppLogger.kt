package com.example.segmentdriveapp.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val tsFormat = SimpleDateFormat("[dd]:[MM]:[yyyy] - [HH]:[mm]:[ss].[SSS]", Locale.US)

    private fun header(): String {
        val stack = Throwable().stackTrace
        val ste = stack.firstOrNull { !it.className.contains("AppLogger") }
        val ts = tsFormat.format(Date())
        val method = ste?.methodName ?: "unknownMethod"
        val line = ste?.lineNumber ?: -1
        return "$ts [$method:$line]"
    }

    fun d(tag: String, message: String) {
        Log.d(tag, "${header()} $message")
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        Log.e(tag, "${header()} $message", tr)
    }
}
