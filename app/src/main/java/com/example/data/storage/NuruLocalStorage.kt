package com.example.data.storage

import android.content.Context
import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class NuruLocalStorage(private val context: Context) {

    private val providersFile get() = File(context.filesDir, "nuru_providers.json")
    private val projectsFile get() = File(context.filesDir, "nuru_projects.json")
    private val chatFile get() = File(context.filesDir, "nuru_chat.json")

    fun saveProviders(providers: List<AIProvider>) {
        try {
            val array = JSONArray()
            providers.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("type", p.type.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.apiKey)
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
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AIProvider(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = ProviderType.valueOf(obj.getString("type")),
                        baseUrl = obj.getString("baseUrl"),
                        apiKey = obj.getString("apiKey"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isDefault = obj.optBoolean("isDefault", false),
                        testStatus = TestStatus.valueOf(obj.optString("testStatus", TestStatus.UNTESTED.name)),
                        testMessage = obj.optString("testMessage", ""),
                        latencyMs = obj.optLong("latencyMs", 0),
                        modelsCount = obj.optInt("modelsCount", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
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
}
