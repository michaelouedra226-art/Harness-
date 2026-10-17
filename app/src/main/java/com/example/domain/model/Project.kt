package com.example.domain.model

enum class ProjectType(
    val displayName: String,
    val iconEmoji: String,
    val stackSummary: String
) {
    WEB_SITE("Site Web Moderne", "🌐", "HTML5 Sémantique · Tailwind CSS · Vanilla JS / React"),
    MOBILE_FLUTTER("Application Mobile Flutter", "📱", "Flutter 3.x · Dart · Clean Architecture · Widgets"),
    MOBILE_KOTLIN_COMPOSE("App Android Native Compose", "🤖", "Kotlin · Jetpack Compose · MVVM · Room / Coroutines"),
    SCRIPT_API("API / Script Backend", "⚡", "Node.js / Python FastREST · Contrats OpenAPI")
}

enum class ProjectStatus(val label: String) {
    IDEA("Idée initiale"),
    PLANNING("Planification"),
    GENERATING("Génération en cours"),
    READY("Généré · à vérifier"),
    ERROR("Erreur de génération")
}

data class ProjectFile(
    val path: String,
    val name: String,
    val extension: String,
    val content: String,
    val language: String,
    val isModified: Boolean = false,
    val isDirectory: Boolean = false
)

data class ProjectVersion(
    val id: String,
    val versionNumber: String,
    val timestamp: Long,
    val description: String,
    val filesCount: Int
)

data class Project(
    val id: String,
    val name: String,
    val description: String,
    val type: ProjectType,
    val status: ProjectStatus = ProjectStatus.READY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val files: List<ProjectFile> = emptyList(),
    val versions: List<ProjectVersion> = emptyList(),
    val tokensUsed: Long = 0,
    val totalLinesOfCode: Int = 0
)
