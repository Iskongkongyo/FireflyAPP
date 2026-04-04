package com.fireflyapp.lite.ui.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.fireflyapp.lite.BuildConfig
import com.fireflyapp.lite.core.icon.ProjectCustomIconReference
import java.io.File

object TemplateRuntimeIconLoader {
    fun resolveDrawable(
        context: Context,
        projectId: String?,
        iconValue: String,
        fallbackId: String
    ): Drawable? {
        ProjectCustomIconReference.relativePathOrNull(iconValue)
            ?.let { relativePath ->
                loadCustomDrawable(
                    context = context,
                    projectId = projectId,
                    relativePath = relativePath
                )?.let { return it }
            }
        return AppCompatResources.getDrawable(
            context,
            TemplateIconCatalog.resolveOrDefault(iconValue, fallbackId)
        )
    }

    private fun loadCustomDrawable(
        context: Context,
        projectId: String?,
        relativePath: String
    ): Drawable? {
        val bitmap = if (BuildConfig.IS_WORKSPACE_HOST_APP) {
            val resolvedProjectId = projectId.orEmpty().trim()
            if (resolvedProjectId.isBlank()) {
                null
            } else {
                val sourceFile = context.filesDir
                    .resolve(PROJECTS_DIR_NAME)
                    .resolve(resolvedProjectId)
                    .resolve(relativePath)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    null
                } else {
                    BitmapFactory.decodeFile(sourceFile.absolutePath)
                }
            }
        } else {
            runCatching {
                context.assets.open(relativePath).use(BitmapFactory::decodeStream)
            }.getOrNull()
        } ?: return null

        return BitmapDrawable(context.resources, bitmap.scaleToIconSize(context)).mutate()
    }

    private fun Bitmap.scaleToIconSize(context: Context): Bitmap {
        val targetSizePx = (context.resources.displayMetrics.density * DEFAULT_ICON_SIZE_DP).toInt()
            .coerceAtLeast(1)
        if (width == targetSizePx && height == targetSizePx) {
            return this
        }
        val scale = minOf(
            targetSizePx.toFloat() / width.coerceAtLeast(1),
            targetSizePx.toFloat() / height.coerceAtLeast(1)
        )
        val drawWidth = (width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val left = ((targetSizePx - drawWidth) / 2f)
        val top = ((targetSizePx - drawHeight) / 2f)
        Canvas(output).drawBitmap(
            this,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        recycle()
        return output
    }

    private const val PROJECTS_DIR_NAME = "projects"
    private const val DEFAULT_ICON_SIZE_DP = 24f
}
