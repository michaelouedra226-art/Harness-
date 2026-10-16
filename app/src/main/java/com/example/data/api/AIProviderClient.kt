package com.example.data.api

import com.example.domain.model.AIProvider
import com.example.domain.model.ProviderType
import com.example.domain.model.TestStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIProviderClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
            if (provider.baseUrl.isBlank()) {
                return@withContext TestResult(
                    status = TestStatus.ERROR,
                    message = "L'URL de base est requise.",
                    latencyMs = 0
                )
            }

            val cleanUrl = provider.baseUrl.trim().removeSuffix("/")
            val testEndpoint = when (provider.type) {
                ProviderType.OPENAI_COMPATIBLE,
                ProviderType.OPENROUTER,
                ProviderType.DEEPSEEK,
                ProviderType.MISTRAL -> "$cleanUrl/models"
                ProviderType.ANTHROPIC -> "$cleanUrl/models"
                ProviderType.GEMINI -> "$cleanUrl/models?key=${provider.apiKey}"
                ProviderType.OLLAMA -> "$cleanUrl/tags"
                ProviderType.CUSTOM -> "$cleanUrl/models"
            }

            val requestBuilder = Request.Builder().url(testEndpoint).get()

            if (provider.apiKey.isNotBlank()) {
                when (provider.type) {
                    ProviderType.ANTHROPIC -> {
                        requestBuilder.header("x-api-key", provider.apiKey)
                        requestBuilder.header("anthropic-version", "2023-06-01")
                    }
                    ProviderType.GEMINI -> {
                        // Handled in query param
                    }
                    else -> {
                        requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                    }
                }
            }

            provider.customHeaders.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyString = response.body?.string().orEmpty()

            when (response.code) {
                200 -> {
                    val models = parseModelsList(bodyString)
                    TestResult(
                        status = TestStatus.SUCCESS,
                        message = "200 OK — ${models.size} modèles détectés en direct",
                        latencyMs = latency,
                        detectedModels = models
                    )
                }
                401 -> TestResult(
                    status = TestStatus.ERROR,
                    message = "401 Non autorisé : Clé API invalide ou expirée.",
                    latencyMs = latency
                )
                403 -> TestResult(
                    status = TestStatus.ERROR,
                    message = "403 Accès refusé : Permissions de clé insuffisantes.",
                    latencyMs = latency
                )
                429 -> TestResult(
                    status = TestStatus.WARNING,
                    message = "429 Quota dépassé : Rate-limit ou solde épuisé.",
                    latencyMs = latency
                )
                else -> TestResult(
                    status = TestStatus.ERROR,
                    message = "HTTP ${response.code} : ${response.message.ifBlank { "Échec" }}",
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            TestResult(
                status = TestStatus.ERROR,
                message = "Erreur de connexion : ${e.localizedMessage ?: "Serveur injoignable"}",
                latencyMs = latency
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
            val cleanUrl = provider.baseUrl.trim().removeSuffix("/")
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            val (url, requestBodyJson, headers) = when (provider.type) {
                ProviderType.ANTHROPIC -> {
                    val endpoint = if (cleanUrl.endsWith("/v1")) "$cleanUrl/messages" else "$cleanUrl/v1/messages"
                    val json = JSONObject().apply {
                        put("model", modelId)
                        put("max_tokens", 4096)
                        if (systemPrompt.isNotBlank()) put("system", systemPrompt)
                        val msgsArray = JSONArray()
                        messages.forEach { (role, content) ->
                            val msgObj = JSONObject()
                            msgObj.put("role", if (role == "user") "user" else "assistant")
                            msgObj.put("content", content)
                            msgsArray.put(msgObj)
                        }
                        put("messages", msgsArray)
                    }
                    Triple(
                        endpoint,
                        json.toString(),
                        mapOf(
                            "x-api-key" to provider.apiKey,
                            "anthropic-version" to "2023-06-01"
                        )
                    )
                }

                ProviderType.GEMINI -> {
                    val cleanModel = if (modelId.startsWith("models/")) modelId.removePrefix("models/") else modelId
                    val endpoint = "$cleanUrl/models/$cleanModel:generateContent?key=${provider.apiKey}"
                    val json = JSONObject().apply {
                        if (systemPrompt.isNotBlank()) {
                            put("systemInstruction", JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                            })
                        }
                        val contentsArray = JSONArray()
                        messages.forEach { (role, content) ->
                            val contentObj = JSONObject()
                            contentObj.put("role", if (role == "user") "user" else "model")
                            contentObj.put("parts", JSONArray().put(JSONObject().put("text", content)))
                            contentsArray.put(contentObj)
                        }
                        put("contents", contentsArray)
                    }
                    Triple(endpoint, json.toString(), emptyMap())
                }

                ProviderType.OLLAMA -> {
                    val endpoint = "$cleanUrl/chat"
                    val json = JSONObject().apply {
                        put("model", modelId)
                        put("stream", false)
                        val msgsArray = JSONArray()
                        if (systemPrompt.isNotBlank()) {
                            msgsArray.put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            })
                        }
                        messages.forEach { (role, content) ->
                            msgsArray.put(JSONObject().apply {
                                put("role", role)
                                put("content", content)
                            })
                        }
                        put("messages", msgsArray)
                    }
                    Triple(endpoint, json.toString(), emptyMap())
                }

                else -> {
                    // OpenAI-Compatible (OpenAI, OpenRouter, DeepSeek, Mistral, Custom)
                    val endpoint = "$cleanUrl/chat/completions"
                    val json = JSONObject().apply {
                        put("model", modelId)
                        put("temperature", temperature.toDouble())
                        val msgsArray = JSONArray()
                        if (systemPrompt.isNotBlank()) {
                            msgsArray.put(JSONObject().apply {
                                put("role", "system")
                                put("content", systemPrompt)
                            })
                        }
                        messages.forEach { (role, content) ->
                            msgsArray.put(JSONObject().apply {
                                put("role", role)
                                put("content", content)
                            })
                        }
                        put("messages", msgsArray)
                    }
                    Triple(
                        endpoint,
                        json.toString(),
                        if (provider.apiKey.isNotBlank()) mapOf("Authorization" to "Bearer ${provider.apiKey}") else emptyMap()
                    )
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody(jsonMediaType))

            headers.forEach { (k, v) -> requestBuilder.header(k, v) }
            provider.customHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code} : $bodyString")
                )
            }

            val parsedContent = parseChatContent(provider.type, bodyString)
            Result.success(
                ChatResponse(
                    content = parsedContent,
                    latencyMs = latency,
                    rawResponse = bodyString
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatCompletionStream(
        provider: AIProvider,
        modelId: String,
        systemPrompt: String,
        messages: List<Pair<String, String>>,
        temperature: Float = 0.5f,
        onChunk: (accumulated: String, delta: String) -> Unit
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        val result = sendChatCompletion(provider, modelId, systemPrompt, messages, temperature)
        if (result.isSuccess) {
            val response = result.getOrThrow()
            val text = response.content
            val words = text.split(Regex("(?<=\\s)|(?<=\\n)"))
            var accumulated = ""
            for (word in words) {
                accumulated += word
                onChunk(accumulated, word)
                kotlinx.coroutines.delay(12)
            }
            Result.success(response)
        } else {
            result
        }
    }

    private fun parseChatContent(type: ProviderType, jsonStr: String): String {
        try {
            val root = JSONObject(jsonStr)
            when (type) {
                ProviderType.ANTHROPIC -> {
                    if (root.has("content")) {
                        val arr = root.getJSONArray("content")
                        if (arr.length() > 0) {
                            return arr.getJSONObject(0).optString("text", "")
                        }
                    }
                }
                ProviderType.GEMINI -> {
                    if (root.has("candidates")) {
                        val candidates = root.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val parts = candidate.getJSONObject("content").getJSONArray("parts")
                            if (parts.length() > 0) {
                                return parts.getJSONObject(0).optString("text", "")
                            }
                        }
                    }
                }
                ProviderType.OLLAMA -> {
                    if (root.has("message")) {
                        return root.getJSONObject("message").optString("content", "")
                    }
                }
                else -> {
                    if (root.has("choices")) {
                        val choices = root.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val first = choices.getJSONObject(0)
                            if (first.has("message")) {
                                return first.getJSONObject("message").optString("content", "")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return "Erreur parsing réponse : ${e.message}\n$jsonStr"
        }
        return jsonStr
    }

    private fun parseModelsList(jsonStr: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val root = JSONObject(jsonStr)
            if (root.has("data")) {
                val dataArr = root.getJSONArray("data")
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    if (item.has("id")) {
                        list.add(item.getString("id"))
                    }
                }
            } else if (root.has("models")) {
                val modelsArr = root.getJSONArray("models")
                for (i in 0 until modelsArr.length()) {
                    val item = modelsArr.getJSONObject(i)
                    val name = if (item.has("name")) item.getString("name") else item.optString("id")
                    if (name.isNotBlank()) list.add(name)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return list
    }
}
