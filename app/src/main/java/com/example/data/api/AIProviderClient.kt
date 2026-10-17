package com.example.data.api

import com.example.domain.model.AIProvider
import com.example.domain.model.ProviderType
import com.example.domain.model.TestStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIProviderClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class TestResult(
        val status: TestStatus,
        val message: String,
        val latencyMs: Long,
        val detectedModels: List<String> = emptyList()
    )

    data class ChatResponse(
        val content: String,
        val tokensIn: Int = 0,
        val tokensOut: Int = 0,
        val latencyMs: Long = 0,
        val rawResponse: String = ""
    )

    suspend fun testConnection(provider: AIProvider): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val validationError = validateProvider(provider)
            if (validationError != null) {
                return@withContext TestResult(TestStatus.ERROR, validationError, 0)
            }

            val url = modelsUrl(provider)
            val request = requestBuilder(provider, url).get().build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string().orEmpty() }
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            if (code in 200..299) {
                val models = parseModelsList(body).distinct()
                if (models.isEmpty()) {
                    TestResult(
                        TestStatus.WARNING,
                        "Connexion réussie, mais l’endpoint n’a renvoyé aucun modèle. Vérifiez l’URL de base et les permissions de la clé.",
                        latency
                    )
                } else {
                    TestResult(
                        TestStatus.SUCCESS,
                        "Connexion réussie — ${models.size} modèles récupérés depuis le fournisseur.",
                        latency,
                        models
                    )
                }
            } else {
                TestResult(TestStatus.ERROR, formatHttpError(code, response.message, body), latency)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            TestResult(
                TestStatus.ERROR,
                "Erreur de connexion : ${e.localizedMessage ?: "serveur injoignable"}",
                System.currentTimeMillis() - startTime
            )
        }
    }

    suspend fun sendChatCompletion(
        provider: AIProvider,
        modelId: String,
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        temperature: Float = 0.7f
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val validationError = validateProvider(provider)
            if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))
            if (modelId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Aucun modèle n’est sélectionné."))

            val request = buildChatRequest(provider, modelId, systemPrompt, messages, temperature, streaming = false)
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string().orEmpty() }
            if (!response.isSuccessful) {
                Result.failure(Exception(formatHttpError(response.code, response.message, body)))
            } else {
                val parsed = parseChatContent(provider.type, body)
                if (parsed.isBlank()) {
                    Result.failure(Exception("Le fournisseur a répondu sans contenu texte."))
                } else {
                    val usage = parseTokenUsage(provider.type, body)
                    Result.success(
                        ChatResponse(
                            content = parsed,
                            tokensIn = usage.first,
                            tokensOut = usage.second,
                            latencyMs = System.currentTimeMillis() - startTime,
                            rawResponse = body
                        )
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reads server-sent events (or Ollama's newline-delimited JSON) as they arrive. */
    suspend fun sendChatCompletionStream(
        provider: AIProvider,
        modelId: String,
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        temperature: Float = 0.5f,
        onChunk: (accumulated: String, delta: String) -> Unit
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val validationError = validateProvider(provider)
            if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))
            if (modelId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Aucun modèle n’est sélectionné."))

            val request = buildChatRequest(provider, modelId, systemPrompt, messages, temperature, streaming = true)
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.use { it.body?.string().orEmpty() }
                return@withContext Result.failure(Exception(formatHttpError(response.code, response.message, errorBody)))
            }

            val accumulated = StringBuilder()
            val body = response.body ?: return@withContext Result.failure(Exception("Réponse vide du fournisseur."))
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val payload = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        provider.type == ProviderType.OLLAMA && line.isNotBlank() -> line.trim()
                        else -> continue
                    }
                    if (payload.isBlank() || payload == "[DONE]") continue
                    val delta = extractStreamDelta(provider.type, payload)
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onChunk(accumulated.toString(), delta)
                    }
                }
            }
            if (accumulated.isEmpty()) {
                Result.failure(Exception("Le fournisseur n’a envoyé aucun fragment de texte."))
            } else {
                Result.success(
                    ChatResponse(
                        content = accumulated.toString(),
                        latencyMs = System.currentTimeMillis() - startTime
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validateProvider(provider: AIProvider): String? {
        if (provider.baseUrl.isBlank()) return "L’URL de base est requise."
        if (provider.type != ProviderType.OLLAMA && provider.type != ProviderType.CUSTOM && provider.apiKey.isBlank()) {
            return "La clé API est requise pour ce fournisseur."
        }
        val url = provider.baseUrl.toHttpUrlOrNull() ?: return "L’URL de base doit être une URL HTTP/HTTPS valide."
        if (url.isHttps.not() && (provider.type != ProviderType.OLLAMA || !isLocalOllamaHost(url.host))) {
            return "HTTPS est requis pour les fournisseurs distants. HTTP est autorisé uniquement pour Ollama sur un hôte local/privé."
        }
        return null
    }

    private fun isLocalOllamaHost(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2" ||
            host.endsWith(".local") || host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host)

    private fun requestBuilder(provider: AIProvider, url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (provider.apiKey.isNotBlank()) {
            when (provider.type) {
                ProviderType.ANTHROPIC -> {
                    builder.header("x-api-key", provider.apiKey)
                    builder.header("anthropic-version", "2023-06-01")
                }
                ProviderType.GEMINI -> Unit // Gemini authenticates with a query parameter.
                else -> builder.header("Authorization", "Bearer ${provider.apiKey}")
            }
        }
        provider.customHeaders.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun modelsUrl(provider: AIProvider): String {
        val base = provider.baseUrl.trim().trimEnd('/')
        val path = when (provider.type) {
            ProviderType.OLLAMA -> if (base.endsWith("/api")) "tags" else "api/tags"
            else -> "models"
        }
        val url = appendPath(base, path)
        return if (provider.type == ProviderType.GEMINI) addQuery(url, "key", provider.apiKey) else url
    }

    private fun buildChatRequest(
        provider: AIProvider,
        modelId: String,
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        temperature: Float,
        streaming: Boolean
    ): Request {
        val base = provider.baseUrl.trim().trimEnd('/')
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val (url, body) = when (provider.type) {
            ProviderType.ANTHROPIC -> {
                val endpoint = appendPath(base, "messages")
                val json = JSONObject().apply {
                    put("model", modelId)
                    put("max_tokens", 4096)
                    put("stream", streaming)
                    if (systemPrompt.isNotBlank()) put("system", systemPrompt)
                    put("messages", JSONArray().apply {
                        messages.filter { it.first != "system" }.forEach { (role, content) ->
                            put(JSONObject().put("role", if (role == "user") "user" else "assistant").put("content", content))
                        }
                    })
                }
                endpoint to json.toString()
            }
            ProviderType.GEMINI -> {
                val cleanModel = modelId.removePrefix("models/")
                val action = if (streaming) "streamGenerateContent" else "generateContent"
                var endpoint = appendPath(base, "models/$cleanModel:$action")
                endpoint = addQuery(endpoint, "key", provider.apiKey)
                if (streaming) endpoint = addQuery(endpoint, "alt", "sse")
                val json = JSONObject().apply {
                    if (systemPrompt.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                    put("contents", JSONArray().apply {
                        messages.filter { it.first != "system" }.forEach { (role, content) ->
                            put(JSONObject().put("role", if (role == "user") "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", content))))
                        }
                    })
                }
                endpoint to json.toString()
            }
            ProviderType.OLLAMA -> {
                val path = if (base.endsWith("/api")) "chat" else "api/chat"
                val json = JSONObject().apply {
                    put("model", modelId)
                    put("stream", streaming)
                    put("messages", JSONArray().apply {
                        if (systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", systemPrompt))
                        messages.forEach { (role, content) -> put(JSONObject().put("role", role).put("content", content)) }
                    })
                }
                appendPath(base, path) to json.toString()
            }
            else -> {
                val json = JSONObject().apply {
                    put("model", modelId)
                    put("temperature", temperature.toDouble())
                    put("stream", streaming)
                    put("messages", JSONArray().apply {
                        if (systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", systemPrompt))
                        messages.forEach { (role, content) -> put(JSONObject().put("role", role).put("content", content)) }
                    })
                }
                appendPath(base, "chat/completions") to json.toString()
            }
        }
        return requestBuilder(provider, url).post(body.toRequestBody(jsonMediaType)).build()
    }

    private fun appendPath(base: String, path: String): String {
        val cleanPath = path.trim('/')
        return if (base.endsWith("/$cleanPath")) base else "$base/$cleanPath"
    }

    private fun addQuery(url: String, name: String, value: String): String {
        val parsed = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("URL invalide : $url")
        return parsed.newBuilder().addQueryParameter(name, value).build().toString()
    }

    private fun formatHttpError(code: Int, message: String, body: String): String {
        val detail = body.trim().take(1200)
        return "HTTP $code${message.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}" +
            detail.takeIf { it.isNotBlank() }?.let { " : $it" }.orEmpty()
    }

    private fun parseChatContent(type: ProviderType, jsonStr: String): String {
        val root = JSONObject(jsonStr)
        return when (type) {
            ProviderType.ANTHROPIC -> root.optJSONArray("content")?.let { content ->
                (0 until content.length()).mapNotNull { content.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("")
            }.orEmpty()
            ProviderType.GEMINI -> root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                    (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("")
                }.orEmpty()
            ProviderType.OLLAMA -> root.optJSONObject("message")?.optString("content").orEmpty()
            else -> root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")?.let(::contentValue).orEmpty()
        }
    }

    private fun extractStreamDelta(type: ProviderType, payload: String): String {
        return try {
            val root = JSONObject(payload)
            when (type) {
                ProviderType.ANTHROPIC -> root.optJSONObject("delta")?.optString("text").orEmpty()
                ProviderType.GEMINI -> root.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                        (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("")
                    }.orEmpty()
                ProviderType.OLLAMA -> root.optJSONObject("message")?.optString("content").orEmpty()
                else -> root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.opt("content")?.let(::contentValue).orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun contentValue(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> (0 until value.length()).mapNotNull { i ->
            val item = value.optJSONObject(i)
            item?.optString("text")?.takeIf(String::isNotBlank)
        }.joinToString("")
        else -> ""
    }

    private fun parseTokenUsage(type: ProviderType, jsonStr: String): Pair<Int, Int> = try {
        val root = JSONObject(jsonStr)
        when (type) {
            ProviderType.ANTHROPIC -> root.optJSONObject("usage")?.let {
                it.optInt("input_tokens") to it.optInt("output_tokens")
            } ?: (0 to 0)
            ProviderType.GEMINI -> root.optJSONObject("usageMetadata")?.let {
                it.optInt("promptTokenCount") to it.optInt("candidatesTokenCount")
            } ?: (0 to 0)
            ProviderType.OLLAMA -> root.optInt("prompt_eval_count") to root.optInt("eval_count")
            else -> root.optJSONObject("usage")?.let {
                it.optInt("prompt_tokens") to it.optInt("completion_tokens")
            } ?: (0 to 0)
        }
    } catch (_: Exception) {
        0 to 0
    }

    private fun parseModelsList(jsonStr: String): List<String> {
        return try {
            val root = JSONObject(jsonStr)
            val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf(String::isNotBlank)
                    ?: item.optString("name").takeIf(String::isNotBlank)
                id
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
