package com.example.domain.model

enum class MessageRole {
    USER,
    ASSISTANT,
    AGENT,
    SYSTEM
}

data class AutonomousActionRecord(
    val actionType: String,
    val filePath: String,
    val linesCount: Int,
    val previousContent: String? = null,
    val newContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val agentRole: AgentRole? = null,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String = "",
    val tokenCount: Int = 0,
    val attachedCodeSnippet: String? = null,
    val isStreaming: Boolean = false,
    val autonomousActions: List<AutonomousActionRecord> = emptyList()
)
