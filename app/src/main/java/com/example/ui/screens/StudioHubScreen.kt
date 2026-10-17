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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NuruViewModel

@Composable
fun StudioHubScreen(
    viewModel: NuruViewModel
) {
    val hubSubTab by viewModel.hubSubTab.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val models by viewModel.models.collectAsState()
    val routerConfig by viewModel.routerConfig.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val searchQuery by viewModel.modelSearchQuery.collectAsState()
    val modelFilter by viewModel.modelFilter.collectAsState()

    var showAddProviderDialog by remember { mutableStateOf(false) }
    var testingModel by remember { mutableStateOf<AIModel?>(null) }
    var slotToEdit by remember { mutableStateOf<RouterSlotType?>(null) }
    var editingKeyProvider by remember { mutableStateOf<AIProvider?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Hub Sub-Tabs
        Surface(
            color = Obsidian,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = hubSubTab,
                containerColor = Obsidian,
                contentColor = NebulaBlue,
                divider = { Divider(color = GlassBorder) }
            ) {
                Tab(
                    selected = hubSubTab == 0,
                    onClick = { viewModel.switchHubSubTab(0) },
                    text = { Text("Providers", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = hubSubTab == 1,
                    onClick = { viewModel.switchHubSubTab(1) },
                    text = { Text("Modèles", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = hubSubTab == 2,
                    onClick = { viewModel.switchHubSubTab(2) },
                    text = { Text("AI Router", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = hubSubTab == 3,
                    onClick = { viewModel.switchHubSubTab(3) },
                    text = { Text("Agents", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Sub-Tab Content
        when (hubSubTab) {
            0 -> {
                // AI PROVIDER MANAGER
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Gestionnaire de Fournisseurs",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Connectez vos clés API en direct (BYO-API)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                                )
                            }

                            Button(
                                onClick = { showAddProviderDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("hub_add_provider_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajouter", fontSize = 12.sp)
                            }
                        }
                    }

                    if (providers.isEmpty()) {
                        item {
                            Text(
                                "Aucun fournisseur n’est préconfiguré. Ajoutez votre propre endpoint et votre clé API pour charger les modèles disponibles.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Silver),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(providers) { provider ->
                            ProviderCard(
                                provider = provider,
                                onTest = { viewModel.testProvider(provider.id) },
                                onEditKey = { editingKeyProvider = provider },
                                onDelete = { viewModel.deleteProvider(provider.id) }
                            )
                        }
                    }
                }
            }

            1 -> {
                // MODEL EXPLORER
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Model Explorer",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Search bar
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setModelSearchQuery(it) },
                                placeholder = { Text("Rechercher parmi les modèles découverts...", fontSize = 12.sp, color = MutedText) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Silver) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Graphite,
                                    unfocusedContainerColor = Graphite,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = PureWhite,
                                    unfocusedTextColor = PureWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("model_explorer_search")
                            )

                            // Chips Filter
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filters = listOf(
                                    "ALL" to "Tous (${models.size})",
                                    "FAVORITES" to "⭐ Favoris",
                                    "VISION" to "👁 Vision",
                                    "CODE" to "💻 Code",
                                    "FAST" to "⚡ Rapide",
                                    "REASONING" to "🧠 Raisonnement"
                                )
                                items(filters) { (key, label) ->
                                    FilterChip(
                                        selected = modelFilter == key,
                                        onClick = { viewModel.setModelFilter(key) },
                                        label = { Text(label, fontSize = 11.sp) },
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
                    }

                    val filteredModels = models.filter { model ->
                        val matchesSearch = model.name.contains(searchQuery, ignoreCase = true) ||
                                model.providerName.contains(searchQuery, ignoreCase = true)
                        val matchesFilter = when (modelFilter) {
                            "FAVORITES" -> model.isFavorite
                            "VISION" -> model.hasVision
                            "CODE" -> model.hasCode
                            "FAST" -> model.isFast
                            "REASONING" -> model.isReasoning
                            else -> true
                        }
                        matchesSearch && matchesFilter
                    }

                    if (filteredModels.isEmpty()) {
                        item {
                            Text(
                                text = if (models.isEmpty()) {
                                    "Aucun modèle enregistré. Ajoutez un fournisseur ou testez sa connexion : les identifiants renvoyés par son API apparaîtront ici."
                                } else {
                                    "Aucun modèle ne correspond à cette recherche ou à ce filtre."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(color = Silver),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(filteredModels) { model ->
                            ModelCard(
                                model = model,
                                onToggleFavorite = { viewModel.toggleFavoriteModel(model.id) },
                                onTest = { testingModel = model },
                                onSelect = { viewModel.selectModel(model) },
                                isSelected = routerConfig.slots.values.any { it.assignedModelId == model.id }
                            )
                        }
                    }
                }
            }

            2 -> {
                // AI ROUTER
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Routage Intelligent (AI Router)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Affectez un modèle réel à chaque type de tâche. Aucune valeur de modèle implicite n’est utilisée.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                            )
                        }
                    }

                    // 5 Configurable Slots
                    items(RouterSlotType.values().toList()) { slot ->
                        val config = routerConfig.slots[slot]
                        RouterSlotCard(
                            slot = slot,
                            config = config,
                            onEdit = { slotToEdit = slot }
                        )
                    }

                    // Fallback Chain Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Graphite)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Gestion des erreurs API",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Une erreur est affichée directement. Aucun changement de modèle n’est effectué sans votre choix.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                // AGENTS TEAM HUB
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Profils d’agents spécialisés",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Chaque profil applique un rôle spécialisé et utilise le modèle affecté à son slot.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                            )
                        }
                    }

                    items(agents) { agent ->
                        AgentHubCard(
                            agent = agent,
                            onChatWithAgent = {
                                viewModel.sendMessage("Initialisation session de travail avec ${agent.title}.", agent)
                                viewModel.switchTab(1) // Chat
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Add Provider Dialog
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onConfirm = { name, type, baseUrl, key ->
                viewModel.addProvider(name, type, baseUrl, key)
                showAddProviderDialog = false
            }
        )
    }

    // Modal Test Model Dialog
    if (testingModel != null) {
        val model = testingModel!!
        TestModelDialog(
            model = model,
            onDismiss = { testingModel = null },
            onTest = { prompt, onResult -> viewModel.testModel(model, prompt, onResult) }
        )
    }

    // Slot Edit Model Picker Dialog
    if (slotToEdit != null) {
        val slot = slotToEdit!!
        SlotModelPickerDialog(
            slot = slot,
            models = models,
            onDismiss = { slotToEdit = null },
            onSelect = { selectedModel ->
                viewModel.updateRouterSlot(slot, selectedModel.id, selectedModel.name)
                slotToEdit = null
            }
        )
    }

    // Modal Edit Provider Key Dialog
    if (editingKeyProvider != null) {
        val prov = editingKeyProvider!!
        EditKeyDialog(
            provider = prov,
            onDismiss = { editingKeyProvider = null },
            onSave = { baseUrl, apiKey ->
                viewModel.updateProviderSettings(prov.id, baseUrl, apiKey)
                editingKeyProvider = null
            }
        )
    }
}

@Composable
fun EditKeyDialog(
    provider: AIProvider,
    onDismiss: () -> Unit,
    onSave: (baseUrl: String, apiKey: String) -> Unit
) {
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var key by remember(provider.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obsidian,
        title = {
            Text("Clé API · ${provider.name}", color = PureWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Modifiez l’URL de base et la clé du fournisseur. La clé actuelle n’est pas affichée ; laissez le champ vide pour la conserver.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL de base") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Nouvelle clé API") },
                    placeholder = { Text("Vide = conserver la clé actuelle") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(baseUrl.trim(), key.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet)
            ) {
                Text("Enregistrer & Tester")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Silver)
            }
        }
    )
}

@Composable
fun ProviderCard(
    provider: AIProvider,
    onTest: () -> Unit,
    onEditKey: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Cable, contentDescription = null, tint = NebulaBlue, modifier = Modifier.size(20.dp))
                    }

                    Column {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = provider.baseUrl,
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedText, fontSize = 11.sp)
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer", tint = MutedText, modifier = Modifier.size(16.dp))
                }
            }

            // Status message
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (provider.testStatus) {
                                    TestStatus.SUCCESS -> MintSuccess
                                    TestStatus.WARNING -> AmberWarning
                                    TestStatus.ERROR -> CoralError
                                    TestStatus.TESTING -> NebulaBlue
                                    TestStatus.UNTESTED -> Silver
                                }
                            )
                    )
                    Text(
                        text = provider.testMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = when (provider.testStatus) {
                                TestStatus.SUCCESS -> MintSuccess
                                TestStatus.WARNING -> AmberWarning
                                TestStatus.ERROR -> CoralError
                                else -> Silver
                            }
                        )
                    )
                }

                if (provider.latencyMs > 0) {
                    Text(
                        text = "${provider.latencyMs} ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NebulaBlue
                    )
                }
            }

            Divider(color = GlassBorder)

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (provider.apiKey.isNotBlank()) "Clé configurée" else "Aucune clé",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (provider.apiKey.isNotBlank()) MintSuccess else AmberWarning
                    )

                    IconButton(onClick = onEditKey, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Key, contentDescription = "Modifier la clé", tint = NebulaBlue, modifier = Modifier.size(15.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onEditKey,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Clé API", fontSize = 10.sp, color = PureWhite)
                    }

                    Button(
                        onClick = onTest,
                        colors = ButtonDefaults.buttonColors(containerColor = if (provider.apiKey.isNotBlank() || provider.type == ProviderType.OLLAMA || provider.type == ProviderType.CUSTOM) NuruViolet else SlateElevated),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = PureWhite)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Tester", fontSize = 10.sp, color = PureWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: AIModel,
    onToggleFavorite: () -> Unit,
    onTest: () -> Unit,
    onSelect: () -> Unit,
    isSelected: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = model.providerName,
                        style = MaterialTheme.typography.bodySmall.copy(color = NebulaBlue)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (model.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori",
                            tint = if (model.isFavorite) SolarGold else Silver,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = onTest,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Tester", fontSize = 11.sp, color = PureWhite)
                    }
                }
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 12.sp)
            )

            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelFeatureBadge(label = "Ctx: ${model.contextWindow}")
                if (model.tokensPerSec > 0) ModelFeatureBadge(label = "${model.tokensPerSec} tok/s")
                if (model.hasVision) ModelFeatureBadge(label = "👁 Vision", color = NebulaBlue)
                if (model.hasCode) ModelFeatureBadge(label = "💻 Code", color = MintSuccess)
                if (model.isReasoning) ModelFeatureBadge(label = "🧠 Raisonnement", color = NuruViolet)
            }

            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isSelected) "Modèle sélectionné pour les agents" else "Utiliser ce modèle pour les agents",
                    fontSize = 12.sp,
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
fun ModelFeatureBadge(label: String, color: Color = Silver) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SlateElevated)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RouterSlotCard(
    slot: RouterSlotType,
    config: RouterSlotConfig?,
    onEdit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onEdit() }
            .padding(16.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = slot.iconEmoji, fontSize = 18.sp)
                    Text(
                        text = "Slot : ${slot.title}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = slot.roleDescription,
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 11.sp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Graphite)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Modèle actif : ${config?.assignedModelName?.takeIf { it.isNotBlank() } ?: "Non assigné"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NebulaBlue,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Modifier",
                tint = Silver,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AgentHubCard(
    agent: AgentRole,
    onChatWithAgent: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Obsidian)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NuruViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = agent.iconEmoji, fontSize = 18.sp)
                    }

                    Column {
                        Text(
                            text = agent.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Slot : ${agent.defaultModelSlot.title}",
                            style = MaterialTheme.typography.labelSmall.copy(color = NebulaBlue)
                        )
                    }
                }

                Button(
                    onClick = onChatWithAgent,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateElevated),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Discuter", fontSize = 11.sp, color = PureWhite)
                }
            }

            Text(
                text = agent.specialty,
                style = MaterialTheme.typography.bodySmall.copy(color = PureWhite)
            )

            Text(
                text = "Focus : ${agent.promptFocus}",
                style = MaterialTheme.typography.bodySmall.copy(color = Silver, fontSize = 11.sp)
            )
        }
    }
}

