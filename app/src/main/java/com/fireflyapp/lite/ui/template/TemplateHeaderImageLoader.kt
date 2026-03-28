package com.fireflyapp.lite.ui.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object TemplateHeaderImageLoader {
    suspend fun loadBitmap(context: Context, source: String): Bitmap? {
        val trimmedSource = source.trim()
        if (trimmedSource.isBlank()) {
            return null
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                when (Uri.parse(trimmedSource).scheme?.lowercase()) {
                    "content", "file", "android.resource" -> {
                        context.contentResolver.openInputStream(Uri.parse(trimmedSource))?.use(BitmapFactory::decodeStream)
                    }

                    "asset" -> {
                        context.assets.open(trimmedSource.removePrefix("asset://"))?.use(BitmapFactory::decodeStream)
                    }

                    "http", "https" -> {
                        URL(trimmedSource).openStream().use(BitmapFactory::decodeStream)
                    }

                    else -> null
                }
            }.getOrNull()
        }
    }
}
