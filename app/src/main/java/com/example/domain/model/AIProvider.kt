package com.example.domain.model

enum class ProviderType(val displayName: String, val defaultBaseUrl: String) {
    OPENAI_COMPATIBLE("OpenAI-Compatible", "https://api.openai.com/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1"),
    MISTRAL("Mistral AI", "https://api.mistral.ai/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    OLLAMA("Ollama Local", "http://127.0.0.1:11434/api"),
    CUSTOM("Fournisseur Personnalisé", "")
}

enum class TestStatus {
    UNTESTED,
    TESTING,
    SUCCESS,
    WARNING,
    ERROR
}

data class AIProvider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val testStatus: TestStatus = TestStatus.UNTESTED,
    val testMessage: String = "Non testé",
    val latencyMs: Long = 0,
    val lastTestedAt: Long = 0,
    val modelsCount: Int = 0,
    val timeoutMs: Int = 30000,
    val customHeaders: Map<String, String> = emptyMap()
)
