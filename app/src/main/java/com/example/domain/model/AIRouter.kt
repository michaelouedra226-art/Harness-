package com.example.domain.model

enum class RouterSlotType(
    val title: String,
    val iconEmoji: String,
    val roleDescription: String,
    val recommendedTag: String
) {
    FAST("Rapide", "⚡", "Conversations simples, prompts courts et questions générales", "gpt-4o-mini / mistral-small"),
    POWERFUL("Puissant", "🧠", "Raisonnement complexe, conception d'architecture & DAG", "claude-3-7-sonnet / gpt-4.5"),
    CODE("Code", "💻", "Génération de code robuste, refactoring et tests", "deepseek-coder / sonnet"),
    VISION("Vision", "👁", "Analyse de maquettes UI, wireframes et images", "gemini-2.5-pro / gpt-4o"),
    CREATIVE("Créatif", "🎨", "Design tokens, rédaction produit et copy web", "mistral-large / gpt-4o")
}

data class RouterSlotConfig(
    val slot: RouterSlotType,
    val assignedModelId: String,
    val assignedModelName: String,
    val autoFallback: Boolean = true,
    val temperature: Float = 0.7f
)

data class AIRouterConfig(
    val slots: Map<RouterSlotType, RouterSlotConfig>,
    val fallbackChain: List<RouterSlotType> = listOf(
        RouterSlotType.POWERFUL,
        RouterSlotType.CODE,
        RouterSlotType.FAST
    ),
    val enableLocalOllamaFallback: Boolean = true
)