@Composable
fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: ProviderType, baseUrl: String, key: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProviderType.OPENAI_COMPATIBLE) }
    var baseUrl by remember { mutableStateOf(ProviderType.OPENAI_COMPATIBLE.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obsidian,
        title = {
            Text("Ajouter un Fournisseur IA", color = PureWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { typeMenuExpanded = true }) {
                        Text("Type : ${selectedType.displayName}", color = PureWhite)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ProviderType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    baseUrl = type.defaultBaseUrl
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom (ex: Mon OpenAI)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL de Base") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Clé API (facultative pour local/personnalisé)") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Après l’enregistrement, l’application interroge cette API et affiche les modèles qu’elle renvoie.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedType, baseUrl, apiKey) },
                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet)
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Silver)
            }
        }
    )
}

@Composable
fun TestModelDialog(
    model: AIModel,
    onDismiss: () -> Unit,
    onTest: (prompt: String, onResult: (String) -> Unit) -> Unit
) {
    var prompt by remember { mutableStateOf("Explique le principe de Clean Architecture en 2 phrases.") }
    var responseOutput by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obsidian,
        title = {
            Text("Test de ${model.name}", color = PureWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Fournisseur : ${model.providerName} · Contexte : ${model.contextWindow}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt de test") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = NuruViolet
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isTesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NebulaBlue)
                }

                if (responseOutput != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Graphite)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = responseOutput.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MintSuccess
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prompt.isBlank() || isTesting) return@Button
                    isTesting = true
                    responseOutput = null
                    onTest(prompt.trim()) { output ->
                        responseOutput = output
                        isTesting = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NuruViolet)
            ) {
                Text(if (isTesting) "Appel en cours…" else "Tester via l’API")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = Silver)
            }
        }
    )
}

@Composable
fun SlotModelPickerDialog(
    slot: RouterSlotType,
    models: List<AIModel>,
    onDismiss: () -> Unit,
    onSelect: (AIModel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obsidian,
        title = {
            Text("Assigner un Modèle au Slot ${slot.title}", color = PureWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(models) { model ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Graphite)
                            .clickable { onSelect(model) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = model.name, fontWeight = FontWeight.Bold, color = PureWhite)
                                Text(text = model.providerName, fontSize = 11.sp, color = NebulaBlue)
                            }
                            Text(text = model.contextWindow, fontSize = 10.sp, color = Silver)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Silver)
            }
        }
    )
}
