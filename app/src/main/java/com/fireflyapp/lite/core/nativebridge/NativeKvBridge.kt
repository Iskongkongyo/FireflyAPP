package com.fireflyapp.lite.core.nativebridge

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import com.fireflyapp.lite.core.rule.UrlMatcher
import com.fireflyapp.lite.data.model.NATIVE_KV_STORAGE_MODE_PERSISTENT
import com.fireflyapp.lite.data.model.NATIVE_KV_STORAGE_MODE_SESSION

class NativeKvBridge(
    private val contextProvider: () -> Context?,
    private val trustedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?,
    private val storageScopeProvider: () -> String?,
    private val storageModeProvider: () -> String? = { null }
) {
    @JavascriptInterface
    fun get(namespace: String?, key: String?): String? {
        val access = resolveAccess() ?: return null
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return null
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return null
        return access.getString(storageKey(access.scope, safeNamespace, safeKey))
    }

    @JavascriptInterface
    fun set(namespace: String?, key: String?, value: String?): Boolean {
        val access = resolveAccess() ?: return false
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return false
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return false
        val safeValue = sanitizeValue(value) ?: return false
        return access.putString(storageKey(access.scope, safeNamespace, safeKey), safeValue)
    }

    @JavascriptInterface
    fun remove(namespace: String?, key: String?): Boolean {
        val access = resolveAccess() ?: return false
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return false
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return false
        return access.remove(storageKey(access.scope, safeNamespace, safeKey))
    }

    @JavascriptInterface
    fun clearNamespace(namespace: String?): Int {
        val access = resolveAccess() ?: return 0
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return 0
        val keyPrefix = storageNamespacePrefix(access.scope, safeNamespace)
        if (access.storageMode == NATIVE_KV_STORAGE_MODE_SESSION) {
            return NativeKvSessionStorage.removeByPrefix(access.scope, keyPrefix)
        }
        val preferences = access.preferences ?: return 0
        val keysToRemove = preferences.all.keys.filter { it.startsWith(keyPrefix) }
        if (keysToRemove.isEmpty()) {
            return 0
        }
        val editor = preferences.edit()
        keysToRemove.forEach(editor::remove)
        return if (editor.commit()) keysToRemove.size else 0
    }

    private fun resolveAccess(): AccessContext? {
        val pageUri = currentPageUri()
        if (pageUri == null || !isTrustedPage(pageUri)) {
            Log.w(
                TAG,
                "Native KV access denied: pageUrl=${currentPageUrlProvider()} trustedHosts=${trustedHostsProvider()}"
            )
            return null
        }

        val scope = normalizeNativeKvStorageScope(storageScopeProvider())
        val storageMode = resolveStorageMode()
        val preferences = if (storageMode == NATIVE_KV_STORAGE_MODE_PERSISTENT) {
            val context = contextProvider()?.applicationContext ?: return null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            null
        }
        return AccessContext(
            storageMode = storageMode,
            preferences = preferences,
            scope = scope
        )
    }

    private fun isTrustedPage(pageUri: Uri): Boolean {
        val scheme = pageUri.scheme.orEmpty().lowercase()
        if (scheme !in setOf("http", "https")) {
            return false
        }

        val trustedHosts = trustedHostsProvider()
            .mapNotNull(::normalizeTrustedHostRule)
            .distinct()

        if (trustedHosts.isEmpty()) {
            return false
        }

        return UrlMatcher.isHostAllowed(pageUri, trustedHosts)
    }

    private fun currentPageUri(): Uri? {
        return runCatching { Uri.parse(currentPageUrlProvider().orEmpty()) }.getOrNull()
    }

    private fun resolveStorageMode(): String {
        return when (storageModeProvider()?.trim()?.lowercase()) {
            NATIVE_KV_STORAGE_MODE_SESSION -> NATIVE_KV_STORAGE_MODE_SESSION
            else -> NATIVE_KV_STORAGE_MODE_PERSISTENT
        }
    }

    private fun sanitizeSegment(value: String?, maxLength: Int): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed.length > maxLength) {
            return null
        }
        return trimmed
    }

    private fun sanitizeValue(value: String?): String? {
        if (value == null || value.length > MAX_VALUE_LENGTH) {
            return null
        }
        return value
    }

    private fun normalizeTrustedHostRule(candidate: String): String? {
        var normalized = candidate.trim().lowercase()
        if (normalized.isBlank()) {
            return null
        }
        if (normalized == "*") {
            return normalized
        }
        if ("://" in normalized) {
            normalized = normalized.substringAfter("://")
        }
        normalized = normalized
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (normalized.startsWith("*.")) {
            val wildcardBase = normalized.removePrefix("*.").substringBefore(':').trim()
            return wildcardBase.takeIf { it.isNotBlank() }?.let { "*.$it" }
        }
        normalized = normalized.substringBefore(':').trim()
        return normalized.takeIf { it.isNotBlank() }
    }

    // The scope and namespace prefixes keep preview projects isolated while sharing data across domains.
    private fun storageKey(scope: String, namespace: String, key: String): String {
        return "${Uri.encode(scope)}|${Uri.encode(namespace)}|${Uri.encode(key)}"
    }

    private fun storageNamespacePrefix(scope: String, namespace: String): String {
        return "${Uri.encode(scope)}|${Uri.encode(namespace)}|"
    }

    private fun AccessContext.getString(key: String): String? {
        return if (storageMode == NATIVE_KV_STORAGE_MODE_SESSION) {
            NativeKvSessionStorage.get(scope, key)
        } else {
            preferences?.getString(key, null)
        }
    }

    private fun AccessContext.putString(key: String, value: String): Boolean {
        return if (storageMode == NATIVE_KV_STORAGE_MODE_SESSION) {
            NativeKvSessionStorage.put(scope, key, value)
        } else {
            preferences?.edit()?.putString(key, value)?.commit() == true
        }
    }

    private fun AccessContext.remove(key: String): Boolean {
        return if (storageMode == NATIVE_KV_STORAGE_MODE_SESSION) {
            NativeKvSessionStorage.remove(scope, key)
        } else {
            preferences?.edit()?.remove(key)?.commit() == true
        }
    }

    private data class AccessContext(
        val storageMode: String,
        val preferences: SharedPreferences?,
        val scope: String
    )

    private companion object {
        const val TAG = "NativeKvBridge"
        const val PREFS_NAME = "firefly_native_kv"
        const val MAX_NAMESPACE_LENGTH = 64
        const val MAX_KEY_LENGTH = 128
        const val MAX_VALUE_LENGTH = 65_536
    }
}

