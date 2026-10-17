package com.example.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class NuruLocalStorage(private val context: Context) {

    private val providersFile get() = File(context.filesDir, "nuru_providers.json")
    private val modelsFile get() = File(context.filesDir, "nuru_models.json")
    private val routerFile get() = File(context.filesDir, "nuru_router.json")
    private val projectsFile get() = File(context.filesDir, "nuru_projects.json")
    private val chatFile get() = File(context.filesDir, "nuru_chat.json")
    private val keyAlias = "nuru.provider.api.keys.v1"
    private val encryptedKeyPrefix = "enc:v1:"

    fun saveProviders(providers: List<AIProvider>) {
        try {
            val array = JSONArray()
            providers.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("type", p.type.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", encryptApiKey(p.apiKey))
                    put("isEnabled", p.isEnabled)
                    put("isDefault", p.isDefault)
                    put("testStatus", p.testStatus.name)
                    put("testMessage", p.testMessage)
                    put("latencyMs", p.latencyMs)
                    put("modelsCount", p.modelsCount)
                }
                array.put(obj)
            }
            providersFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

    fun loadProviders(): List<AIProvider> {
        if (!providersFile.exists()) return emptyList()
        return try {
            val content = providersFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<AIProvider>()
            var needsMigration = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawApiKey = obj.optString("apiKey")
                if (rawApiKey.isNotBlank() && !rawApiKey.startsWith(encryptedKeyPrefix)) needsMigration = true
                list.add(
                    AIProvider(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = ProviderType.valueOf(obj.getString("type")),
                        baseUrl = obj.getString("baseUrl"),
                        apiKey = decryptApiKey(rawApiKey),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isDefault = obj.optBoolean("isDefault", false),
                        testStatus = TestStatus.valueOf(obj.optString("testStatus", TestStatus.UNTESTED.name)),
                        testMessage = obj.optString("testMessage", ""),
                        latencyMs = obj.optLong("latencyMs", 0),
                        modelsCount = obj.optInt("modelsCount", 0)
                    )
                )
            }
            val configured = list.filter { provider ->
                provider.apiKey.isNotBlank() ||
                    provider.type == ProviderType.CUSTOM ||
                    (provider.type == ProviderType.OLLAMA && provider.baseUrl != ProviderType.OLLAMA.defaultBaseUrl)
            }
            if (needsMigration || configured.size != list.size) saveProviders(configured)
            configured
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveModels(models: List<AIModel>) {
        try {
            val array = JSONArray()
            models.forEach { model ->
                array.put(JSONObject().apply {
                    put("id", model.id)
                    put("apiModelId", model.apiModelId)
                    put("providerId", model.providerId)
                    put("providerName", model.providerName)
                    put("name", model.name)
                    put("contextWindow", model.contextWindow)
                    put("tokensPerSec", model.tokensPerSec)
                    put("pricingInfo", model.pricingInfo)
                    put("hasVision", model.hasVision)
                    put("hasCode", model.hasCode)
                    put("hasTools", model.hasTools)
                    put("isFast", model.isFast)
                    put("isReasoning", model.isReasoning)
                    put("isFavorite", model.isFavorite)
                    put("description", model.description)
                })
            }
            modelsFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

    fun loadModels(): List<AIModel> {
        if (!modelsFile.exists()) return emptyList()
        return try {
            val array = JSONArray(modelsFile.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                AIModel(
                    id = id,
                    apiModelId = obj.optString("apiModelId", id),
                    providerId = obj.getString("providerId"),
                    providerName = obj.getString("providerName"),
                    name = obj.getString("name"),
                    contextWindow = obj.optString("contextWindow", "Non communiqué"),
                    tokensPerSec = obj.optInt("tokensPerSec", 0),
                    pricingInfo = obj.optString("pricingInfo", "Selon fournisseur"),
                    hasVision = obj.optBoolean("hasVision", false),
                    hasCode = obj.optBoolean("hasCode", false),
                    hasTools = obj.optBoolean("hasTools", false),
                    isFast = obj.optBoolean("isFast", false),
                    isReasoning = obj.optBoolean("isReasoning", false),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    description = obj.optString("description", "Modèle découvert auprès du fournisseur.")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRouterConfig(config: AIRouterConfig) {
        try {
            val array = JSONArray()
            config.slots.values.forEach { slot ->
                array.put(JSONObject().apply {
                    put("slot", slot.slot.name)
                    put("assignedModelId", slot.assignedModelId)
                    put("assignedModelName", slot.assignedModelName)
                    put("autoFallback", slot.autoFallback)
                    put("temperature", slot.temperature.toDouble())
                })
            }
            routerFile.writeText(JSONObject().put("slots", array).toString())
        } catch (_: Exception) {}
    }

    fun loadRouterConfig(): AIRouterConfig? {
        if (!routerFile.exists()) return null
        return try {
            val array = JSONObject(routerFile.readText()).optJSONArray("slots") ?: return null
            val slots = mutableMapOf<RouterSlotType, RouterSlotConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val slot = RouterSlotType.valueOf(obj.getString("slot"))
                slots[slot] = RouterSlotConfig(
                    slot = slot,
                    assignedModelId = obj.optString("assignedModelId"),
                    assignedModelName = obj.optString("assignedModelName"),
                    autoFallback = obj.optBoolean("autoFallback", true),
                    temperature = obj.optDouble("temperature", 0.7).toFloat()
                )
            }
            AIRouterConfig(slots = slots)
        } catch (_: Exception) {
            null
        }
    }

    fun saveProjects(projects: List<Project>) {
        try {
            val array = JSONArray()
            projects.forEach { proj ->
                val obj = JSONObject().apply {
                    put("id", proj.id)
                    put("name", proj.name)
                    put("description", proj.description)
                    put("type", proj.type.name)
                    put("status", proj.status.name)
                    put("createdAt", proj.createdAt)
                    put("updatedAt", proj.updatedAt)
                    put("tokensUsed", proj.tokensUsed)
                    put("totalLinesOfCode", proj.totalLinesOfCode)

                    val filesArray = JSONArray()
                    proj.files.forEach { f ->
                        val fObj = JSONObject().apply {
                            put("path", f.path)
                            put("name", f.name)
                            put("extension", f.extension)
                            put("content", f.content)
                            put("language", f.language)
                            put("isModified", f.isModified)
                        }
                        filesArray.put(fObj)
                    }
                    put("files", filesArray)
                }
                array.put(obj)
            }
            projectsFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

    fun loadProjects(): List<Project> {
        if (!projectsFile.exists()) return emptyList()
        return try {
            val content = projectsFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<Project>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filesList = mutableListOf<ProjectFile>()
                if (obj.has("files")) {
                    val filesArray = obj.getJSONArray("files")
                    for (j in 0 until filesArray.length()) {
                        val fObj = filesArray.getJSONObject(j)
                        filesList.add(
                            ProjectFile(
                                path = fObj.getString("path"),
                                name = fObj.getString("name"),
                                extension = fObj.getString("extension"),
                                content = fObj.getString("content"),
                                language = fObj.getString("language"),
                                isModified = fObj.optBoolean("isModified", false)
                            )
                        )
                    }
                }
                list.add(
                    Project(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        type = ProjectType.valueOf(obj.getString("type")),
                        status = ProjectStatus.valueOf(obj.optString("status", ProjectStatus.READY.name)),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        files = filesList,
                        tokensUsed = obj.optLong("tokensUsed", 0),
                        totalLinesOfCode = obj.optInt("totalLinesOfCode", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChatMessages(messages: List<ChatMessage>) {
        try {
            val array = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.name)
                    if (msg.agentRole != null) put("agentRole", msg.agentRole.name)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("modelName", msg.modelName)
                    put("tokenCount", msg.tokenCount)
                }
                array.put(obj)
            }
            chatFile.writeText(array.toString())
        } catch (_: Exception) {}
    }

    fun loadChatMessages(): List<ChatMessage> {
        if (!chatFile.exists()) return emptyList()
        return try {
            val content = chatFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val agentRole = if (obj.has("agentRole")) {
                    try { AgentRole.valueOf(obj.getString("agentRole")) } catch (_: Exception) { null }
                } else null

                list.add(
                    ChatMessage(
                        id = obj.getString("id"),
                        role = MessageRole.valueOf(obj.getString("role")),
                        agentRole = agentRole,
                        content = obj.getString("content"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        modelName = obj.optString("modelName", ""),
                        tokenCount = obj.optInt("tokenCount", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encryptApiKey(value: String): String {
        if (value.isBlank() || value.startsWith(encryptedKeyPrefix)) return value
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return encryptedKeyPrefix + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        if (!value.startsWith(encryptedKeyPrefix)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(encryptedKeyPrefix), Base64.NO_WRAP)
            require(payload.size > 12) { "Clé chiffrée invalide" }
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

}
