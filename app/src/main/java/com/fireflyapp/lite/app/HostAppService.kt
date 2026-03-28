package com.fireflyapp.lite.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class HostAppService(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val preferences = HostAppPreferences(context)

    suspend fun loadStartupPrompts(): HostAppStartupPrompts = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Startup prompts skipped: network unavailable")
            return@withContext HostAppStartupPrompts()
        }

        val currentVersionCode = currentVersionCode()
        val currentVersionName = currentVersionName()
        val updatePrompt = fetchWithRetry(
            url = AppConfig.UPDATE_API_URL,
            serializer = HostAppUpdateResponse.serializer()
        )?.toPrompt(currentVersionCode, currentVersionName)

        val noticePrompt = fetchWithRetry(
            url = AppConfig.NOTIFICATION_API_URL,
            serializer = HostAppNoticeResponse.serializer()
        )?.toPrompt()
            ?.takeIf { prompt ->
                !prompt.showOnce || !preferences.hasSeenNotice(prompt.noticeKey)
            }

        HostAppStartupPrompts(
            updatePrompt = updatePrompt,
            noticePrompt = noticePrompt
        )
    }

    fun markNoticeSeen(noticeKey: String) {
        if (noticeKey.isBlank()) {
            return
        }
        preferences.markNoticeSeen(noticeKey)
    }

    private fun currentVersionCode(): Long {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun currentVersionName(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName.orEmpty()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun <T> fetchWithRetry(
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>
    ): T? {
        val target = url.trim()
        if (target.isBlank()) {
            return null
        }

        repeat(AppConfig.HOST_API_MAX_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Fetching host prompt JSON: url=$target attempt=${attempt + 1}/${AppConfig.HOST_API_MAX_ATTEMPTS}")
                return fetchJson(target, serializer)
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Host prompt fetch failed: url=$target attempt=${attempt + 1}/${AppConfig.HOST_API_MAX_ATTEMPTS} error=${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable
                )
                val shouldRetry = attempt + 1 < AppConfig.HOST_API_MAX_ATTEMPTS && isNetworkAvailable()
                if (!shouldRetry) {
                    return null
                }
            }
        }
        return null
    }

    private fun <T> fetchJson(
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>
    ): T {
        val userAgent = buildHostApiUserAgent()
        val acceptLanguage = buildAcceptLanguageHeader()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = AppConfig.HOST_API_CONNECT_TIMEOUT_MS
            readTimeout = AppConfig.HOST_API_READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", userAgent)
        }
        Log.d(
            TAG,
            "Host prompt request headers: url=$url userAgent=$userAgent acceptLanguage=$acceptLanguage"
        )
        return connection.useAndDisconnect { http ->
            val responseCode = http.responseCode
            val contentType = http.contentType.orEmpty()
            if (responseCode !in 200..299) {
                val errorBodyPreview = readResponsePreview(http.errorStream)
                Log.w(
                    TAG,
                    "Host prompt HTTP failure: url=$url code=$responseCode contentType=$contentType bodyPreview=$errorBodyPreview"
                )
                throw IOException("HTTP $responseCode")
            }
            val body = http.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Log.d(
                TAG,
                "Host prompt HTTP success: url=$url code=$responseCode contentType=$contentType bodyPreview=${body.previewForLog()}"
            )
            runCatching {
                json.decodeFromString(serializer, body)
            }.getOrElse { throwable ->
                Log.e(
                    TAG,
                    "Host prompt JSON parse failed: url=$url contentType=$contentType bodyPreview=${body.previewForLog()}",
                    throwable
                )
                throw throwable
            }
        }
    }

    private fun buildHostApiUserAgent(): String {
        return "FireflyAPP/${currentVersionName().ifBlank { "unknown" }}"
    }

    private fun buildAcceptLanguageHeader(): String {
        val locale = Locale.getDefault()
        val primary = locale.toLanguageTag().ifBlank { "en-US" }
        val language = locale.language.takeIf { it.isNotBlank() } ?: "en"
        return "$primary,$language;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    private fun <T> HttpURLConnection.useAndDisconnect(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private fun readResponsePreview(stream: java.io.InputStream?): String {
        if (stream == null) {
            return "<empty>"
        }
        return runCatching {
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.previewForLog()
        }.getOrDefault("<unreadable>")
    }

    private fun String.previewForLog(maxLength: Int = 220): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) {
            return "<empty>"
        }
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength) + "..."
        }
    }

    @Serializable
    private data class HostAppUpdateResponse(
        val version: String = "",
        val versionCode: Int = 0,
        val is_force: Int = 0,
        val downloadUrl: String = "",
        val changelog: String = ""
    ) {
        fun toPrompt(currentVersionCode: Long, currentVersionName: String): HostAppUpdatePrompt? {
            if (versionCode.toLong() <= currentVersionCode) {
                return null
            }
            return HostAppUpdatePrompt(
                version = version.trim().ifBlank { versionCode.toString() },
                versionCode = versionCode,
                currentVersionName = currentVersionName.ifBlank { currentVersionCode.toString() },
                currentVersionCode = currentVersionCode,
                isForce = is_force != 0,
                downloadUrl = downloadUrl.trim(),
                changelog = changelog.trim()
            )
        }
    }

    @Serializable
    private data class HostAppNoticeResponse(
        val hasNotice: Boolean = false,
        val title: String = "",
        val content: String = "",
        val showOnce: Boolean = true,
        val noticeId: String = ""
    ) {
        fun toPrompt(): HostAppNoticePrompt? {
            if (!hasNotice) {
                return null
            }
            val resolvedTitle = title.trim().ifBlank { "Notice" }
            val resolvedContent = content.trim()
            val resolvedKey = noticeId.trim().ifBlank {
                sha256("$resolvedTitle|$resolvedContent")
            }
            return HostAppNoticePrompt(
                title = resolvedTitle,
                content = resolvedContent,
                showOnce = showOnce,
                noticeKey = resolvedKey
            )
        }
    }

    companion object {
        private const val TAG = "HostAppService"

        private fun sha256(value: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

data class HostAppStartupPrompts(
    val updatePrompt: HostAppUpdatePrompt? = null,
    val noticePrompt: HostAppNoticePrompt? = null
)

data class HostAppUpdatePrompt(
    val version: String,
    val versionCode: Int,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val isForce: Boolean,
    val downloadUrl: String,
    val changelog: String
)

data class HostAppNoticePrompt(
    val title: String,
    val content: String,
    val showOnce: Boolean,
    val noticeKey: String
)

private class HostAppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasSeenNotice(noticeKey: String): Boolean {
        if (noticeKey.isBlank()) {
            return false
        }
        return preferences.getBoolean("$NOTICE_KEY_PREFIX$noticeKey", false)
    }

    fun markNoticeSeen(noticeKey: String) {
        if (noticeKey.isBlank()) {
            return
        }
        preferences.edit().putBoolean("$NOTICE_KEY_PREFIX$noticeKey", true).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "host_app_preferences"
        private const val NOTICE_KEY_PREFIX = "seen_notice_"
    }
}
