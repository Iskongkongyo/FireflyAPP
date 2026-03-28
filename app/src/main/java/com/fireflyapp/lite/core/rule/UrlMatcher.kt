package com.fireflyapp.lite.core.rule

import android.net.Uri
import com.fireflyapp.lite.data.model.MatchRule

object UrlMatcher {
    fun matches(url: String, rule: MatchRule): Boolean {
        val normalizedUrl = url.trim()
        val exact = rule.urlEquals?.takeIf { it.isNotBlank() }?.let { normalizedUrl == it } ?: false
        val startsWith = rule.urlStartsWith?.takeIf { it.isNotBlank() }?.let { normalizedUrl.startsWith(it) } ?: false
        val contains = rule.urlContains?.takeIf { it.isNotBlank() }?.let { normalizedUrl.contains(it) } ?: false
        return exact || startsWith || contains
    }

    fun matchPriority(rule: MatchRule): Int {
        return when {
            !rule.urlEquals.isNullOrBlank() -> 3
            !rule.urlStartsWith.isNullOrBlank() -> 2
            !rule.urlContains.isNullOrBlank() -> 1
            else -> 0
        }
    }

    fun isHostAllowed(uri: Uri, allowedHosts: List<String>): Boolean {
        if (allowedHosts.isEmpty()) {
            return true
        }

        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { candidate ->
            matchesAllowedHost(host, candidate)
        }
    }

    private fun matchesAllowedHost(host: String, candidate: String): Boolean {
        val normalizedCandidate = candidate.trim().lowercase()
        if (normalizedCandidate.isBlank()) {
            return false
        }
        if (normalizedCandidate == "*") {
            return true
        }
        if (normalizedCandidate.startsWith("*.")) {
            val wildcardBase = normalizedCandidate.removePrefix("*.")
            if (wildcardBase.isBlank()) {
                return false
            }
            return host.endsWith(".$wildcardBase")
        }
        return host == normalizedCandidate || host.endsWith(".$normalizedCandidate")
    }
}
