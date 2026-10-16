package com.example.domain.model

enum class AgentRole(
    val title: String,
    val iconEmoji: String,
    val specialty: String,
    val defaultModelSlot: RouterSlotType,
    val promptFocus: String
) {
    ARCHITECT(
        title = "Architecte",
        iconEmoji = "🏛",
        specialty = "Définit structure, stack, diagrammes et DAG de tâches",
        defaultModelSlot = RouterSlotType.POWERFUL,
        promptFocus = "Conception d'architecture système, arborescence de fichiers et modularité"
    ),
    DESIGNER(
        title = "Designer UI",
        iconEmoji = "🎨",
        specialty = "Palette, typo, tokens, wireframes et layout responsive",
        defaultModelSlot = RouterSlotType.CREATIVE,
        promptFocus = "Design system M3/Tailwind, contrastes accessibles et micro-interactions"
    ),
    DEVELOPER(
        title = "Développeur",
        iconEmoji = "💻",
        specialty = "Génère le code source propre, typé et modulaire",
        defaultModelSlot = RouterSlotType.CODE,
        promptFocus = "Implémentation production-ready, algorithmes, composants et gestion d'état"
    ),
    TESTER(
        title = "Testeur",
        iconEmoji = "🧪",
        specialty = "Écrit et valide les tests unitaires et d'intégration",
        defaultModelSlot = RouterSlotType.CODE,
        promptFocus = "Couverture des cas limites, tests automatisés et assertions"
    ),
    DEBUGGER(
        title = "Débogueur",
        iconEmoji = "🐞",
        specialty = "Analyse les stack traces, erreurs et exceptions",
        defaultModelSlot = RouterSlotType.POWERFUL,
        promptFocus = "Diagnostic cause racine, correction ciblée et prévention de régressions"
    ),
    SECURITY(
        title = "Expert Sécurité",
        iconEmoji = "🔒",
        specialty = "Audit OWASP, injection, détection de secrets en clair",
        defaultModelSlot = RouterSlotType.POWERFUL,
        promptFocus = "Sécurisation des endpoints, chiffrement Keystore et conformité"
    ),
    DEVOPS(
        title = "DevOps & CI/CD",
        iconEmoji = "⚙️",
        specialty = "Workflows GitHub Actions, scripts de build et export",
        defaultModelSlot = RouterSlotType.FAST,
        promptFocus = "Automatisation de livraison, APK/ZIP packaging et orchestration"
    ),
    DOCS(
        title = "Documentaliste",
        iconEmoji = "📝",
        specialty = "README, guides d'installation et spécifications d'API",
        defaultModelSlot = RouterSlotType.FAST,
        promptFocus = "Documentation claire, guides pas-à-pas et contrats techniques"
    )
}

enum class AgentTaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class AgentTask(
    val id: String,
    val agentRole: AgentRole,
    val title: String,
    val description: String,
    val status: AgentTaskStatus = AgentTaskStatus.PENDING,
    val progressPercent: Int = 0,
    val logs: List<String> = emptyList(),
    val generatedFilesCount: Int = 0
)
