package com.example.domain.model

data class HermesApprovalRequest(
    val runId: String,
    val requestId: String,
    val toolName: String,
    val commandPreview: String,
    val availableChoices: List<String>
)

/** Observable, factual progress for one live chat request. */
data class ChatProgress(
    val stage: String,
    val startedAtMillis: Long,
    val providerName: String = "",
    val modelName: String = "",
    val receivedCharacters: Int = 0,
    val firstChunkAtMillis: Long? = null,
    val providerLatencyMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val approvalRequest: HermesApprovalRequest? = null
)
