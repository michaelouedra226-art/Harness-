package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
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
import com.example.domain.model.ProjectType
import com.example.ui.theme.*

@Composable
fun CreateProjectDialog(
    isGenerating: Boolean,
    generationStep: String,
    generationProgress: Float,
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String, type: ProjectType) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var projectDesc by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.WEB_SITE) }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        containerColor = Obsidian,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isGenerating) "Orchestration Multi-Agents..." else "Nouveau Projet IA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                )
                if (!isGenerating) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Silver)
                    }
                }
            }
        },
        text = {
            if (isGenerating) {
                // Live Generation Progress View
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NuruGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = generationStep,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NebulaBlue
                            )
                        )
                        Text(
                            text = "Les agents spécialisés collaborent et compilent les artefacts...",
                            style = MaterialTheme.typography.bodySmall.copy(color = Silver)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { generationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NuruViolet,
                        trackColor = SlateElevated
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Nom du Projet") },
                        placeholder = { Text("ex: Bistro Gourmet, TaskMaster...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedBorderColor = NuruViolet
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_proj_name_input")
                    )

                    Text(
                        text = "Type de Livrable :",
                        style = MaterialTheme.typography.labelMedium.copy(color = Silver)
                    )

                    // Deliverable options
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProjectType.values().forEach { type ->
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) SlateElevated else Graphite)
                                    .border(
                                        1.dp,
                                        if (isSelected) NuruViolet else GlassBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedType = type }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(text = type.iconEmoji, fontSize = 18.sp)
                                    Column {
                                        Text(
                                            text = type.displayName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) PureWhite else Silver
                                            )
                                        )
                                        Text(
                                            text = type.stackSummary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MutedText,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = projectDesc,
                        onValueChange = { projectDesc = it },
                        label = { Text("Description / Brief") },
                        placeholder = { Text("Décrivez les fonctionnalités, sections ou endpoints souhaités...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedBorderColor = NuruViolet
                        ),
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_proj_desc_input")
                    )
                }
            }
        },
        confirmButton = {
            if (!isGenerating) {
                Button(
                    onClick = {
                        val name = projectName.ifBlank { "Nouveau Projet Nuru" }
                        val desc = projectDesc.ifBlank { "Projet créé par l'orchestrateur multi-agents" }
                        onConfirm(name, desc, selectedType)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuruViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("create_proj_confirm_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Générer avec les Agents", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                TextButton(onClick = onDismiss) {
                    Text("Annuler", color = Silver)
                }
            }
        }
    )
}
