package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.NuruRepository
import com.example.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class NuruViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NuruRepository(application.applicationContext)

    private val quotedSecretAssignment = Regex(
        """(?i)([\"']?(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|secret|password|authorization|private[_-]?key)[\"']?\s*[:=]\s*[\"'])([^\"'\r\n]+)([\"'])"""
    )
    private val plainSecretAssignment = Regex(
        """(?im)^(\s*(?:API[_-]?KEY|ACCESS[_-]?TOKEN|REFRESH[_-]?TOKEN|CLIENT[_-]?SECRET|SECRET|PASSWORD|AUTHORIZATION|PRIVATE[_-]?KEY)\s*[:=]\s*)\S+"""
    )
    private val commonSecretToken = Regex(
        """(?i)(?:sk-[A-Za-z0-9_-]{12,}|AIza[0-9A-Za-z_-]{25,}|gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})"""
    )
    private val privateKeyBlock = Regex("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----")

    init {
        viewModelScope.launch { repository.refreshConfiguredProviders() }
    }

    // Tab Navigation: 0 = Home, 1 = Chat, 2 = Projects, 3 = Code/Preview, 4 = Studio Hub
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // Sub-tab in Studio Hub: 0 = Providers, 1 = Model Explorer, 2 = AI Router, 3 = Agents
    private val _hubSubTab = MutableStateFlow(0)
    val hubSubTab: StateFlow<Int> = _hubSubTab.asStateFlow()

    // Sub-tab in IDE: 0 = Code Editor, 1 = Live Preview, 2 = Terminal, 3 = Git
    private val _ideSubTab = MutableStateFlow(0)
    val ideSubTab: StateFlow<Int> = _ideSubTab.asStateFlow()

    // Search query in Model Explorer
    private val _modelSearchQuery = MutableStateFlow("")
    val modelSearchQuery: StateFlow<String> = _modelSearchQuery.asStateFlow()

    // Filter in Model Explorer
    private val _modelFilter = MutableStateFlow("ALL")
    val modelFilter: StateFlow<String> = _modelFilter.asStateFlow()

    // State from repository
    val providers = repository.providers
    val models = repository.models
    val routerConfig = repository.routerConfig
    val agents = repository.agents
    val projects = repository.projects
    val selectedProject = repository.selectedProject
    val activeFile = repository.activeFile
    val chatMessages = repository.chatMessages
    val chatProgress = repository.chatProgress

    // AI project generation state
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationStep = MutableStateFlow("")
    val generationStep: StateFlow<String> = _generationStep.asStateFlow()

    private val _generationProgress = MutableStateFlow(0f)
    val generationProgress: StateFlow<Float> = _generationProgress.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    // Preview Full Screen Mode
    private val _isPreviewFullScreen = MutableStateFlow(false)
    val isPreviewFullScreen: StateFlow<Boolean> = _isPreviewFullScreen.asStateFlow()

    // Terminal History
    private val _terminalLogs = MutableStateFlow(listOf("Terminal de projet — aperçu local uniquement.", "Type 'help' for available commands."))
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    fun switchTab(tab: Int) {
        _currentTab.value = tab
    }

    fun switchHubSubTab(subTab: Int) {
        _hubSubTab.value = subTab
    }

    fun switchIdeSubTab(subTab: Int) {
        _ideSubTab.value = subTab
    }

    fun setModelSearchQuery(query: String) {
        _modelSearchQuery.value = query
    }

    fun setModelFilter(filter: String) {
        _modelFilter.value = filter
    }

    fun testProvider(providerId: String) {
        viewModelScope.launch {
            repository.testProvider(providerId)
        }
    }

    fun addProvider(
        name: String,
        type: ProviderType,
        baseUrl: String,
        apiKey: String
    ) {
        val newProvider = AIProvider(
            id = "prov-${UUID.randomUUID().toString().take(6)}",
            name = name.ifBlank { type.displayName },
            type = type,
            baseUrl = baseUrl.ifBlank { type.defaultBaseUrl },
            apiKey = apiKey,
            isEnabled = true,
            isDefault = false,
            testStatus = TestStatus.UNTESTED,
            testMessage = "Prêt à être testé"
        )
        viewModelScope.launch {
            repository.addProvider(newProvider)
            repository.testProvider(newProvider.id)
        }
    }

    fun updateProviderSettings(providerId: String, baseUrl: String, newKey: String) {
        val current = providers.value.find { it.id == providerId } ?: return
        val updated = current.copy(
            baseUrl = baseUrl.trim(),
            apiKey = newKey.ifBlank { current.apiKey },
            testStatus = TestStatus.UNTESTED,
            testMessage = "Configuration mise à jour"
        )
        viewModelScope.launch {
            repository.updateProvider(updated)
            repository.testProvider(providerId)
        }
    }

    fun deleteProvider(providerId: String) {
        repository.deleteProvider(providerId)
    }

    fun toggleFavoriteModel(modelId: String) {
        repository.toggleFavoriteModel(modelId)
    }

    fun selectModel(model: AIModel) {
        repository.selectModelForAllSlots(model)
    }

    fun testModel(model: AIModel, prompt: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.testModel(model, prompt)
            onComplete(result.fold({ it.content }, { "Erreur : ${it.localizedMessage ?: "échec de l’appel API"}" }))
        }
    }

    fun updateRouterSlot(slot: RouterSlotType, modelId: String, modelName: String) {
        repository.updateSlotConfig(slot, modelId, modelName)
    }

    fun selectProject(projectId: String) {
        repository.selectProject(projectId)
    }

    fun selectActiveFile(file: ProjectFile) {
        repository.selectActiveFile(file)
    }

    fun setPreviewFullScreen(fullScreen: Boolean) {
        _isPreviewFullScreen.value = fullScreen
    }

    fun addNewFile(path: String, content: String = "") {
        repository.addNewFileToProject(path, content)
    }

    fun deleteFile(path: String) {
        repository.deleteFileFromProject(path)
    }

    fun exportProjectZip(context: android.content.Context) {
        val proj = selectedProject.value ?: return
        com.example.data.export.ProjectExporter.shareProject(context, proj)
    }

    fun exportActiveFile(context: android.content.Context) {
        val file = activeFile.value ?: return
        com.example.data.export.ProjectExporter.shareFile(context, file)
    }

    fun updateActiveFileContent(newContent: String) {
        val currentFile = activeFile.value ?: return
        repository.updateFileContent(currentFile.path, newContent)
    }

    fun createProjectWithOrchestration(
        name: String,
        description: String,
        type: ProjectType,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationError.value = null
            _generationProgress.value = 0.05f
            _generationStep.value = "Initialisation de la tâche..."

            try {
                repository.createProjectWithRealAI(
                    name = name,
                    description = description,
                    type = type,
                    onStepUpdate = { step, progress ->
                        _generationStep.value = step
                        _generationProgress.value = progress
                    }
                )
                _currentTab.value = 3
                _ideSubTab.value = 1
                onComplete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _generationError.value = e.localizedMessage ?: "La génération du projet a échoué."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearGenerationError() {
        _generationError.value = null
    }

    fun sendMessage(content: String, targetAgent: AgentRole? = null) {
        val runtimeContext = buildRuntimeContext(targetAgent)
        viewModelScope.launch {
            val destinationTab = repository.sendUserMessageLive(content, targetAgent, runtimeContext)
            if (destinationTab != null) {
                _currentTab.value = destinationTab
                if (destinationTab == 3) _ideSubTab.value = 0
            }
        }
    }

    private fun buildRuntimeContext(targetAgent: AgentRole?): String = buildString {
        val tabName = when (_currentTab.value) {
            0 -> "Accueil"
            1 -> "Chat IA"
            2 -> "Projets"
            3 -> "Nuru Code"
            4 -> "Studio Hub"
            else -> "Écran inconnu"
        }
        appendLine("Application : Nuru Studio Android")
        appendLine("Écran actif : $tabName (index ${_currentTab.value})")
        if (_currentTab.value == 3) appendLine("Onglet IDE : ${listOf("Éditeur", "Aperçu", "Terminal", "Git").getOrElse(_ideSubTab.value) { "Inconnu" }}")
        if (_currentTab.value == 4) appendLine("Onglet Studio Hub : ${listOf("Fournisseurs", "Modèles", "AI Router", "Agents").getOrElse(_hubSubTab.value) { "Inconnu" }}")
        appendLine("Profil appelé : ${targetAgent?.title ?: "Assistant général (slot Architecte)"}")
        appendLine("Connexion Internet disponible : ${repository.connectivityObserver.isOnline.value}")
        appendLine("Écran d’aperçu plein écran : ${_isPreviewFullScreen.value}")
        appendLine("Génération en cours : ${_isGenerating.value} | étape : ${_generationStep.value.ifBlank { "aucune" }} | erreur : ${_generationError.value ?: "aucune"}")
        appendLine("Recherche modèles : '${_modelSearchQuery.value}' | filtre : ${_modelFilter.value}")
        appendLine("Derniers événements du terminal : ${_terminalLogs.value.takeLast(10).joinToString(" | ").ifBlank { "aucun" }}")
        appendLine("Sécurité : les clés de fournisseurs enregistrées ne sont jamais incluses; les fichiers manifestement sensibles sont exclus et les motifs courants de secrets masqués.")

        val project = selectedProject.value
        appendLine("Projets connus : ${projects.value.joinToString { it.name }.ifBlank { "aucun" }}")
        if (project == null) {
            appendLine("Projet actif : aucun")
        } else {
            appendLine("Projet actif : ${project.name} | type=${project.type.displayName} | état=${project.status.label}")
            appendLine("Description du projet : ${project.description}")
            appendLine("Nombre de fichiers : ${project.files.size}")
            appendLine("Arborescence : ${project.files.joinToString { it.path }.ifBlank { "vide" }}")
            appendLine("Fichier actif : ${activeFile.value?.path ?: "aucun"}")
            appendLine("Contenu du projet (extrait, budget max. 24 000 caractères) :")
            var remaining = 24_000
            val orderedFiles = project.files.sortedByDescending { it.path == activeFile.value?.path }
            var sensitiveFilesOmitted = false
            for (file in orderedFiles) {
                if (remaining <= 0) break
                val fileName = file.path.substringAfterLast('/').lowercase()
                val isSensitiveFile = fileName.startsWith(".env") || fileName.contains("secret") ||
                    fileName.contains("credential") || fileName.endsWith(".pem") ||
                    fileName.endsWith(".key") || fileName.endsWith(".keystore") || fileName == "local.properties"
                if (isSensitiveFile) {
                    sensitiveFilesOmitted = true
                    appendLine("--- FILE ${file.path} : contenu sensible omis ---")
                    continue
                }
                val safeContent = redactSecrets(file.content)
                val snippet = safeContent.take(minOf(safeContent.length, remaining, 6_000))
                appendLine("--- FILE ${file.path} (${file.language}) ---")
                appendLine(snippet)
                if (snippet.length < safeContent.length) appendLine("[Contenu tronqué pour respecter le budget de contexte]")
                remaining -= snippet.length
            }
            if (sensitiveFilesOmitted) appendLine("[Le contenu de certains fichiers sensibles a été omis.]")
            if (project.files.sumOf { it.content.length } > 24_000) {
                appendLine("[Certains contenus de fichiers ne sont pas inclus. Utilise la liste des fichiers fournie et n’affirme pas avoir lu le contenu omis.]")
            }
        }

        appendLine("Fournisseurs configurés :")
        if (providers.value.isEmpty()) appendLine("- aucun")
        providers.value.forEach { provider ->
            appendLine("- ${provider.name} | type=${provider.type.displayName} | activé=${provider.isEnabled} | statut=${provider.testStatus} | modèles=${provider.modelsCount}")
        }
        appendLine("Modèles découverts : ${models.value.take(80).joinToString { "${it.name} (${it.providerName})" }.ifBlank { "aucun" }}")
        appendLine("Modèles au-delà des 80 premiers : ${maxOf(0, models.value.size - 80)}")
        appendLine("Routage des slots :")
        routerConfig.value.slots.forEach { (slot, config) ->
            val model = models.value.firstOrNull { it.id == config.assignedModelId }
            appendLine("- ${slot.title} : ${model?.let { "${it.name} (${it.providerName})" } ?: "non configuré"}")
        }
    }

    private fun redactSecrets(source: String): String {
        var safe = privateKeyBlock.replace(source, "[PRIVATE KEY REDACTED]")
        safe = quotedSecretAssignment.replace(safe) { match -> "${match.groupValues[1]}[REDACTED]${match.groupValues[3]}" }
        safe = plainSecretAssignment.replace(safe) { match -> "${match.groupValues[1]}[REDACTED]" }
        return commonSecretToken.replace(safe, "[REDACTED TOKEN]")
    }

    fun executeTerminalCommand(input: String) {
        val cmd = input.trim()
        val currentLogs = _terminalLogs.value.toMutableList()
        currentLogs.add("$ $cmd")

        val parts = cmd.split(" ")
        val mainCmd = parts.firstOrNull()?.lowercase() ?: ""
        when (mainCmd) {
            "help" -> {
                currentLogs.add("Available here: tree, status, clear, help")
                currentLogs.add("run/build/test/git are not connected to a real shell or repository in this app yet.")
            }
            "run", "serve" -> {
                currentLogs.add("Not executed: this terminal is not connected to a shell. Export the project to run it in a real development environment.")
            }
            "build" -> {
                currentLogs.add("Not executed: no project build toolchain is connected to this Android app.")
            }
            "test" -> {
                currentLogs.add("Not executed: tests require a project-specific runner, which is not connected yet.")
            }
            "git" -> {
                currentLogs.add("Not executed: this project workspace is not connected to a Git repository.")
            }
            "status" -> {
                val project = selectedProject.value
                if (project == null) currentLogs.add("No active project.")
                else currentLogs.add("${project.name} · ${project.status} · ${project.files.size} fichier(s)")
            }
            "tree" -> {
                val proj = selectedProject.value
                if (proj != null) {
                    currentLogs.add(proj.name)
                    proj.files.forEach { currentLogs.add("├── ${it.path}") }
                } else {
                    currentLogs.add("No active project.")
                }
            }
            "clear" -> {
                currentLogs.clear()
            }
            else -> {
                currentLogs.add("Command not found: $cmd. Type 'help' for available commands.")
            }
        }
        _terminalLogs.value = currentLogs
    }
}
