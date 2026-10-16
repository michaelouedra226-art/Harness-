package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Project
import com.example.domain.model.ProjectType
import com.example.ui.theme.*
import com.example.ui.viewmodel.NuruViewModel

@Composable
fun ProjectsScreen(
    viewModel: NuruViewModel,
    onCreateProjectClick: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredProjects = remember(projects, selectedFilter) {
        when (selectedFilter) {
            "WEB" -> projects.filter { it.type == ProjectType.WEB_SITE }
            "FLUTTER" -> projects.filter { it.type == ProjectType.MOBILE_FLUTTER }
            "COMPOSE" -> projects.filter { it.type == ProjectType.MOBILE_KOTLIN_COMPOSE }
            "API" -> projects.filter { it.type == ProjectType.SCRIPT_API }
            else -> projects
        }
    }

    Scaffold(
        containerColor = DeepSpace,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProjectClick,
                containerColor = NuruViolet,
                contentColor = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("projects_fab_add")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nouveau Projet"
                    )
                    Text(
                        text = "Nouveau Projet",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Deliverable Filter Chips
            Surface(
                color = Obsidian,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "Tous (${projects.size})",
                        "WEB" to "🌐 Web",
                        "FLUTTER" to "📱 Flutter",
                        "COMPOSE" to "🤖 Compose",
                        "API" to "⚡ API"
                    )

                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NuruViolet,
                                selectedLabelColor = PureWhite,
                                containerColor = Graphite,
                                labelColor = Silver
                            )
                        )
                    }
                }
            }

            // Projects List
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Aucun projet trouvé",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = PureWhite)
                        )
                        Text(
                            text = "Créez votre projet avec le bouton ci-dessous pour déclencher l'orchestration multi-agents en direct.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Silver),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    items(filteredProjects) { project ->
                        ProjectDetailCard(
                            project = project,
                            onOpen = {
                                viewModel.selectProject(project.id)
                                viewModel.switchTab(3) // Code IDE
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectDetailCard(
    project: Project,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SlateElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = project.type.iconEmoji,
                            fontSize = 20.sp
                        )
                    }

                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = project.type.displayName,
                            style = MaterialTheme.typography.bodySmall.copy(color = NebulaBlue)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MintSuccess.copy(alpha = 0.15f))
                        .border(1.dp, MintSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = project.status.label,
                        color = MintSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Description
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Silver,
                    lineHeight = 18.sp
                )
            )

            Divider(color = GlassBorder, thickness = 1.dp)

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${project.files.size} fichiers",
                            style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataArray,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${project.totalLinesOfCode} lignes",
                            style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${project.tokensUsed / 1000}k tok",
                            style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            com.example.data.export.ProjectExporter.shareProject(context, project)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Télécharger ZIP",
                            tint = NebulaBlue
                        )
                    }

                    Button(
                        onClick = onOpen,
                        colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "Éditer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
