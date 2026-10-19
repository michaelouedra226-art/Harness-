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

    data class HermesRunEvent(
        val event: String,
        val runId: String = "",
        val toolName: String = "",
        val preview: String = "",
        val isError: Boolean = false,
        val output: String = "",
        val requestId: String = "",
        val choices: List<String> = emptyList(),
        val command: String = ""
    )

    private val hermesRunsClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Runs a real Hermes Agent turn and consumes its structured tool/approval event stream. */
    suspend fun runHermesAgent(
        provider: AIProvider,
        modelId: String,
        instructions: String,
        input: String,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onChunk: (accumulated: String, delta: String) -> Unit = { _, _ -> },
        onEvent: (HermesRunEvent) -> Unit = {}
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            if (provider.type != ProviderType.HERMES_AGENT) {
                return@withContext Result.failure(IllegalArgumentException("Cette API Runs est réservée à Hermes Agent."))
            }
            val validationError = validateProvider(provider)
            if (validationError != null) return@withContext Result.failure(IllegalArgumentException(validationError))
            if (modelId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Aucun modèle Hermes n’est sélectionné."))
            if (input.isBlank()) return@withContext Result.failure(IllegalArgumentException("La demande est vide."))

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = JSONObject().apply {
                put("input", input)
                put("instructions", instructions)
                put("model", modelId)
                if (conversationHistory.isNotEmpty()) {
                    put("conversation_history", JSONArray().apply {
                        conversationHistory.forEach { (role, content) ->
                            put(JSONObject().put("role", role).put("content", content))
                        }
                    })
                }
            }
            val base = provider.baseUrl.trim().trimEnd('/')
            val submitRequest = requestBuilder(provider, appendPath(base, "runs"))
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()
            val submitResponse = hermesRunsClient.newCall(submitRequest).execute()
            val submitBody = submitResponse.use { it.body?.string().orEmpty() }
            if (!submitResponse.isSuccessful) {
                return@withContext Result.failure(Exception(formatHttpError(submitResponse.code, submitResponse.message, submitBody)))
            }
            val runId = JSONObject(submitBody).optString("run_id").takeIf { it.isNotBlank() }
                ?: return@withContext Result.failure(Exception("Hermes n’a pas renvoyé l’identifiant du run."))
            onEvent(HermesRunEvent(event = "run.started", runId = runId))

            val eventsUrl = appendPath(base, "runs/$runId/events")
            val eventsRequest = requestBuilder(provider, eventsUrl).get().build()
            val eventsResponse = hermesRunsClient.newCall(eventsRequest).execute()
            if (!eventsResponse.isSuccessful) {
                val body = eventsResponse.use { it.body?.string().orEmpty() }
                return@withContext Result.failure(Exception(formatHttpError(eventsResponse.code, eventsResponse.message, body)))
            }

            val accumulated = StringBuilder()
            var finalOutput = ""
            var errorMessage = ""
            var inputTokens = 0
            var outputTokens = 0
            val eventBody = eventsResponse.body ?: return@withContext Result.failure(Exception("Flux d’événements Hermes vide."))
            eventBody.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank()) continue
                    val root = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    val eventName = root.optString("event")
                    val eventRunId = root.optString("run_id", runId)
                    val toolName = sequenceOf("tool", "tool_name", "name")
                        .mapNotNull { key -> root.opt(key)?.let { value ->
                            if (value is JSONObject) value.optString("name") else value.toString()
                        } }
                        .firstOrNull { it.isNotBlank() }.orEmpty()
                    when (eventName) {
                        "message.delta" -> {
                            val delta = root.optString("delta")
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                                onChunk(accumulated.toString(), delta)
                            }
                        }
                        "tool.started", "tool.completed", "subagent.start", "subagent.complete" -> {
                            onEvent(HermesRunEvent(
                                event = eventName,
                                runId = eventRunId,
                                toolName = toolName,
                                preview = root.optString("preview"),
                                isError = root.optBoolean("error", root.optBoolean("is_error", false))
                            ))
                        }
                        "approval.request" -> {
                            val choicesArray = root.optJSONArray("choices")
                            val choices = if (choicesArray == null) emptyList() else (0 until choicesArray.length())
                                .mapNotNull { choicesArray.optString(it).takeIf(String::isNotBlank) }
                            onEvent(HermesRunEvent(
                                event = eventName,
                                runId = eventRunId,
                                toolName = toolName,
                                preview = root.optString("preview"),
                                requestId = root.optString("request_id"),
                                choices = choices,
                                command = root.optString("command")
                            ))
                        }
                        "run.completed" -> {
                            finalOutput = root.optString("output")
                            val usage = root.optJSONObject("usage")
                            inputTokens = usage?.optInt("input_tokens", 0) ?: 0
                            outputTokens = usage?.optInt("output_tokens", 0) ?: 0
                            onEvent(HermesRunEvent(event = eventName, runId = eventRunId, output = finalOutput))
                        }
                        "run.failed", "run.cancelled", "run.interrupted" -> {
                            errorMessage = root.optString("error").ifBlank { "Le run Hermes s’est terminé avec le statut ${eventName.removePrefix("run.")}." }
                            onEvent(HermesRunEvent(event = eventName, runId = eventRunId, preview = errorMessage, isError = true))
                        }
                        "approval.responded" -> onEvent(HermesRunEvent(event = eventName, runId = eventRunId))
                    }
                    if (eventName in setOf("run.completed", "run.failed", "run.cancelled", "run.interrupted")) break
                }
            }

            if (errorMessage.isNotBlank()) return@withContext Result.failure(Exception(errorMessage))
            val content = finalOutput.ifBlank { accumulated.toString() }
            if (content.isBlank()) return@withContext Result.failure(Exception("Hermes a terminé sans renvoyer de réponse."))
            if (accumulated.toString() != content) onChunk(content, content)
            Result.success(ChatResponse(
                content = content,
                tokensIn = inputTokens,
                tokensOut = outputTokens,
                latencyMs = System.currentTimeMillis() - startedAt,
                rawResponse = content
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveHermesRunApproval(
        provider: AIProvider,
        runId: String,
        requestId: String,
        choice: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (choice !in setOf("once", "deny")) {
                return@withContext Result.failure(IllegalArgumentException("Choix d’approbation non autorisé."))
            }
            val body = JSONObject().put("choice", choice).apply {
                if (requestId.isNotBlank()) put("request_id", requestId)
            }
            val url = appendPath(provider.baseUrl.trim().trimEnd('/'), "runs/$runId/approval")
            val request = requestBuilder(provider, url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.use { it.body?.string().orEmpty() }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(formatHttpError(response.code, response.message, responseBody)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                if (provider.type == ProviderType.HERMES_AGENT) {
                    val capabilitiesRequest = requestBuilder(provider, appendPath(provider.baseUrl.trim().trimEnd('/'), "capabilities")).get().build()
                    val capabilitiesResponse = client.newCall(capabilitiesRequest).execute()
                    val capabilitiesBody = capabilitiesResponse.use { it.body?.string().orEmpty() }
                    if (!capabilitiesResponse.isSuccessful) {
                        return@withContext TestResult(
                            TestStatus.ERROR,
                            "L’endpoint répond, mais la vérification Hermes /capabilities a échoué : ${formatHttpError(capabilitiesResponse.code, capabilitiesResponse.message, capabilitiesBody)}",
                            System.currentTimeMillis() - startTime,
                            models
                        )
                    }
                    val capabilities = JSONObject(capabilitiesBody)
                    val isHermes = capabilities.optString("object") == "hermes.api_server.capabilities"
                    val features = capabilities.optJSONObject("features")
                    val supportsChat = features?.optBoolean("chat_completions", false) == true
                    if (!isHermes || !supportsChat) {
                        return@withContext TestResult(
                            TestStatus.ERROR,
                            "Ce serveur n’annonce pas l’API Hermes Agent attendue. Vérifiez l’URL /v1 et l’activation de l’API Server Hermes.",
                            System.currentTimeMillis() - startTime,
                            models
                        )
                    }
                    return@withContext TestResult(
                        if (models.isEmpty()) TestStatus.WARNING else TestStatus.SUCCESS,
                        if (models.isEmpty()) "Serveur Hermes confirmé, mais aucun modèle n’est exposé sur /models. Vérifiez le modèle côté Hermes."
                        else "Serveur Hermes Agent confirmé — ${models.size} modèles exposés. Ses outils s’exécutent côté serveur.",
                        System.currentTimeMillis() - startTime,
                        models
                    )
                }
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
        onChunk: (accumulated: String, delta: String) -> Unit,
        onAgentProgress: (String) -> Unit = {}
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
            var sseEventName = ""
            val body = response.body ?: return@withContext Result.failure(Exception("Réponse vide du fournisseur."))
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("event:")) {
                        sseEventName = line.removePrefix("event:").trim()
                        continue
                    }
                    if (line.isBlank()) {
                        sseEventName = ""
                        continue
                    }
                    val payload = when {
                        line.startsWith("data:") -> line.removePrefix("data:").trim()
                        provider.type == ProviderType.OLLAMA && line.isNotBlank() -> line.trim()
                        else -> continue
                    }
                    if (payload.isBlank() || payload == "[DONE]") continue
                    if (sseEventName == "hermes.tool.progress") {
                        onAgentProgress(formatHermesToolProgress(payload))
                        continue
                    }
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
        val localRuntime = provider.type in setOf(ProviderType.OLLAMA, ProviderType.HERMES_AGENT) && isLocalOllamaHost(url.host)
        if (url.isHttps.not() && !localRuntime) {
            return "HTTPS est requis pour les fournisseurs distants. HTTP est autorisé uniquement pour un runtime local/privé Ollama ou Hermes Agent."
        }
        return null
    }

    private fun isLocalOllamaHost(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2" ||
            host.endsWith(".local") || host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host)

    private fun formatHermesToolProgress(payload: String): String {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return "Hermes exécute un outil"
        val rawTool = sequenceOf("tool_name", "tool", "name").mapNotNull { key ->
            root.opt(key)?.let { value -> if (value is JSONObject) value.optString("name") else value.toString() }
        }.firstOrNull { it.isNotBlank() }
        val safeTool = rawTool?.replace(Regex("[^A-Za-z0-9_.-]"), "")?.take(48)
        return if (safeTool.isNullOrBlank()) "Hermes exécute un outil" else "Hermes · outil : $safeTool"
    }

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
