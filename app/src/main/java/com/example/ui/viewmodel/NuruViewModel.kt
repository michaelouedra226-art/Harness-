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

    // Project Generation Multi-Agent Orchestration State
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
        viewModelScope.launch {
            repository.sendUserMessageLive(content, targetAgent)
        }
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
