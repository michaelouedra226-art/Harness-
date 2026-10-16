package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Project
import com.example.domain.model.ProjectType
import com.example.ui.theme.*
import com.example.ui.viewmodel.NuruViewModel

@Composable
fun HomeScreen(
    viewModel: NuruViewModel,
    onCreateProjectClick: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val models by viewModel.models.collectAsState()
    var quickPrompt by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceGradient)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bonjour, Créateur 👋",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Silver)
                            )
                            Text(
                                text = "Que voulez-vous créer ?",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NuruViolet.copy(alpha = 0.2f))
                                .border(1.dp, NuruViolet.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "IA Active",
                                tint = SolarGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Fast Prompt Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DeepSpace)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = quickPrompt,
                            onValueChange = { quickPrompt = it },
                            placeholder = {
                                Text(
                                    "Décrivez un site, une app Flutter ou une API...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MutedText)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_prompt_input")
                        )

                        IconButton(
                            onClick = {
                                if (quickPrompt.isNotBlank()) {
                                    val prompt = quickPrompt
                                    quickPrompt = ""
                                    viewModel.sendMessage(prompt)
                                    viewModel.switchTab(1) // Go to Chat
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NuruGradient)
                                .testTag("home_quick_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Envoyer prompt",
                                tint = PureWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Platform Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    label = "Providers",
                    value = "${providers.count { it.isEnabled }} Actifs",
                    icon = Icons.Default.CloudQueue,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "Modèles",
                    value = "${models.size} Prêts",
                    icon = Icons.Default.Extension,
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "Projets",
                    value = "${projects.size} En cours",
                    icon = Icons.Default.Code,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6 Action Shortcuts (Grid 2x3)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Raccourcis Studio",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShortcutCard(
                        title = "Créer un projet",
                        subtitle = "Web, Flutter, Compose",
                        icon = Icons.Default.AddCircle,
                        gradient = NuruGradient,
                        onClick = onCreateProjectClick,
                        modifier = Modifier.weight(1f)
                    )
                    ShortcutCard(
                        title = "Continuer le code",
                        subtitle = "IDE & Live Preview",
                        icon = Icons.Default.PlayArrow,
                        gradient = Brush.linearGradient(listOf(NebulaBlue, Color(0xFF00C6FF))),
                        onClick = {
                            viewModel.switchTab(3) // Code IDE
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShortcutCard(
                        title = "8 Agents IA",
                        subtitle = "Architecte, Dev, Sec...",
                        icon = Icons.Default.SmartToy,
                        gradient = Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))),
                        onClick = {
                            viewModel.switchTab(4)
                            viewModel.switchHubSubTab(3)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ShortcutCard(
                        title = "Model Explorer",
                        subtitle = "Catalogue & Tests",
                        icon = Icons.Default.Explore,
                        gradient = Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
                        onClick = {
                            viewModel.switchTab(4)
                            viewModel.switchHubSubTab(1)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShortcutCard(
                        title = "Fournisseurs API",
                        subtitle = "OpenAI, Ollama, BYO",
                        icon = Icons.Default.Cable,
                        gradient = Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
                        onClick = {
                            viewModel.switchTab(4)
                            viewModel.switchHubSubTab(0)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ShortcutCard(
                        title = "AI Router",
                        subtitle = "Routage & Fallback",
                        icon = Icons.Default.AltRoute,
                        gradient = AccentGoldGradient,
                        onClick = {
                            viewModel.switchTab(4)
                            viewModel.switchHubSubTab(2)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Projects Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Projets Récents",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = { viewModel.switchTab(2) }) {
                        Text(text = "Voir tout", color = NebulaBlue, fontSize = 12.sp)
                    }
                }

                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Obsidian)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Aucun projet pour le moment",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = PureWhite)
                            )
                            Text(
                                text = "Créez votre premier projet en direct avec vos agents IA.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 12.sp)
                            )
                        }
                    }
                } else {
                    projects.take(3).forEach { project ->
                        RecentProjectCard(
                            project = project,
                            onClick = {
                                viewModel.selectProject(project.id)
                                viewModel.switchTab(3) // Code IDE
                            }
                        )
                    }
                }
            }
        }

        // Suggestions IA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Suggestions Prêtes à Générer",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                SuggestionItem(
                    title = "Landing Page SaaS Dark Mode",
                    desc = "Site web one-page avec pricing table, animations Tailwind et FAQ.",
                    type = "Web · HTML/Tailwind",
                    onClick = {
                        viewModel.createProjectWithOrchestration(
                            name = "SaaS CloudPulse",
                            description = "Landing page SaaS moderne avec animations néon",
                            type = ProjectType.WEB_SITE,
                            onComplete = {}
                        )
                    }
                )

                SuggestionItem(
                    title = "App Flutter Crypto Tracker",
                    desc = "Portfolio de cryptomonnaies avec cours en temps réel et alertes.",
                    type = "Mobile · Flutter",
                    onClick = {
                        viewModel.createProjectWithOrchestration(
                            name = "CryptoPulse",
                            description = "Application mobile de suivi de crypto-actifs",
                            type = ProjectType.MOBILE_FLUTTER,
                            onComplete = {}
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun StatBadge(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Graphite)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NebulaBlue,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedText,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Graphite)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 11.sp)
            )
        }
    }
}

@Composable
fun RecentProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.type.iconEmoji,
                        fontSize = 20.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${project.files.size} fichiers · ${project.totalLinesOfCode} lignes · ${project.type.displayName}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 11.sp)
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
                    text = "Prêt",
                    color = MintSuccess,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SuggestionItem(
    title: String,
    desc: String,
    type: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Graphite)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 12.sp)
                )
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall.copy(color = NebulaBlue)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Silver
            )
        }
    }
}
