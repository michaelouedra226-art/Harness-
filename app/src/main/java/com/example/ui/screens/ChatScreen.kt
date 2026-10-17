package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChatContentSanitizer
import com.example.domain.model.ChatProgress
import com.example.domain.model.AgentRole
import com.example.domain.model.ChatMessage
import com.example.domain.model.MessageRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.NuruViewModel
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    viewModel: NuruViewModel
) {
    val messages by viewModel.chatMessages.collectAsState()
    val chatProgress by viewModel.chatProgress.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()
    val activeFile by viewModel.activeFile.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var selectedAgent by remember { mutableStateOf<AgentRole?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Agent Filter Chips Bar
        Surface(
            color = Obsidian,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedAgent == null,
                        onClick = { selectedAgent = null },
                        label = { Text("Assistant général") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NuruViolet,
                            selectedLabelColor = PureWhite,
                            containerColor = Graphite,
                            labelColor = Silver
                        )
                    )
                }

                items(agents) { agent ->
                    FilterChip(
                        selected = selectedAgent == agent,
                        onClick = { selectedAgent = if (selectedAgent == agent) null else agent },
                        label = { Text("${agent.iconEmoji} ${agent.title}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NebulaBlue,
                            selectedLabelColor = PureWhite,
                            containerColor = Graphite,
                            labelColor = Silver
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Obsidian)
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val project = selectedProject
            Text(
                text = if (project == null) {
                    "Contexte IA : aucun projet actif"
                } else {
                    "${project.name} · ${project.files.size} fichier(s) · extraits assainis transmis (max. 24 000 caractères)"
                },
                style = MaterialTheme.typography.labelSmall.copy(color = MutedText),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    viewModel.switchTab(3)
                    viewModel.switchIdeSubTab(0)
                    activeFile?.let(viewModel::selectActiveFile)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Fichiers", fontSize = 11.sp)
            }
        }

        if (chatProgress != null) {
            ChatProgressBanner(
                progress = chatProgress!!,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatMessageBubble(
                    message = message,
                    onCopy = { textToCopy ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Nuru Message", textToCopy)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Texte copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Action Quick Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quickPrompts = listOf(
                "Générer un site vitrine responsive" to AgentRole.DEVELOPER,
                "Créer une app mobile Flutter" to AgentRole.DEVELOPER,
                "Auditer la sécurité et les secrets" to AgentRole.SECURITY,
                "Expliquer l'architecture du projet" to AgentRole.ARCHITECT
            )

            items(quickPrompts) { (prompt, agent) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Graphite)
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .clickable(enabled = chatProgress?.isComplete != false) {
                            viewModel.sendMessage(prompt, agent)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                    )
                }
            }
        }

        // Bottom Input Composer
        Surface(
            color = Obsidian,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (selectedAgent != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "Dédié à :",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedText)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NuruViolet.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${selectedAgent?.iconEmoji} ${selectedAgent?.title}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NebulaBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Graphite)
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        readOnly = chatProgress?.isComplete == false,
                        placeholder = {
                            Text(
                                "Décrivez le résultat souhaité ; l’IA choisira les fichiers...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MutedText)
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
                            .testTag("chat_input_field")
                    )

                    IconButton(
                        enabled = chatProgress?.isComplete != false,
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val text = inputText
                                inputText = ""
                                viewModel.sendMessage(text, selectedAgent)
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NuruGradient)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Envoyer message",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatProgressBanner(progress: ChatProgress, modifier: Modifier = Modifier) {
    var currentTime by remember(progress.startedAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress.startedAtMillis, progress.isComplete) {
        while (!progress.isComplete) {
            currentTime = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val endTime = progress.completedAtMillis ?: currentTime
    val elapsedSeconds = ((endTime - progress.startedAtMillis).coerceAtLeast(0L) / 1_000L).toInt()
    val elapsedLabel = if (elapsedSeconds < 60) "${elapsedSeconds}s" else "${elapsedSeconds / 60} min ${elapsedSeconds % 60}s"
    val firstResponseLabel = progress.firstChunkAtMillis?.let {
        val seconds = ((it - progress.startedAtMillis).coerceAtLeast(0L) / 1_000L).toInt()
        "Première réponse après ${seconds}s"
    } ?: if (progress.isComplete) "Aucun fragment reçu" else "Attente de la première réponse · $elapsedLabel"
    val providerLatencyLabel = progress.providerLatencyMillis?.let { "Temps API : ${it / 1_000}s" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Graphite,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (progress.isComplete) {
                    Icon(
                        imageVector = if (progress.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (progress.isError) Color(0xFFFF6B6B) else NebulaBlue,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NebulaBlue)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(progress.stage, style = MaterialTheme.typography.labelMedium.copy(color = PureWhite, fontWeight = FontWeight.SemiBold))
                    val routeLabel = listOf(progress.providerName, progress.modelName).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(
                        text = listOfNotNull(routeLabel.takeIf { it.isNotBlank() }, firstResponseLabel, providerLatencyLabel, "Durée : $elapsedLabel").joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                    )
                    if (progress.receivedCharacters > 0) {
                        Text(
                            "${progress.receivedCharacters} caractères reçus · contenu masqué dans le chat",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedText)
                        )
                    }
                }
            }
            if (!progress.isComplete) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = NebulaBlue,
                    trackColor = Obsidian
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onCopy: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val visibleContent = if (isUser) message.content else ChatContentSanitizer.sanitize(
        message.content,
        filesWereSaved = message.autonomousActions.isNotEmpty()
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Agent or Role Tag
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(NuruViolet),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message.agentRole?.iconEmoji ?: "⚡",
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = message.agentRole?.title ?: "Assistant Nuru",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = NebulaBlue
                    )
                )

                if (message.modelName.isNotBlank()) {
                    Text(
                        text = "· ${message.modelName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MutedText)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) NuruViolet
                    else Obsidian
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) NuruViolet else GlassBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.isStreaming) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NuruViolet)
                        Text(
                            "Suivez l’étape et le temps dans le panneau ci-dessus. Le code reste masqué dans le chat.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                        )
                    }
                } else {
                    Text(
                        text = visibleContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = PureWhite,
                            lineHeight = 22.sp
                        )
                    )
                }

                if (!isUser && !message.isStreaming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onCopy(visibleContent) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copier le message",
                                tint = Silver,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
