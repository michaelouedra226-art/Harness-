package com.example.domain.model

data class AIModel(
    val id: String,
    /** The exact identifier returned by the provider's model-list endpoint. */
    val apiModelId: String = id,
    val providerId: String,
    val providerName: String,
    val name: String,
    val contextWindow: String,
    val tokensPerSec: Int,
    val pricingInfo: String,
    val hasVision: Boolean = false,
    val hasCode: Boolean = false,
    val hasTools: Boolean = false,
    val isFast: Boolean = false,
    val isReasoning: Boolean = false,
    val isFavorite: Boolean = false,
    val description: String = ""
)
