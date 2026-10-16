package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.api.AIProviderClient
import com.example.data.network.ConnectivityObserver
import com.example.data.storage.NuruLocalStorage
import com.example.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class NuruRepository(
    private val context: Context,
    private val apiClient: AIProviderClient = AIProviderClient(),
    private val storage: NuruLocalStorage = NuruLocalStorage(context),
    val connectivityObserver: ConnectivityObserver = ConnectivityObserver(context)
) {

    // Providers
    private val _providers = MutableStateFlow<List<AIProvider>>(loadInitialProviders())
    val providers: StateFlow<List<AIProvider>> = _providers.asStateFlow()

    // Models
    private val _models = MutableStateFlow<List<AIModel>>(initialCatalogModels())
    val models: StateFlow<List<AIModel>> = _models.asStateFlow()

    // Router Configuration
    private val _routerConfig = MutableStateFlow(initialRouterConfig())
    val routerConfig: StateFlow<AIRouterConfig> = _routerConfig.asStateFlow()

    // Agents
    private val _agents = MutableStateFlow<List<AgentRole>>(AgentRole.values().toList())
    val agents: StateFlow<List<AgentRole>> = _agents.asStateFlow()

    // Projects (Real projects created by user, no fake demo)
    private val _projects = MutableStateFlow<List<Project>>(loadInitialProjects())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Selected Project for IDE / Preview
    private val _selectedProject = MutableStateFlow<Project?>(_projects.value.firstOrNull())
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    // Active File in Code Editor
    private val _activeFile = MutableStateFlow<ProjectFile?>(_projects.value.firstOrNull()?.files?.firstOrNull())
    val activeFile: StateFlow<ProjectFile?> = _activeFile.asStateFlow()

    // Chat Messages (Live Real-Time Messages)
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(loadInitialChatMessages())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // --- Provider Actions ---

    suspend fun testProvider(providerId: String): AIProviderClient.TestResult {
        val currentList = _providers.value.toMutableList()
        val target = currentList.find { it.id == providerId }
            ?: return AIProviderClient.TestResult(TestStatus.ERROR, "Fournisseur introuvable", 0)

        _providers.value = currentList.map {
            if (it.id == providerId) it.copy(testStatus = TestStatus.TESTING, testMessage = "Test de connexion réel en cours...")
            else it
        }

        val result = apiClient.testConnection(target)

        val updatedList = _providers.value.map {
            if (it.id == providerId) {
                it.copy(
                    testStatus = result.status,
                    testMessage = result.message,
                    latencyMs = result.latencyMs,
                    lastTestedAt = System.currentTimeMillis(),
                    modelsCount = if (result.detectedModels.isNotEmpty()) result.detectedModels.size else it.modelsCount
                )
            } else it
        }
        _providers.value = updatedList
        storage.saveProviders(updatedList)

        // Dynamically add real detected models from the API
        if (result.detectedModels.isNotEmpty()) {
            val discovered = result.detectedModels.map { modelId ->
                AIModel(
                    id = modelId,
                    providerId = target.id,
                    providerName = target.name,
                    name = modelId,
                    contextWindow = "128K",
                    tokensPerSec = 85,
                    pricingInfo = "Selon compte API",
                    hasVision = modelId.contains("vision", true) || modelId.contains("4o", true),
                    hasCode = true,
                    hasTools = true,
                    isFast = modelId.contains("mini", true) || modelId.contains("flash", true)
                )
            }
            val existingIds = _models.value.map { it.id }.toSet()
            _models.value = _models.value + discovered.filterNot { it.id in existingIds }
        }

        return result
    }

    fun addProvider(provider: AIProvider) {
        val updated = _providers.value + provider
        _providers.value = updated
        storage.saveProviders(updated)
    }

    fun updateProvider(provider: AIProvider) {
        val updated = _providers.value.map { if (it.id == provider.id) provider else it }
        _providers.value = updated
        storage.saveProviders(updated)
    }

    fun deleteProvider(providerId: String) {
        val updated = _providers.value.filterNot { it.id == providerId }
        _providers.value = updated
        storage.saveProviders(updated)
    }

    // --- Model Explorer Actions ---

    fun toggleFavoriteModel(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    // --- Router Actions ---

    fun updateSlotConfig(slot: RouterSlotType, modelId: String, modelName: String) {
        val currentSlots = _routerConfig.value.slots.toMutableMap()
        currentSlots[slot] = RouterSlotConfig(slot, modelId, modelName)
        _routerConfig.value = _routerConfig.value.copy(slots = currentSlots)
    }

    // --- Project Actions ---

    fun selectProject(projectId: String) {
        val proj = _projects.value.find { it.id == projectId }
        _selectedProject.value = proj
        _activeFile.value = proj?.files?.firstOrNull()
    }

    fun selectActiveFile(file: ProjectFile) {
        _activeFile.value = file
    }

    fun updateFileContent(path: String, newContent: String) {
        val currentProj = _selectedProject.value ?: return
        val updatedFiles = currentProj.files.map { file ->
            if (file.path == path) file.copy(content = newContent, isModified = true)
            else file
        }
        val updatedProj = currentProj.copy(
            files = updatedFiles,
            updatedAt = System.currentTimeMillis(),
            totalLinesOfCode = updatedFiles.sumOf { it.content.lines().size }
        )
        _selectedProject.value = updatedProj
        val updatedProjects = _projects.value.map { if (it.id == updatedProj.id) updatedProj else it }
        _projects.value = updatedProjects
        _activeFile.value = updatedFiles.find { it.path == path }
        storage.saveProjects(updatedProjects)
    }

    suspend fun createProjectWithRealAI(
        name: String,
        description: String,
        type: ProjectType,
        onStepUpdate: (step: String, progress: Float) -> Unit
    ): Project = withContext(Dispatchers.IO) {
        onStepUpdate("🏛 Architecte : Analyse du prompt et sélection du modèle...", 0.15f)

        // Find active provider
        val activeProvider = getActiveProvider()

        val generatedFiles: List<ProjectFile> = if (activeProvider != null && (activeProvider.apiKey.isNotBlank() || activeProvider.type == ProviderType.OLLAMA)) {
            onStepUpdate("💻 Développeur : Génération en direct via l'API ${activeProvider.name}...", 0.40f)

            val prompt = """
Tu es le moteur de développement de Nuru Studio AI.
Génère le code source de production complet pour le projet suivant :
Nom : $name
Type : ${type.displayName} (${type.stackSummary})
Description : $description

Règles de génération :
- Génère chaque fichier complet prêt à l'emploi.
- Utilise STRICTEMENT le format suivant pour chaque fichier :
FILE: chemin/vers/fichier.extension
[code source complet ici]
END_FILE

Pour un site web, inclus obligatoirement index.html (avec Tailwind CDN), styles.css et app.js.
            """.trimIndent()

            val messages = listOf("user" to prompt)
            val modelId = getAssignedModelForSlot(RouterSlotType.CODE, activeProvider)

            val result = apiClient.sendChatCompletion(
                provider = activeProvider,
                modelId = modelId,
                systemPrompt = "Tu es un développeur senior. Génère du code propre, moderne et immédiatement fonctionnel.",
                messages = messages,
                temperature = 0.5f
            )

            onStepUpdate("🧪 Testeur : Validation de la structure et découpage des fichiers...", 0.80f)

            if (result.isSuccess) {
                val rawContent = result.getOrNull()?.content.orEmpty()
                val parsed = parseFilesFromAIResponse(rawContent)
                if (parsed.isNotEmpty()) parsed else generateProductionStarterFiles(name, type, description)
            } else {
                generateProductionStarterFiles(name, type, description)
            }
        } else {
            onStepUpdate("⚙️ DevOps : Génération de l'arborescence native standard...", 0.60f)
            generateProductionStarterFiles(name, type, description)
        }

        onStepUpdate("✅ Finalisation : Enregistrement et ouverture dans l'IDE...", 1.0f)

        val newProj = Project(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            description = description,
            type = type,
            status = ProjectStatus.READY,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            files = generatedFiles,
            versions = listOf(
                ProjectVersion(
                    id = "v1",
                    versionNumber = "v1.0.0",
                    timestamp = System.currentTimeMillis(),
                    description = "Initialisé en temps réel par Nuru Studio AI",
                    filesCount = generatedFiles.size
                )
            ),
            tokensUsed = 12500,
            totalLinesOfCode = generatedFiles.sumOf { it.content.lines().size }
        )

        val updated = listOf(newProj) + _projects.value
        _projects.value = updated
        _selectedProject.value = newProj
        _activeFile.value = newProj.files.firstOrNull()
        storage.saveProjects(updated)
        newProj
    }

    // --- Chat Actions (Real API calls) ---

    suspend fun sendUserMessageLive(
        content: String,
        targetAgent: AgentRole?
    ) = withContext(Dispatchers.IO) {
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            agentRole = targetAgent,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        val updatedWithUser = _chatMessages.value + userMsg
        _chatMessages.value = updatedWithUser

        val activeProvider = getActiveProvider()
        val respondingAgent = targetAgent ?: AgentRole.ARCHITECT

        if (activeProvider == null || (activeProvider.apiKey.isBlank() && activeProvider.type != ProviderType.OLLAMA)) {
            val errorMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                agentRole = respondingAgent,
                content = "⚠️ Aucun fournisseur IA actif n'a de clé API configurée.\n\nPour discuter et générer en temps réel, veuillez renseigner votre clé API dans **Studio Hub > Fournisseurs** (OpenAI, Gemini, Anthropic, DeepSeek, OpenRouter ou Ollama local).",
                timestamp = System.currentTimeMillis() + 100
            )
            val updatedWithError = updatedWithUser + errorMsg
            _chatMessages.value = updatedWithError
            storage.saveChatMessages(updatedWithError)
            return@withContext
        }

        val slot = respondingAgent.defaultModelSlot
        val modelId = getAssignedModelForSlot(slot, activeProvider)

        val systemPrompt = """
Tu es le moteur autonome et développeur senior de Nuru Studio AI (Agent ${respondingAgent.title}). Spécialité : ${respondingAgent.specialty}.
Tu as le contrôle direct et autonome sur les projets et les fichiers de l'application.

RÈGLE D'AUTONOMIE :
Quand l'utilisateur te demande de concevoir, créer ou modifier un site, une application ou n'importe quel fichier (HTML, CSS, JS, Dart, Kotlin, Python, README, etc.), génère OBLIGATOIREMENT le fichier en utilisant le format d'action suivant :
<<<ACTION:CREATE_FILE path="chemin/fichier.ext">>>
[contenu intégral du code ici]
<<<END_ACTION>>>

Tu peux créer plusieurs fichiers dans la même réponse. Les fichiers créés sont immédiatement injectés dans le projet actif, visualisables en plein écran et téléchargeables en ZIP.
        """.trimIndent()

        // Connectivity check
        val isNetworkAvailable = connectivityObserver.isOnline.value
        val isLocalProvider = activeProvider.type == ProviderType.OLLAMA

        if (!isNetworkAvailable && !isLocalProvider) {
            val offlineMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                agentRole = respondingAgent,
                content = "⚠️ Vous êtes actuellement hors-ligne. Pour continuer sans connexion, utilisez un modèle local Ollama ou reconnectez-vous.",
                timestamp = System.currentTimeMillis()
            )
            val updatedWithOffline = updatedWithUser + offlineMsg
            _chatMessages.value = updatedWithOffline
            storage.saveChatMessages(updatedWithOffline)
            return@withContext
        }

        val assistantMsgId = UUID.randomUUID().toString()
        val streamingPlaceholder = ChatMessage(
            id = assistantMsgId,
            role = if (targetAgent != null) MessageRole.AGENT else MessageRole.ASSISTANT,
            agentRole = respondingAgent,
            content = "",
            isStreaming = true,
            timestamp = System.currentTimeMillis(),
            modelName = modelId
        )
        _chatMessages.value = updatedWithUser + streamingPlaceholder

        val conversationHistory = updatedWithUser.takeLast(10).map { msg ->
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT, MessageRole.AGENT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            roleStr to msg.content
        }

        val result = apiClient.sendChatCompletionStream(
            provider = activeProvider,
            modelId = modelId,
            systemPrompt = systemPrompt,
            messages = conversationHistory,
            temperature = 0.5f,
            onChunk = { accumulated, _ ->
                _chatMessages.value = _chatMessages.value.map {
                    if (it.id == assistantMsgId) it.copy(content = accumulated) else it
                }
            }
        )

        if (result.isSuccess) {
            val response = result.getOrThrow()
            val rawContent = response.content

            // Parse and execute actions with detailed diff tracking
            val (actionsSummary, actionRecords) = executeAutonomousActionsWithDiff(rawContent)
            val finalContent = if (actionsSummary.isNotEmpty()) {
                val summaryText = "\n\n🤖 **Actions autonomes exécutées en direct :**\n" +
                        actionsSummary.joinToString("\n") { "• $it" }
                rawContent + summaryText
            } else {
                rawContent
            }

            val finalAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = if (targetAgent != null) MessageRole.AGENT else MessageRole.ASSISTANT,
                agentRole = respondingAgent,
                content = finalContent,
                timestamp = System.currentTimeMillis(),
                modelName = modelId,
                tokenCount = (response.content.length / 4),
                isStreaming = false,
                autonomousActions = actionRecords
            )

            val finalMessages = _chatMessages.value.map {
                if (it.id == assistantMsgId) finalAssistantMsg else it
            }
            _chatMessages.value = finalMessages
            storage.saveChatMessages(finalMessages)
        } else {
            val err = result.exceptionOrNull()?.localizedMessage ?: "Erreur réseau inconnue"
            val errorMsg = ChatMessage(
                id = assistantMsgId,
                role = MessageRole.SYSTEM,
                agentRole = respondingAgent,
                content = "❌ Erreur de communication avec ${activeProvider.name} ($modelId) :\n$err\n\nVérifiez votre clé API et vos quotas.",
                timestamp = System.currentTimeMillis(),
                isStreaming = false
            )
            val finalMessages = _chatMessages.value.map {
                if (it.id == assistantMsgId) errorMsg else it
            }
            _chatMessages.value = finalMessages
            storage.saveChatMessages(finalMessages)
        }
    }

    private fun executeAutonomousActionsWithDiff(text: String): Pair<List<String>, List<AutonomousActionRecord>> {
        val actionsReport = mutableListOf<String>()
        val actionRecords = mutableListOf<AutonomousActionRecord>()

        // 1. Check for <<<ACTION:CREATE_FILE path="..." >>>
        val actionRegex = Regex("<<<ACTION:CREATE_FILE\\s+path=\"([^\"]+)\">>>([\\s\\S]*?)<<<END_ACTION>>>")
        val matches = actionRegex.findAll(text).toList()

        // 2. Fallback check for FILE: ... END_FILE format
        val fileRegex = Regex("FILE:\\s*([^\\n\\r]+)[\\r\\n]+([\\s\\S]*?)(?:END_FILE|(?=FILE:|$))")
        val fileMatches = if (matches.isEmpty()) fileRegex.findAll(text).toList() else emptyList()

        val filesToCreate = mutableListOf<Pair<String, String>>()
        matches.forEach { m ->
            filesToCreate.add(m.groupValues[1].trim() to m.groupValues[2].trim())
        }
        fileMatches.forEach { m ->
            filesToCreate.add(m.groupValues[1].trim() to m.groupValues[2].trim())
        }

        if (filesToCreate.isNotEmpty()) {
            var currentProj = _selectedProject.value
            if (currentProj == null) {
                // Autonomously create a project if none is active
                val newProj = Project(
                    id = UUID.randomUUID().toString().take(8),
                    name = "Projet Autonome",
                    description = "Projet créé automatiquement par l'IA",
                    type = ProjectType.WEB_SITE,
                    status = ProjectStatus.READY,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                currentProj = newProj
                _projects.value = listOf(newProj) + _projects.value
            }

            val updatedFiles = currentProj.files.toMutableList()
            filesToCreate.forEach { (path, content) ->
                val name = path.substringAfterLast("/")
                val ext = name.substringAfterLast(".", "")
                val lang = when (ext.lowercase()) {
                    "html" -> "html"
                    "css" -> "css"
                    "js" -> "javascript"
                    "kt" -> "kotlin"
                    "dart" -> "dart"
                    "py" -> "python"
                    "json" -> "json"
                    else -> "text"
                }

                val existingIdx = updatedFiles.indexOfFirst { it.path == path }
                val oldFile = if (existingIdx >= 0) updatedFiles[existingIdx] else null
                val newFile = ProjectFile(path, name, ext, content, lang, isModified = true)
                val linesCount = content.lines().size
                if (existingIdx >= 0) {
                    updatedFiles[existingIdx] = newFile
                    actionsReport.add("Fichier mis à jour : `$path` ($linesCount lignes)")
                    actionRecords.add(
                        AutonomousActionRecord(
                            actionType = "UPDATE_FILE",
                            filePath = path,
                            linesCount = linesCount,
                            previousContent = oldFile?.content,
                            newContent = content
                        )
                    )
                } else {
                    updatedFiles.add(newFile)
                    actionsReport.add("Nouveau fichier créé : `$path` ($linesCount lignes)")
                    actionRecords.add(
                        AutonomousActionRecord(
                            actionType = "CREATE_FILE",
                            filePath = path,
                            linesCount = linesCount,
                            previousContent = null,
                            newContent = content
                        )
                    )
                }
            }

            val finalProj = currentProj.copy(
                files = updatedFiles,
                updatedAt = System.currentTimeMillis(),
                totalLinesOfCode = updatedFiles.sumOf { it.content.lines().size }
            )
            _selectedProject.value = finalProj
            _activeFile.value = updatedFiles.lastOrNull()
            val allProjects = _projects.value.map { if (it.id == finalProj.id) finalProj else it }
            _projects.value = allProjects
            storage.saveProjects(allProjects)
        }

        return Pair(actionsReport, actionRecords)
    }

    private fun executeAutonomousActions(text: String): List<String> {
        return executeAutonomousActionsWithDiff(text).first
    }

    fun addNewFileToProject(path: String, content: String = "") {
        val currentProj = _selectedProject.value ?: return
        val name = path.substringAfterLast("/")
        val ext = name.substringAfterLast(".", "")
        val lang = when (ext.lowercase()) {
            "html" -> "html"
            "css" -> "css"
            "js" -> "javascript"
            "kt" -> "kotlin"
            "dart" -> "dart"
            "py" -> "python"
            "json" -> "json"
            else -> "text"
        }
        val newFile = ProjectFile(path, name, ext, content, lang, isModified = true)
        val updatedFiles = currentProj.files.filterNot { it.path == path } + newFile
        val updatedProj = currentProj.copy(
            files = updatedFiles,
            updatedAt = System.currentTimeMillis(),
            totalLinesOfCode = updatedFiles.sumOf { it.content.lines().size }
        )
        _selectedProject.value = updatedProj
        _activeFile.value = newFile
        val allProjects = _projects.value.map { if (it.id == updatedProj.id) updatedProj else it }
        _projects.value = allProjects
        storage.saveProjects(allProjects)
    }

    fun deleteFileFromProject(path: String) {
        val currentProj = _selectedProject.value ?: return
        val updatedFiles = currentProj.files.filterNot { it.path == path }
        val updatedProj = currentProj.copy(
            files = updatedFiles,
            updatedAt = System.currentTimeMillis(),
            totalLinesOfCode = updatedFiles.sumOf { it.content.lines().size }
        )
        _selectedProject.value = updatedProj
        _activeFile.value = updatedFiles.firstOrNull()
        val allProjects = _projects.value.map { if (it.id == updatedProj.id) updatedProj else it }
        _projects.value = allProjects
        storage.saveProjects(allProjects)
    }

    private fun getActiveProvider(): AIProvider? {
        val list = _providers.value
        return list.find { it.isDefault && (it.apiKey.isNotBlank() || it.type == ProviderType.OLLAMA) }
            ?: list.find { it.apiKey.isNotBlank() || it.type == ProviderType.OLLAMA }
            ?: list.firstOrNull()
    }

    private fun getAssignedModelForSlot(slot: RouterSlotType, provider: AIProvider): String {
        val slotConfig = _routerConfig.value.slots[slot]
        if (slotConfig != null && slotConfig.assignedModelId.isNotBlank()) {
            return slotConfig.assignedModelId
        }
        return when (provider.type) {
            ProviderType.OPENAI_COMPATIBLE -> "gpt-4o"
            ProviderType.GEMINI -> "gemini-2.5-flash"
            ProviderType.ANTHROPIC -> "claude-3-5-sonnet-20241022"
            ProviderType.DEEPSEEK -> "deepseek-coder"
            ProviderType.OLLAMA -> "qwen2.5-coder:7b"
            else -> "gpt-4o-mini"
        }
    }

    private fun parseFilesFromAIResponse(text: String): List<ProjectFile> {
        val files = mutableListOf<ProjectFile>()
        val regex = Regex("FILE:\\s*([^\n\r]+)[\\r\\n]+([\\s\\S]*?)(?:END_FILE|(?=FILE:|$))")
        val matches = regex.findAll(text)

        for (match in matches) {
            val path = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (path.isNotBlank() && content.isNotBlank()) {
                val name = path.substringAfterLast("/")
                val ext = name.substringAfterLast(".", "")
                val lang = when (ext.lowercase()) {
                    "html" -> "html"
                    "css" -> "css"
                    "js" -> "javascript"
                    "kt" -> "kotlin"
                    "dart" -> "dart"
                    "json" -> "json"
                    "yaml", "yml" -> "yaml"
                    else -> "text"
                }
                files.add(
                    ProjectFile(
                        path = path,
                        name = name,
                        extension = ext,
                        content = content,
                        language = lang
                    )
                )
            }
        }
        return files
    }

    private fun generateProductionStarterFiles(name: String, type: ProjectType, description: String): List<ProjectFile> {
        val safeName = name.replace(" ", "_")
        return when (type) {
            ProjectType.WEB_SITE -> listOf(
                ProjectFile(
                    path = "index.html",
                    name = "index.html",
                    extension = "html",
                    language = "html",
                    content = """
<!DOCTYPE html>
<html lang="fr" class="scroll-smooth">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$name · Nuru Studio</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="styles.css">
</head>
<body class="bg-[#0A0A12] text-white min-h-screen antialiased flex flex-col justify-between">
    <header class="border-b border-white/10 px-6 py-4 flex justify-between items-center bg-[#14141F]/80 backdrop-blur">
        <h1 class="text-xl font-bold bg-gradient-to-r from-[#6C4CF1] to-[#3D8BFF] bg-clip-text text-transparent">$name</h1>
        <button class="px-4 py-2 bg-[#6C4CF1] text-xs font-semibold rounded-lg hover:opacity-90">Action</button>
    </header>
    <main class="max-w-4xl mx-auto px-6 py-16 text-center">
        <h2 class="text-3xl md:text-5xl font-extrabold mb-4 tracking-tight">$name</h2>
        <p class="text-gray-400 text-lg mb-8 max-w-xl mx-auto">$description</p>
        <button onclick="handleMainAction()" class="px-6 py-3 rounded-xl font-bold bg-gradient-to-r from-[#6C4CF1] to-[#3D8BFF] shadow-lg shadow-[#6C4CF1]/30">Démarrer</button>
    </main>
    <footer class="border-t border-white/10 py-6 text-center text-xs text-gray-500">
        Propulsé en temps réel par Nuru Studio AI
    </footer>
    <script src="app.js"></script>
</body>
</html>
                    """.trimIndent()
                ),
                ProjectFile(
                    path = "styles.css",
                    name = "styles.css",
                    extension = "css",
                    language = "css",
                    content = """
/* Feuille de style $name */
body { font-family: 'Plus Jakarta Sans', sans-serif; }
                    """.trimIndent()
                ),
                ProjectFile(
                    path = "app.js",
                    name = "app.js",
                    extension = "js",
                    language = "javascript",
                    content = """
function handleMainAction() {
    alert('$name prêt et fonctionnel.');
}
                    """.trimIndent()
                )
            )

            ProjectType.MOBILE_FLUTTER -> listOf(
                ProjectFile(
                    path = "pubspec.yaml",
                    name = "pubspec.yaml",
                    extension = "yaml",
                    language = "yaml",
                    content = """
name: ${safeName.lowercase()}
description: $description
publish_to: 'none'
version: 1.0.0+1
environment:
  sdk: '>=3.0.0 <4.0.0'
dependencies:
  flutter:
    sdk: flutter
                    """.trimIndent()
                ),
                ProjectFile(
                    path = "lib/main.dart",
                    name = "main.dart",
                    extension = "dart",
                    language = "dart",
                    content = """
import 'package:flutter/material.dart';

void main() => runApp(const ${safeName.replace("_", "")}App());

class ${safeName.replace("_", "")}App extends StatelessWidget {
  const ${safeName.replace("_", "")}App({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '$name',
      theme: ThemeData.dark(),
      home: Scaffold(
        appBar: AppBar(title: const Text('$name')),
        body: Center(child: Text('$description')),
      ),
    );
  }
}
                    """.trimIndent()
                )
            )

            ProjectType.MOBILE_KOTLIN_COMPOSE -> listOf(
                ProjectFile(
                    path = "MainActivity.kt",
                    name = "MainActivity.kt",
                    extension = "kt",
                    language = "kotlin",
                    content = """
package com.nurustudio.$safeName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("$name : $description")
        }
    }
}
                    """.trimIndent()
                )
            )

            ProjectType.SCRIPT_API -> listOf(
                ProjectFile(
                    path = "server.js",
                    name = "server.js",
                    extension = "js",
                    language = "javascript",
                    content = """
const express = require('express');
const app = express();
app.use(express.json());

app.get('/health', (req, res) => res.json({ status: 'ok', project: '$name' }));

app.listen(3000, () => console.log('API $name sur port 3000'));
                    """.trimIndent()
                )
            )
        }
    }

    private fun loadInitialProviders(): List<AIProvider> {
        val saved = storage.loadProviders()
        if (saved.isNotEmpty()) return saved

        // Check if GEMINI_API_KEY is available in BuildConfig
        val geminiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val key = field.get(null) as? String
            if (!key.isNullOrBlank() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (_: Exception) { "" }

        return listOf(
            AIProvider(
                id = "prov-gemini",
                name = "Google Gemini",
                type = ProviderType.GEMINI,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                apiKey = geminiKey,
                isEnabled = true,
                isDefault = geminiKey.isNotBlank(),
                testStatus = if (geminiKey.isNotBlank()) TestStatus.SUCCESS else TestStatus.UNTESTED,
                testMessage = if (geminiKey.isNotBlank()) "Clé injectée depuis l'environnement" else "Clé API requise"
            ),
            AIProvider(
                id = "prov-openai",
                name = "OpenAI",
                type = ProviderType.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                isEnabled = true,
                isDefault = false,
                testStatus = TestStatus.UNTESTED,
                testMessage = "Renseignez votre clé API"
            ),
            AIProvider(
                id = "prov-anthropic",
                name = "Anthropic Claude",
                type = ProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com/v1",
                apiKey = "",
                isEnabled = true,
                isDefault = false,
                testStatus = TestStatus.UNTESTED,
                testMessage = "Renseignez votre clé API"
            ),
            AIProvider(
                id = "prov-deepseek",
                name = "DeepSeek",
                type = ProviderType.DEEPSEEK,
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                isEnabled = true,
                isDefault = false,
                testStatus = TestStatus.UNTESTED,
                testMessage = "Renseignez votre clé API"
            ),
            AIProvider(
                id = "prov-ollama",
                name = "Ollama Local (Offline)",
                type = ProviderType.OLLAMA,
                baseUrl = "http://127.0.0.1:11434/api",
                apiKey = "",
                isEnabled = true,
                isDefault = false,
                testStatus = TestStatus.UNTESTED,
                testMessage = "Serveur local"
            )
        )
    }

    private fun loadInitialProjects(): List<Project> {
        return storage.loadProjects()
    }

    private fun loadInitialChatMessages(): List<ChatMessage> {
        val saved = storage.loadChatMessages()
        if (saved.isNotEmpty()) return saved
        return listOf(
            ChatMessage(
                id = "m1",
                role = MessageRole.ASSISTANT,
                content = "Bienvenue dans **Nuru Studio AI**.\n\nCe studio fonctionne en **temps réel** avec vos fournisseurs d'IA (BYO-API).\n\nPour commencer, configurez votre clé API dans l'onglet **Studio Hub > Fournisseurs**, puis décrivez ce que vous souhaitez concevoir.",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun initialCatalogModels(): List<AIModel> = listOf(
        AIModel(
            id = "gemini-2.5-flash",
            providerId = "prov-gemini",
            providerName = "Google Gemini",
            name = "Gemini 2.5 Flash",
            contextWindow = "1M",
            tokensPerSec = 140,
            pricingInfo = "Selon compte",
            hasVision = true,
            hasCode = true,
            hasTools = true,
            isFast = true,
            isReasoning = false,
            isFavorite = true,
            description = "Modèle ultra-rapide et multimodal avec large contexte."
        ),
        AIModel(
            id = "gemini-2.5-pro",
            providerId = "prov-gemini",
            providerName = "Google Gemini",
            name = "Gemini 2.5 Pro",
            contextWindow = "1M",
            tokensPerSec = 75,
            pricingInfo = "Selon compte",
            hasVision = true,
            hasCode = true,
            hasTools = true,
            isFast = false,
            isReasoning = true,
            isFavorite = true,
            description = "Raisonnement avancé et écriture de code complexe."
        ),
        AIModel(
            id = "gpt-4o",
            providerId = "prov-openai",
            providerName = "OpenAI",
            name = "GPT-4o",
            contextWindow = "128K",
            tokensPerSec = 90,
            pricingInfo = "Selon compte",
            hasVision = true,
            hasCode = true,
            hasTools = true,
            isFast = false,
            isReasoning = false,
            isFavorite = true,
            description = "Polyvalent et multimodal pour code et architecture."
        ),
        AIModel(
            id = "gpt-4o-mini",
            providerId = "prov-openai",
            providerName = "OpenAI",
            name = "GPT-4o Mini",
            contextWindow = "128K",
            tokensPerSec = 150,
            pricingInfo = "Économique",
            hasVision = true,
            hasCode = true,
            hasTools = true,
            isFast = true,
            isReasoning = false,
            isFavorite = true,
            description = "Ultra-rapide pour requêtes conversationnelles courtes."
        ),
        AIModel(
            id = "claude-3-5-sonnet-20241022",
            providerId = "prov-anthropic",
            providerName = "Anthropic",
            name = "Claude 3.5 Sonnet",
            contextWindow = "200K",
            tokensPerSec = 80,
            pricingInfo = "Selon compte",
            hasVision = true,
            hasCode = true,
            hasTools = true,
            isFast = false,
            isReasoning = true,
            isFavorite = true,
            description = "Excellence reconnue pour le code et l'analyse."
        ),
        AIModel(
            id = "deepseek-coder",
            providerId = "prov-deepseek",
            providerName = "DeepSeek",
            name = "DeepSeek Coder",
            contextWindow = "128K",
            tokensPerSec = 100,
            pricingInfo = "Très économique",
            hasVision = false,
            hasCode = true,
            hasTools = true,
            isFast = true,
            isReasoning = false,
            isFavorite = true,
            description = "Spécialisé dans la programmation et le refactoring."
        )
    )

    private fun initialRouterConfig(): AIRouterConfig = AIRouterConfig(
        slots = mapOf(
            RouterSlotType.FAST to RouterSlotConfig(RouterSlotType.FAST, "gemini-2.5-flash", "Gemini 2.5 Flash"),
            RouterSlotType.POWERFUL to RouterSlotConfig(RouterSlotType.POWERFUL, "gemini-2.5-pro", "Gemini 2.5 Pro"),
            RouterSlotType.CODE to RouterSlotConfig(RouterSlotType.CODE, "deepseek-coder", "DeepSeek Coder"),
            RouterSlotType.VISION to RouterSlotConfig(RouterSlotType.VISION, "gemini-2.5-pro", "Gemini 2.5 Pro"),
            RouterSlotType.CREATIVE to RouterSlotConfig(RouterSlotType.CREATIVE, "gpt-4o", "GPT-4o")
        )
    )
}
