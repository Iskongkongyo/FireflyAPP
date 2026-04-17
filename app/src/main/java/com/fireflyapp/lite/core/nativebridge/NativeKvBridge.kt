package com.fireflyapp.lite.core.nativebridge

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import com.fireflyapp.lite.core.rule.UrlMatcher

class NativeKvBridge(
    private val contextProvider: () -> Context?,
    private val trustedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?,
    private val storageScopeProvider: () -> String?
) {
    @JavascriptInterface
    fun get(namespace: String?, key: String?): String? {
        val access = resolveAccess() ?: return null
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return null
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return null
        return access.preferences.getString(storageKey(access.scope, safeNamespace, safeKey), null)
    }

    @JavascriptInterface
    fun set(namespace: String?, key: String?, value: String?): Boolean {
        val access = resolveAccess() ?: return false
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return false
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return false
        val safeValue = sanitizeValue(value) ?: return false
        return access.preferences.edit()
            .putString(storageKey(access.scope, safeNamespace, safeKey), safeValue)
            .commit()
    }

    @JavascriptInterface
    fun remove(namespace: String?, key: String?): Boolean {
        val access = resolveAccess() ?: return false
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return false
        val safeKey = sanitizeSegment(key, MAX_KEY_LENGTH) ?: return false
        return access.preferences.edit()
            .remove(storageKey(access.scope, safeNamespace, safeKey))
            .commit()
    }

    @JavascriptInterface
    fun clearNamespace(namespace: String?): Int {
        val access = resolveAccess() ?: return 0
        val safeNamespace = sanitizeSegment(namespace, MAX_NAMESPACE_LENGTH) ?: return 0
        val keyPrefix = storageNamespacePrefix(access.scope, safeNamespace)
        val keysToRemove = access.preferences.all.keys.filter { it.startsWith(keyPrefix) }
        if (keysToRemove.isEmpty()) {
            return 0
        }
        val editor = access.preferences.edit()
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

        val context = contextProvider()?.applicationContext ?: return null
        return AccessContext(
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            scope = storageScopeProvider()
                ?.trim()
                ?.take(MAX_SCOPE_LENGTH)
                .orEmpty()
                .ifBlank { DEFAULT_SCOPE }
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

    private data class AccessContext(
        val preferences: SharedPreferences,
        val scope: String
    )

    private companion object {
        const val TAG = "NativeKvBridge"
        const val PREFS_NAME = "firefly_native_kv"
        const val DEFAULT_SCOPE = "standalone"
        const val MAX_SCOPE_LENGTH = 96
        const val MAX_NAMESPACE_LENGTH = 64
        const val MAX_KEY_LENGTH = 128
        const val MAX_VALUE_LENGTH = 65_536
    }
}
