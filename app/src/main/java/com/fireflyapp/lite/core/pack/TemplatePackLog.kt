package com.fireflyapp.lite.core.pack

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TemplatePackLog {
    fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
