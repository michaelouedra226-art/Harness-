package com.example.domain.model

enum class RouterSlotType(
    val title: String,
    val iconEmoji: String,
    val roleDescription: String
) {
    FAST("Rapide", "⚡", "Conversations simples, prompts courts et questions générales"),
    POWERFUL("Puissant", "🧠", "Raisonnement complexe, conception d'architecture & DAG"),
    CODE("Code", "💻", "Génération de code robuste, refactoring et tests"),
    VISION("Vision", "👁", "Analyse de maquettes UI, wireframes et images"),
    CREATIVE("Créatif", "🎨", "Design tokens, rédaction produit et copy web")
}

data class RouterSlotConfig(
    val slot: RouterSlotType,
    val assignedModelId: String,
    val assignedModelName: String,
    val autoFallback: Boolean = true,
    val temperature: Float = 0.7f
)

data class AIRouterConfig(
    val slots: Map<RouterSlotType, RouterSlotConfig>
)
