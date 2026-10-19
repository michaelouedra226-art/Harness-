package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.AgentRole
import com.example.domain.model.HermesApprovalRequest
import com.example.domain.model.Project
import com.example.domain.model.ProjectFile
import com.example.domain.model.ProjectPathValidator
import com.example.ui.theme.*
import com.example.ui.viewmodel.NuruViewModel
import java.io.ByteArrayInputStream

@Composable
fun CodeEditorScreen(
    viewModel: NuruViewModel
) {
    val selectedProject by viewModel.selectedProject.collectAsState()
    val activeFile by viewModel.activeFile.collectAsState()
    val ideSubTab by viewModel.ideSubTab.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val terminalApproval by viewModel.terminalApproval.collectAsState()
    val isTerminalRunning by viewModel.isTerminalRunning.collectAsState()
    val context = LocalContext.current

    var editorContent by remember(activeFile) { mutableStateOf(activeFile?.content.orEmpty()) }
    var terminalInput by remember { mutableStateOf("") }
    var isMobileViewport by remember { mutableStateOf(true) }
    var showNewFileDialog by remember { mutableStateOf(false) }

    val project = selectedProject

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Aucun projet sélectionné",
                    style = MaterialTheme.typography.titleMedium.copy(color = Silver)
                )
                Button(
                    onClick = { viewModel.switchTab(2) },
                    colors = ButtonDefaults.buttonColors(containerColor = NuruViolet)
                ) {
                    Text("Ouvrir la liste des projets")
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Project Header & Sub-Tabs
        Surface(
            color = Obsidian,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = project.type.iconEmoji,
                            fontSize = 16.sp
                        )
                        Column {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            )
                            Text(
                                text = "${project.files.size} fichiers · ${project.totalLinesOfCode} lignes",
                                style = MaterialTheme.typography.labelSmall.copy(color = Silver)
                            )
                        }
                    }

                    // Actions: Download ZIP, Share File, Save
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Download full project ZIP
                        Button(
                            onClick = { viewModel.exportProjectZip(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                        }

                        // Share active file
                        IconButton(
                            onClick = { viewModel.exportActiveFile(context) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Télécharger fichier", tint = Silver, modifier = Modifier.size(16.dp))
                        }

                        // Save Modified indicator
                        if (activeFile?.isModified == true || editorContent != activeFile?.content) {
                            Button(
                                onClick = { viewModel.updateActiveFileContent(editorContent) },
                                colors = ButtonDefaults.buttonColors(containerColor = MintSuccess),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Enregistrer", fontSize = 11.sp, color = DeepSpace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sub-Tabs Bar: Éditeur, Aperçu, Terminal, Git
                TabRow(
                    selectedTabIndex = ideSubTab,
                    containerColor = Obsidian,
                    contentColor = NebulaBlue,
                    divider = { Divider(color = GlassBorder) }
                ) {
                    Tab(
                        selected = ideSubTab == 0,
                        onClick = { viewModel.switchIdeSubTab(0) },
                        text = { Text("Éditeur", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = ideSubTab == 1,
                        onClick = { viewModel.switchIdeSubTab(1) },
                        text = { Text("Aperçu Live", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = ideSubTab == 2,
                        onClick = { viewModel.switchIdeSubTab(2) },
                        text = { Text("Terminal", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = ideSubTab == 3,
                        onClick = { viewModel.switchIdeSubTab(3) },
                        text = { Text("Git", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Sub-Tab Content
        when (ideSubTab) {
            0 -> {
                // CODE EDITOR
                Column(modifier = Modifier.fillMaxSize()) {
                    // Files selector bar + New File button
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Graphite)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuruViolet)
                                    .clickable { showNewFileDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PureWhite, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Fichier", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(project.files) { file ->
                            val isSelected = file.path == activeFile?.path
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Obsidian else SlateElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) NuruViolet else GlassBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.updateActiveFileContent(editorContent)
                                        viewModel.selectActiveFile(file)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (isSelected) NebulaBlue else Silver,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) PureWhite else Silver,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Mobile Dev Keyboard Toolbar
                    val quickChars = listOf("Tab", "{", "}", "(", ")", "<", ">", ";", "/", "=", "\"", ".", "$", ":")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Obsidian)
                            .border(1.dp, GlassBorder)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickChars) { char ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Graphite)
                                    .clickable {
                                        editorContent += if (char == "Tab") "  " else char
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = char,
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // AI Quick Copilot Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSpace)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.sendMessage(
                                    "Améliore et optimise ce code du fichier ${activeFile?.name} :\n```\n${editorContent.take(500)}\n```",
                                    AgentRole.DEVELOPER
                                )
                                viewModel.switchTab(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("✨ Optimiser IA", fontSize = 11.sp, color = NebulaBlue)
                        }

                        Button(
                            onClick = {
                                viewModel.sendMessage(
                                    "Audite et débugge les erreurs possibles dans ce code :\n```\n${editorContent.take(500)}\n```",
                                    AgentRole.DEBUGGER
                                )
                                viewModel.switchTab(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("🐞 Déboguer IA", fontSize = 11.sp, color = AmberWarning)
                        }

                        Button(
                            onClick = {
                                viewModel.sendMessage(
                                    "Écris des tests complets pour le fichier ${activeFile?.name}",
                                    AgentRole.TESTER
                                )
                                viewModel.switchTab(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("🧪 Tests IA", fontSize = 11.sp, color = MintSuccess)
                        }
                    }

                    // Code Editor Text Area with Line Numbers
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Obsidian)
                            .padding(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            val lines = editorContent.lines()
                            val lineNumbersText = lines.indices.joinToString("\n") { (it + 1).toString() }
                            Text(
                                text = lineNumbersText,
                                style = CodeTextStyle.copy(
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .verticalScroll(rememberScrollState())
                            )

                            Divider(
                                color = GlassBorder,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                            )

                            TextField(
                                value = editorContent,
                                onValueChange = { editorContent = it },
                                textStyle = CodeTextStyle.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = PureWhite
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("code_editor_textarea")
                            )
                        }
                    }
                }
            }

            1 -> {
                // LIVE PREVIEW TAB
                LivePreviewTab(
                    project = project,
                    isMobileViewport = isMobileViewport,
                    onToggleViewport = { isMobileViewport = !isMobileViewport },
                    onToggleFullScreen = { viewModel.setPreviewFullScreen(true) }
                )
            }

            2 -> {
                // TERMINAL TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF07070D))
                        .padding(12.dp)
                ) {
                    Text(
                        "Terminal Hermes distant : les commandes agentiques nécessitent un modèle Hermes dans le slot Code. Le workspace distant n’est pas le terminal local et ne modifie pas les fichiers de l’application.",
                        style = MaterialTheme.typography.labelSmall.copy(color = AmberWarning),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (isTerminalRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            color = NuruViolet,
                            trackColor = SlateElevated
                        )
                    }
                    terminalApproval?.let { request ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            color = Graphite,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, AmberWarning)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Hermes demande une approbation · ${request.toolName.ifBlank { "outil" }}",
                                    color = AmberWarning,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (request.commandPreview.isNotBlank()) {
                                    Text(request.commandPreview.take(600), color = Silver, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if ("once" in request.availableChoices) {
                                        Button(
                                            onClick = {
                                                viewModel.respondToHermesApproval(request, "once") { error ->
                                                    if (error != null) android.widget.Toast.makeText(context, "Approbation non transmise : $error", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("Autoriser une fois", fontSize = 11.sp) }
                                    }
                                    if ("deny" in request.availableChoices) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.respondToHermesApproval(request, "deny") { error ->
                                                    if (error != null) android.widget.Toast.makeText(context, "Refus non transmis : $error", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) { Text("Refuser", fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                    }
                    val quickCommands = listOf("help", "tree", "status", "clear")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickCommands) { cmd ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SlateElevated)
                                    .clickable { viewModel.executeTerminalCommand(cmd) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cmd,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NebulaBlue
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Obsidian)
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(terminalLogs) { log ->
                            Text(
                                text = log,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when {
                                    log.startsWith("$") -> SolarGold
                                    log.contains("[SUCCESS]") || log.contains("✓") -> MintSuccess
                                    log.contains("[INFO]") -> NebulaBlue
                                    log.contains("Error") -> CoralError
                                    else -> PureWhite
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Graphite)
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$ ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NebulaBlue
                        )
                        TextField(
                            value = terminalInput,
                            onValueChange = { terminalInput = it },
                            placeholder = { Text("Commande (ex. npm test, build, ls)...", fontSize = 12.sp, color = MutedText) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("terminal_input_field"),
                            enabled = !isTerminalRunning
                        )
                        IconButton(
                            enabled = !isTerminalRunning,
                            onClick = {
                                if (terminalInput.isNotBlank()) {
                                    val cmd = terminalInput
                                    terminalInput = ""
                                    viewModel.executeTerminalCommand(cmd)
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardReturn, contentDescription = "Exécuter", tint = PureWhite)
                        }
                    }
                }
            }

            3 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = Obsidian,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Git n’est pas connecté", fontWeight = FontWeight.Bold, color = PureWhite)
                            Text(
                                "Les fichiers de ce projet sont enregistrés dans l’application, mais aucun dépôt distant, aucune branche ni aucun commit Git n’est configuré. Aucun commit n’a été créé. ",
                                style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                            )
                            Text(
                                "Pour l’instant, utilisez l’export ZIP du projet pour récupérer vos fichiers.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MutedText)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.switchIdeSubTab(0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir les fichiers du projet")
                    }
                }
            }
        }
    }

    // Modal Create New File Dialog
    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onConfirm = { path, content ->
                viewModel.addNewFile(path, content)
                showNewFileDialog = false
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewTab(
    project: Project,
    isMobileViewport: Boolean = true,
    onToggleViewport: () -> Unit = {},
    isFullScreen: Boolean = false,
    onToggleFullScreen: () -> Unit = {},
    onExitFullScreen: () -> Unit = {}
) {
    val htmlEntry = remember(project.id, project.files) {
        project.files.firstOrNull { it.path.equals("index.html", ignoreCase = true) }
            ?: project.files.firstOrNull { it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true) }
    }
    val htmlContent = remember(project.id, project.files) {
        htmlEntry?.content ?: buildMergedHtmlPreview(project)
    }
    val entryPath = htmlEntry?.path ?: "__nuru_preview__.html"
    val previewFiles = remember(project.id, project.files, htmlContent, entryPath) {
        project.files.mapNotNull { file ->
            ProjectPathValidator.normalizeRelativePath(file.path)?.let { it to file }
        }.toMap().toMutableMap().apply {
            if (entryPath !in this) {
                put(entryPath, ProjectFile(entryPath, entryPath.substringAfterLast('/'), "html", htmlContent, "html"))
            }
        }.toMap()
    }
    val pageUrl = remember(entryPath) {
        "https://$PREVIEW_HOST/" + entryPath.split('/').joinToString("/") { Uri.encode(it) }
    }
    val pageRevision = remember(project.id, project.files, entryPath) {
        "${project.id}:${project.files.hashCode()}:$entryPath"
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isFullScreen) {
                // Standard preview controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Obsidian)
                        .border(1.dp, GlassBorder)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMobileViewport) NuruViolet else SlateElevated)
                                .clickable { onToggleViewport() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneIphone, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mobile", fontSize = 11.sp, color = PureWhite)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isMobileViewport) NuruViolet else SlateElevated)
                                .clickable { onToggleViewport() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Desktop", fontSize = 11.sp, color = PureWhite)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = Silver, modifier = Modifier.size(18.dp))
                        }

                        Button(
                            onClick = onToggleFullScreen,
                            colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Plein écran", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // WebView Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (!isFullScreen && isMobileViewport) 16.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (!isFullScreen && isMobileViewport) {
                                Modifier
                                    .width(360.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(2.dp, NuruViolet.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        )
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                webChromeClient = WebChromeClient()
                                webViewClient = LocalProjectPreviewClient(previewFiles, entryPath)
                                tag = pageRevision
                                loadUrl(pageUrl)
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            if (webView.tag != pageRevision) {
                                webView.tag = pageRevision
                                webView.webViewClient = LocalProjectPreviewClient(previewFiles, entryPath)
                                webView.loadUrl(pageUrl)
                            }
                            webViewRef = webView
                        }
                    )
                }
            }
        }

        // Full Screen Floating Controls Overlay
        if (isFullScreen) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Obsidian.copy(alpha = 0.85f))
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { webViewRef?.reload() }
                        .padding(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = PureWhite, modifier = Modifier.size(20.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NuruViolet.copy(alpha = 0.9f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { onExitFullScreen() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
                        Text("Quitter Plein Écran", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NewFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (path: String, content: String) -> Unit
) {
    var filePath by remember { mutableStateOf("") }
    var initialContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obsidian,
        title = {
            Text("Créer un Nouveau Fichier", color = PureWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Indiquez le chemin et l'extension du fichier (ex: index.html, src/app.js, styles.css, README.md...)",
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                )

                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("Chemin du fichier") },
                    placeholder = { Text("ex: src/components/header.js") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialContent,
                    onValueChange = { initialContent = it },
                    label = { Text("Contenu initial (optionnel)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (filePath.isNotBlank()) {
                        onConfirm(filePath.trim(), initialContent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet)
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Silver)
            }
        }
    )
}

private const val PREVIEW_HOST = "preview.nuru.local"

private class LocalProjectPreviewClient(
    private val filesByPath: Map<String, ProjectFile>,
    private val entryPath: String
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (!uri.host.equals(PREVIEW_HOST, ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        val requestedPath = uri.pathSegments.joinToString("/").ifBlank { entryPath }
        val safePath = ProjectPathValidator.normalizeRelativePath(requestedPath)
        val file = safePath?.let(filesByPath::get)
            ?: if (requestedPath.substringAfterLast('/').contains('.')) null else filesByPath[entryPath]
        if (file == null) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                mapOf("Cache-Control" to "no-store"),
                ByteArrayInputStream("Ressource absente du projet".toByteArray(Charsets.UTF_8))
            )
        }

        val mimeType = when (file.extension.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "webmanifest" -> "application/manifest+json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "xml" -> "application/xml"
            "wasm" -> "application/wasm"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(
            mimeType,
            "UTF-8",
            ByteArrayInputStream(file.content.toByteArray(Charsets.UTF_8))
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
        request.isForMainFrame && !request.url.host.equals(PREVIEW_HOST, ignoreCase = true)
}

fun buildMergedHtmlPreview(project: Project): String {
    val htmlFile = project.files.find { it.extension == "html" }
        ?: return """
            <!DOCTYPE html>
            <html>
                <body style="background:#0A0A12;color:white;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;font-family:sans-serif;">
                    <div style="text-align:center;padding:24px;">
                        <h2 style="font-size:24px;margin-bottom:8px;color:#6C4CF1;">${project.name}</h2>
                        <p style="color:#A0A0B8;max-width:400px;font-size:14px;">Aucune page HTML n'a été trouvée dans ce projet. Ajoutez une page Web pour afficher son vrai rendu ici. Les projets Android/Flutter nécessitent une compilation distincte pour être lancés.</p>
                    </div>
                </body>
            </html>
        """.trimIndent()

    var processed = htmlFile.content

    // Inline CSS files
    val cssFiles = project.files.filter { it.extension == "css" }
    cssFiles.forEach { css ->
        val hrefRegex = Regex("<link[^>]+href=[\"'](?:\\./)?${Regex.escape(css.name)}[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val styleTag = "<style>\n/* ${css.name} */\n${css.content}\n</style>"
        processed = if (hrefRegex.containsMatchIn(processed)) {
            processed.replace(hrefRegex, styleTag)
        } else if (processed.contains("</head>", ignoreCase = true)) {
            processed.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$styleTag\n</head>")
        } else {
            "$styleTag\n$processed"
        }
    }

    // Inline JS files
    val jsFiles = project.files.filter { it.extension == "js" }
    jsFiles.forEach { js ->
        val scriptSrcRegex = Regex("<script[^>]+src=[\"'](?:\\./)?${Regex.escape(js.name)}[\"'][^>]*>\\s*</script>", RegexOption.IGNORE_CASE)
        val scriptTag = "<script>\n// ${js.name}\n${js.content}\n</script>"
        processed = if (scriptSrcRegex.containsMatchIn(processed)) {
            processed.replace(scriptSrcRegex, scriptTag)
        } else if (processed.contains("</body>", ignoreCase = true)) {
            processed.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptTag\n</body>")
        } else {
            "$processed\n$scriptTag"
        }
    }

    return processed
}
