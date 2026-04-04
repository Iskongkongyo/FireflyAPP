package com.fireflyapp.lite.core.icon

object ProjectCustomIconReference {
    const val PREFIX = "custom://"
    const val DIRECTORY = "branding/custom-icons"

    fun create(relativePath: String): String {
        val normalized = sanitizeRelativePath(relativePath)
        return if (normalized.isBlank()) "" else PREFIX + normalized
    }

    fun isCustomReference(value: String?): Boolean {
        return relativePathOrNull(value) != null
    }

    fun relativePathOrNull(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) {
            return null
        }
        return sanitizeRelativePath(trimmed.substring(PREFIX.length))
            .takeIf { it.isNotBlank() }
    }

    fun displayName(value: String?): String {
        return relativePathOrNull(value)
            ?.substringAfterLast('/')
            .orEmpty()
    }

    private fun sanitizeRelativePath(path: String): String {
        val normalized = path.trim()
            .replace('\\', '/')
            .trimStart('/')
        if (normalized.isBlank()) {
            return ""
        }
        if (normalized == ".." || normalized.startsWith("../") || normalized.contains("/../")) {
            return ""
        }
        return normalized
    }
}
