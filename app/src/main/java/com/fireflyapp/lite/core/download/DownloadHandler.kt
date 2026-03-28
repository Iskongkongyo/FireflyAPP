package com.fireflyapp.lite.core.download

import android.content.ContentValues
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fireflyapp.lite.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import android.webkit.MimeTypeMap

class DownloadHandler(
    private val context: Context
) {
    private val blobSessions = ConcurrentHashMap<String, BlobDownloadSession>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    fun guessFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        return normalizeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType)
    }

    fun enqueue(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
        suggestedFileName: String? = null,
        onEvent: ((DownloadEvent) -> Unit)? = null
    ): Result<Unit> {
        return runCatching {
            Thread {
                val result = downloadFile(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    referer = referer,
                    suggestedFileName = suggestedFileName,
                    onEvent = onEvent
                )
                if (result.isFailure) {
                    mainHandler.post {
                        onEvent?.invoke(
                            DownloadEvent.Failure(
                                fileName = preferredFileName(
                                    suggestedFileName = suggestedFileName,
                                    url = url,
                                    contentDisposition = contentDisposition,
                                    mimeType = mimeType
                                ),
                                reason = result.exceptionOrNull()?.message ?: "download failed"
                            )
                        )
                    }
                }
            }.start()
        }.onFailure { throwable ->
            Log.e(TAG, "enqueue failed before start. url=$url mimeType=$mimeType", throwable)
        }
    }

    fun saveBase64Download(
        base64Data: String,
        fileName: String,
        mimeType: String?
    ): Result<Uri> {
        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(bytes, fileName, mimeType)
            } else {
                saveToAppExternalFiles(bytes, fileName)
            }
        }.onFailure { throwable ->
            Log.e(TAG, "saveBase64Download failed. fileName=$fileName mimeType=$mimeType", throwable)
        }
    }

    fun createBlobDownloadSession(
        sessionId: String,
        fileName: String,
        mimeType: String?,
        totalChunks: Int?,
        onEvent: ((DownloadEvent) -> Unit)? = null
    ): Result<Unit> {
        return runCatching {
            val session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createMediaStoreSession(sessionId, fileName, mimeType)
            } else {
                createFileSession(sessionId, fileName)
            }
            val normalizedTotalChunks = totalChunks?.takeIf { it > 0 }
            val sessionWithState = session.copy(
                fileName = session.fileName,
                mimeType = mimeType,
                totalChunks = normalizedTotalChunks,
                notificationId = nextNotificationId(session.fileName),
                onEvent = onEvent
            )
            blobSessions[session.id] = sessionWithState
            ensureDownloadNotificationChannel()
            notifyProgress(
                notificationId = sessionWithState.notificationId,
                fileName = sessionWithState.fileName,
                progressPercent = 0.takeIf { normalizedTotalChunks != null }
            )
            dispatchEvent(onEvent, DownloadEvent.Started(sessionWithState.fileName))
        }.onFailure { throwable ->
            Log.e(TAG, "createBlobDownloadSession failed. fileName=$fileName mimeType=$mimeType", throwable)
        }
    }

    fun appendBase64Chunk(
        sessionId: String,
        base64Chunk: String,
        isLastChunk: Boolean
    ): Result<Unit> {
        return runCatching {
            val session = checkNotNull(blobSessions[sessionId]) { "Missing blob session: $sessionId" }
            val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
            session.outputStream.write(bytes)
            session.downloadedBytes += bytes.size
            session.completedChunks += 1
            val progressPercent = session.totalChunks?.let { total ->
                ((session.completedChunks * 100L) / total).toInt().coerceIn(0, 100)
            }
            if (!isLastChunk && shouldDispatchProgress(progressPercent, session.lastProgressPercent, session.downloadedBytes, null)) {
                session.lastProgressPercent = progressPercent ?: session.lastProgressPercent
                notifyProgress(session.notificationId, session.fileName, progressPercent)
                dispatchEvent(
                    session.onEvent,
                    DownloadEvent.Progress(
                        fileName = session.fileName,
                        downloadedBytes = session.downloadedBytes,
                        totalBytes = null
                    )
                )
            }
            if (isLastChunk) {
                finishBlobDownloadSession(sessionId)
            }
        }.onFailure { throwable ->
            Log.e(TAG, "appendBase64Chunk failed. sessionId=$sessionId isLastChunk=$isLastChunk", throwable)
            abortBlobDownloadSession(sessionId, throwable.message ?: "blob download failed")
        }
    }

    fun abortBlobDownloadSession(sessionId: String, reason: String = "blob download canceled") {
        val session = blobSessions.remove(sessionId) ?: return
        runCatching {
            session.outputStream.close()
        }
        if (session.isMediaStore) {
            runCatching {
                context.contentResolver.delete(session.uri, null, null)
            }
        } else {
            runCatching {
                File(checkNotNull(session.uri.path)).delete()
            }
        }
        notifyFailed(session.notificationId, session.fileName, reason)
        dispatchEvent(session.onEvent, DownloadEvent.Failure(session.fileName, reason))
    }

    private fun saveToMediaStore(
        bytes: ByteArray,
        fileName: String,
        mimeType: String?
    ): Uri {
        val target = createDownloadTarget(fileName, mimeType, pending = false)
        target.outputStream.use { output ->
            output.write(bytes)
            output.flush()
        }
        return target.uri
    }

    private fun saveToAppExternalFiles(
        bytes: ByteArray,
        fileName: String
    ): Uri {
        val target = createDownloadTarget(fileName, null, pending = false)
        target.outputStream.use { output ->
            output.write(bytes)
            output.flush()
        }
        return target.uri
    }

    private fun createMediaStoreSession(
        sessionId: String,
        fileName: String,
        mimeType: String?
    ): BlobDownloadSession {
        val target = createDownloadTarget(fileName, mimeType, pending = true)
        return BlobDownloadSession(
            id = sessionId,
            uri = target.uri,
            outputStream = target.outputStream,
            isMediaStore = target.isMediaStore,
            fileName = target.fileName,
            mimeType = mimeType
        )
    }

    private fun createFileSession(sessionId: String, fileName: String): BlobDownloadSession {
        val target = createDownloadTarget(fileName, null, pending = false)
        return BlobDownloadSession(
            id = sessionId,
            uri = target.uri,
            outputStream = target.outputStream,
            isMediaStore = target.isMediaStore,
            fileName = target.fileName
        )
    }

    private fun finishBlobDownloadSession(sessionId: String) {
        val session = blobSessions.remove(sessionId) ?: return
        session.outputStream.flush()
        session.outputStream.close()
        markDownloadCompleted(session.uri, session.isMediaStore)
        notifyCompleted(session.notificationId, session.fileName, session.uri, session.mimeType)
        dispatchEvent(session.onEvent, DownloadEvent.Success(session.fileName, session.uri))
    }

    private fun downloadFile(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        referer: String?,
        suggestedFileName: String?,
        onEvent: ((DownloadEvent) -> Unit)?
    ): Result<Uri> {
        var connection: HttpURLConnection? = null
        var target: DownloadTarget? = null
        var fileNameForNotification = preferredFileName(
            suggestedFileName = suggestedFileName,
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType
        )
        val notificationId = nextNotificationId(fileNameForNotification)
        return runCatching {
            ensureDownloadNotificationChannel()
            notifyProgress(
                notificationId = notificationId,
                fileName = fileNameForNotification,
                progressPercent = null
            )
            dispatchEvent(onEvent, DownloadEvent.Started(fileNameForNotification))

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "*/*")
                if (!userAgent.isNullOrBlank()) {
                    setRequestProperty("User-Agent", userAgent)
                }
                if (!referer.isNullOrBlank()) {
                    setRequestProperty("Referer", referer)
                    buildOriginHeader(referer)?.let { setRequestProperty("Origin", it) }
                }

                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }

            val responseCode = connection!!.responseCode
            if (responseCode !in 200..299) {
                error("Unexpected HTTP status: $responseCode")
            }

            val finalUrl = connection!!.url.toString()
            val finalMimeType = connection!!.contentType?.substringBefore(";")?.trim().orEmpty()
                .ifBlank { mimeType.orEmpty() }
                .ifBlank { "application/octet-stream" }
            val finalDisposition = connection!!.getHeaderField("Content-Disposition") ?: contentDisposition
            val fileName = preferredFileName(
                suggestedFileName = suggestedFileName,
                url = finalUrl,
                contentDisposition = finalDisposition,
                mimeType = finalMimeType
            )

            target = createDownloadTarget(fileName, finalMimeType, pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            fileNameForNotification = target!!.fileName
            val totalBytes = connection!!.contentLengthLong.takeIf { it > 0L }
            var downloadedBytes = 0L
            var lastProgressPercent = -1
            connection!!.inputStream.use { input ->
                target!!.outputStream.use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val progressPercent = if (totalBytes != null && totalBytes > 0) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        if (shouldDispatchProgress(progressPercent, lastProgressPercent, downloadedBytes, totalBytes)) {
                            lastProgressPercent = progressPercent ?: lastProgressPercent
                            notifyProgress(notificationId, target!!.fileName, progressPercent)
                            dispatchEvent(
                                onEvent,
                                DownloadEvent.Progress(
                                    fileName = target!!.fileName,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
            markDownloadCompleted(target!!.uri, target!!.isMediaStore)
            notifyCompleted(notificationId, target!!.fileName, target!!.uri, finalMimeType)
            dispatchEvent(onEvent, DownloadEvent.Success(fileName = target!!.fileName, uri = target!!.uri))
            Log.d(TAG, "downloadFile success. url=$url finalUrl=$finalUrl fileName=${target!!.fileName}")
            target!!.uri
        }.onFailure { throwable ->
            Log.e(TAG, "downloadFile failed. url=$url mimeType=$mimeType referer=$referer", throwable)
            notifyFailed(notificationId, fileNameForNotification, throwable.message ?: "download failed")
            target?.let { failedTarget ->
                cleanupDownloadTarget(failedTarget)
            }
        }.also {
            connection?.disconnect()
        }
    }

    private fun createDownloadTarget(
        fileName: String,
        mimeType: String?,
        pending: Boolean
    ): DownloadTarget {
        val safeFileName = resolveUniqueFileName(fileName, mimeType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreTarget(safeFileName, mimeType, pending)
        } else {
            createFileTarget(safeFileName)
        }
    }

    private fun createMediaStoreTarget(
        fileName: String,
        mimeType: String?,
        pending: Boolean
    ): DownloadTarget {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            if (pending) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        val outputStream = checkNotNull(resolver.openOutputStream(uri, "w"))
        return DownloadTarget(
            fileName = fileName,
            uri = uri,
            outputStream = outputStream,
            isMediaStore = true
        )
    }

    private fun createFileTarget(fileName: String): DownloadTarget {
        val directory = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, fileName)
        return DownloadTarget(
            fileName = fileName,
            uri = Uri.fromFile(file),
            outputStream = FileOutputStream(file),
            isMediaStore = false
        )
    }

    private fun markDownloadCompleted(uri: Uri, isMediaStore: Boolean) {
        if (isMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun cleanupDownloadTarget(target: DownloadTarget) {
        runCatching {
            target.outputStream.close()
        }
        if (target.isMediaStore) {
            runCatching {
                context.contentResolver.delete(target.uri, null, null)
            }
        } else {
            runCatching {
                File(checkNotNull(target.uri.path)).delete()
            }
        }
    }

    private fun normalizeFileName(fileName: String, mimeType: String?): String {
        val trimmed = fileName.trim().ifBlank { DEFAULT_FILE_NAME }
        val sanitized = trimmed
            .replace(INVALID_FILE_NAME_CHARS, "_")
            .trim()
            .trim('.', ' ')
            .ifBlank { DEFAULT_FILE_NAME }

        if (sanitized.contains('.')) {
            return sanitized
        }

        val extension = mimeType
            ?.substringBefore(';')
            ?.lowercase()
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            .orEmpty()
        return if (extension.isBlank()) sanitized else "$sanitized.$extension"
    }

    private fun preferredFileName(
        suggestedFileName: String?,
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        val normalizedSuggestion = suggestedFileName?.trim().orEmpty()
        return if (normalizedSuggestion.isNotBlank()) {
            normalizeFileName(normalizedSuggestion, mimeType)
        } else {
            guessFileName(url, contentDisposition, mimeType)
        }
    }

    private fun resolveUniqueFileName(fileName: String, mimeType: String?): String {
        val normalized = normalizeFileName(fileName, mimeType)
        val baseName = normalized.substringBeforeLast('.', normalized)
        val extension = normalized.substringAfterLast('.', "")
        var candidate = normalized
        var index = 1
        while (downloadTargetExists(candidate)) {
            candidate = buildString {
                append(baseName)
                append(" (")
                append(index)
                append(")")
                if (extension.isNotBlank()) {
                    append(".")
                    append(extension)
                }
            }
            index += 1
        }
        return candidate
    }

    private fun downloadTargetExists(fileName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } == true
        } else {
            val directory = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
            File(directory, fileName).exists()
        }
    }

    private fun ensureDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun notifyProgress(
        notificationId: Int,
        fileName: String,
        progressPercent: Int?
    ) {
        if (!canPostNotifications()) {
            return
        }

        val text = progressPercent?.let {
            context.getString(R.string.download_progress_with_percent, fileName, it)
        } ?: context.getString(R.string.download_progress_text, fileName)

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(100, progressPercent ?: 0, progressPercent == null)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun notifyCompleted(notificationId: Int, fileName: String, uri: Uri, mimeType: String?) {
        if (!canPostNotifications()) {
            return
        }

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(fileName)
            .setContentText(context.getString(R.string.download_completed_text, fileName))
            .setOngoing(false)
            .setAutoCancel(true)

        buildOpenFilePendingIntent(uri, mimeType)?.let(builder::setContentIntent)
        notificationManager.notify(notificationId, builder.build())
    }

    private fun notifyFailed(notificationId: Int, fileName: String, reason: String) {
        if (!canPostNotifications()) {
            return
        }

        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(fileName)
            .setContentText(context.getString(R.string.download_failed_text, reason))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.download_failed_text, reason)))
            .setOngoing(false)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun nextNotificationId(fileName: String): Int {
        return (fileName.hashCode().toLong() xor System.nanoTime()).toInt()
    }

    private fun buildOpenFilePendingIntent(uri: Uri, mimeType: String?): PendingIntent? {
        val shareableUri = resolveShareableUri(uri) ?: return null
        val resolvedMimeType = resolveMimeTypeForUri(shareableUri, mimeType)
        val packageManager = context.packageManager

        val exactIntent = createOpenFileIntent(shareableUri, resolvedMimeType)
        val genericIntent = createOpenFileIntent(shareableUri, "*/*")
        val exactResolvable = exactIntent.resolveActivity(packageManager) != null
        val genericResolvable = genericIntent.resolveActivity(packageManager) != null

        val finalIntent = when {
            exactResolvable && genericResolvable && resolvedMimeType != "*/*" -> {
                Intent.createChooser(exactIntent, context.getString(R.string.download_open_with)).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(genericIntent))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("", shareableUri)
                }
            }

            exactResolvable -> exactIntent
            genericResolvable -> genericIntent
            else -> context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return null
        }

        return PendingIntent.getActivity(
            context,
            nextNotificationId(shareableUri.toString()),
            finalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenFileIntent(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("", uri)
        }
    }

    private fun resolveShareableUri(uri: Uri): Uri? {
        return when (uri.scheme?.lowercase()) {
            "content" -> uri
            "file" -> {
                val file = uri.path?.let(::File) ?: return null
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            else -> null
        }
    }

    private fun resolveMimeTypeForUri(uri: Uri, fallbackMimeType: String?): String {
        val contentType = context.contentResolver.getType(uri)
        if (!contentType.isNullOrBlank()) {
            return contentType
        }
        if (!fallbackMimeType.isNullOrBlank()) {
            return fallbackMimeType
        }
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).orEmpty().lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun shouldDispatchProgress(
        progressPercent: Int?,
        lastProgressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long?
    ): Boolean {
        return when {
            progressPercent != null -> progressPercent >= lastProgressPercent + 1
            totalBytes == null -> downloadedBytes == 0L || downloadedBytes % UNKNOWN_PROGRESS_STEP_BYTES < BUFFER_SIZE
            else -> false
        }
    }

    private fun dispatchEvent(onEvent: ((DownloadEvent) -> Unit)?, event: DownloadEvent) {
        if (onEvent == null) {
            return
        }
        mainHandler.post {
            onEvent(event)
        }
    }

    private fun buildOriginHeader(referer: String): String? {
        return runCatching {
            val uri = Uri.parse(referer)
            val scheme = uri.scheme.orEmpty()
            val host = uri.host.orEmpty()
            val port = uri.port
            if (scheme.isBlank() || host.isBlank()) {
                null
            } else if (port == -1) {
                "$scheme://$host"
            } else {
                "$scheme://$host:$port"
            }
        }.getOrNull()
    }

    private data class BlobDownloadSession(
        val id: String,
        val uri: Uri,
        val outputStream: OutputStream,
        val isMediaStore: Boolean,
        val fileName: String = DEFAULT_FILE_NAME,
        val mimeType: String? = null,
        val totalChunks: Int? = null,
        val notificationId: Int = 0,
        val onEvent: ((DownloadEvent) -> Unit)? = null,
        var completedChunks: Int = 0,
        var downloadedBytes: Long = 0L,
        var lastProgressPercent: Int = -1
    )

    private data class DownloadTarget(
        val fileName: String,
        val uri: Uri,
        val outputStream: OutputStream,
        val isMediaStore: Boolean
    )

    sealed interface DownloadEvent {
        data class Started(val fileName: String) : DownloadEvent
        data class Progress(
            val fileName: String,
            val downloadedBytes: Long,
            val totalBytes: Long?
        ) : DownloadEvent {
            val progressPercent: Int?
                get() = totalBytes?.takeIf { it > 0L }?.let { ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100) }
        }

        data class Success(val fileName: String, val uri: Uri) : DownloadEvent
        data class Failure(val fileName: String, val reason: String) : DownloadEvent
    }

    private companion object {
        const val TAG = "DownloadHandler"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val BUFFER_SIZE = 16 * 1024
        const val UNKNOWN_PROGRESS_STEP_BYTES = 256 * 1024L
        const val DOWNLOAD_CHANNEL_ID = "firefly_downloads"
        const val DEFAULT_FILE_NAME = "download"
        val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    }
}