internal object NativeKvSessionStorage {
    private val lock = Any()
    private val valuesByScope = mutableMapOf<String, MutableMap<String, String>>()

    fun get(scope: String, key: String): String? {
        return synchronized(lock) {
            valuesByScope[scope]?.get(key)
        }
    }

    fun put(scope: String, key: String, value: String): Boolean {
        synchronized(lock) {
            valuesByScope.getOrPut(scope) { mutableMapOf() }[key] = value
        }
        return true
    }

    fun remove(scope: String, key: String): Boolean {
        synchronized(lock) {
            val values = valuesByScope[scope] ?: return true
            values.remove(key)
            if (values.isEmpty()) {
                valuesByScope.remove(scope)
            }
        }
        return true
    }

    fun removeByPrefix(scope: String, keyPrefix: String): Int {
        return synchronized(lock) {
            val values = valuesByScope[scope] ?: return@synchronized 0
            val keysToRemove = values.keys.filter { it.startsWith(keyPrefix) }
            keysToRemove.forEach(values::remove)
            if (values.isEmpty()) {
                valuesByScope.remove(scope)
            }
            keysToRemove.size
        }
    }

    fun clearScope(scope: String?) {
        synchronized(lock) {
            valuesByScope.remove(normalizeNativeKvStorageScope(scope))
        }
    }
}

private const val DEFAULT_NATIVE_KV_STORAGE_SCOPE = "standalone"
private const val MAX_NATIVE_KV_STORAGE_SCOPE_LENGTH = 96

private fun normalizeNativeKvStorageScope(scope: String?): String {
    return scope
        ?.trim()
        ?.take(MAX_NATIVE_KV_STORAGE_SCOPE_LENGTH)
        .orEmpty()
        .ifBlank { DEFAULT_NATIVE_KV_STORAGE_SCOPE }
}
