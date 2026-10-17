package com.example.domain.model

/** Normalizes a project-relative path and rejects paths that could escape the project root. */
object ProjectPathValidator {
    fun normalizeRelativePath(rawPath: String): String? {
        val path = rawPath.trim().replace('\\', '/')
        if (path.isBlank() || path.startsWith('/') || path.contains('\u0000')) return null
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return null

        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        if (segments.any { it == "." || it == ".." || ':' in it }) return null
        return segments.joinToString("/")
    }
}
