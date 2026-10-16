package com.example.domain.model

data class AIModel(
    val id: String,
    val providerId: String,
    val providerName: String,
    val name: String,
    val contextWindow: String,
    val tokensPerSec: Int,
    val pricingInfo: String,
    val hasVision: Boolean = false,
    val hasCode: Boolean = true,
    val hasTools: Boolean = true,
    val isFast: Boolean = false,
    val isReasoning: Boolean = false,
    val isFavorite: Boolean = false,
    val description: String = ""
)
