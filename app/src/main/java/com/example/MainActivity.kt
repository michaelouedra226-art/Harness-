package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.RouterSlotType
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.NuruBottomBar
import com.example.ui.components.NuruTopBar
import com.example.ui.screens.*
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NuruStudioTheme
import com.example.ui.viewmodel.NuruViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NuruStudioTheme {
                val viewModel: NuruViewModel = viewModel()
                val currentTab by viewModel.currentTab.collectAsState()
                val isGenerating by viewModel.isGenerating.collectAsState()
                val generationStep by viewModel.generationStep.collectAsState()
                val generationProgress by viewModel.generationProgress.collectAsState()
                val generationError by viewModel.generationError.collectAsState()
                val models by viewModel.models.collectAsState()
                val providers by viewModel.providers.collectAsState()
                val routerConfig by viewModel.routerConfig.collectAsState()
                val isPreviewFullScreen by viewModel.isPreviewFullScreen.collectAsState()
                val selectedProject by viewModel.selectedProject.collectAsState()
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    if (isPreviewFullScreen) {
                        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                val activeModel = routerConfig.slots[RouterSlotType.POWERFUL]?.assignedModelId
                    ?.let { selectedId -> models.firstOrNull { it.id == selectedId } }
                val activeModelName = activeModel?.name ?: "Aucun modèle"
                val activeProvider = activeModel?.let { model -> providers.firstOrNull { it.id == model.providerId } }
                val isModelReady = activeProvider?.isEnabled == true &&
                    activeProvider.testStatus == com.example.domain.model.TestStatus.SUCCESS

                var showCreateDialog by remember { mutableStateOf(false) }

                if (isPreviewFullScreen && selectedProject != null) {
                    BackHandler {
                        viewModel.setPreviewFullScreen(false)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
                        LivePreviewTab(
                            project = selectedProject!!,
                            isMobileViewport = false,
                            onToggleViewport = {},
                            isFullScreen = true,
                            onToggleFullScreen = {},
                            onExitFullScreen = { viewModel.setPreviewFullScreen(false) }
                        )
                    }
                } else {
                    BackHandler(enabled = currentTab != 0) {
                        viewModel.switchTab(0)
                    }

                    val currentTabTitle = when (currentTab) {
                        0 -> "Accueil Studio"
                        1 -> "Nuru Chat IA"
                        2 -> "Mes Projets"
                        3 -> "Nuru Code & Preview"
                        4 -> "Studio Hub"
                        else -> "Nuru Studio"
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = DeepSpace,
                        topBar = {
                            NuruTopBar(
                                currentTabTitle = currentTabTitle,
                                activeModelName = activeModelName,
                                isModelReady = isModelReady,
                                onTuneClick = {
                                    viewModel.switchTab(4)
                                    viewModel.switchHubSubTab(2) // Open AI Router
                                }
                            )
                        },
                        bottomBar = {
                            NuruBottomBar(
                                currentTab = currentTab,
                                onTabSelected = { viewModel.switchTab(it) }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DeepSpace)
                                .padding(innerPadding)
                        ) {
                            when (currentTab) {
                                0 -> HomeScreen(
                                    viewModel = viewModel,
                                    onCreateProjectClick = { showCreateDialog = true }
                                )
                                1 -> ChatScreen(viewModel = viewModel)
                                2 -> ProjectsScreen(
                                    viewModel = viewModel,
                                    onCreateProjectClick = { showCreateDialog = true }
                                )
                                3 -> CodeEditorScreen(viewModel = viewModel)
                                4 -> StudioHubScreen(viewModel = viewModel)
                            }
                        }

                        if (showCreateDialog || isGenerating) {
                            CreateProjectDialog(
                                isGenerating = isGenerating,
                                generationStep = generationStep,
                                generationProgress = generationProgress,
                                generationError = generationError,
                                onDismiss = {
                                    showCreateDialog = false
                                    viewModel.clearGenerationError()
                                },
                                onConfirm = { name, desc, type ->
                                    viewModel.createProjectWithOrchestration(
                                        name = name,
                                        description = desc,
                                        type = type,
                                        onComplete = {
                                            showCreateDialog = false
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
