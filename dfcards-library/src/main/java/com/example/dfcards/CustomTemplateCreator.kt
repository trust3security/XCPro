package com.example.dfcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTemplateCreator(
    selectedCardIds: Set<String>,
    existingTemplate: FlightTemplate? = null, //  CHANGED: Use FlightTemplate instead of CustomTemplate
    onCreateTemplate: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var templateName by remember {
        mutableStateOf(existingTemplate?.name ?: "")
    }
    var selectedCards by remember {
        mutableStateOf(
            existingTemplate?.cardIds?.toSet() ?: selectedCardIds.toSet()
        )
    }

    val isValid = templateName.isNotBlank() && selectedCards.isNotEmpty()
    val isEditing = existingTemplate != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Template" else "Create Template",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        IconButton(
                            onClick = {
                                if (isValid) {
                                    onCreateTemplate(templateName, selectedCards.toList())
                                }
                            },
                            enabled = isValid
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = if (isEditing) "Save Changes" else "Create",
                                tint = if (isValid)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel"
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Template name input
                    Text(
                        text = "Template Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        placeholder = { Text("e.g., My Competition Setup") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selected cards count
                    Text(
                        text = "Selected Cards (${selectedCards.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Cards selection list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CardCategory.values().forEach { category ->
                            val categoryCards = CardLibrary.getCardsByCategory(category)

                            if (categoryCards.isNotEmpty()) {
                                item {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = category.color,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                items(categoryCards) { card ->
                                    CardSelectionItem(
                                        card = card,
                                        isSelected = selectedCards.contains(card.id),
                                        onToggle = { cardId ->
                                            selectedCards = if (selectedCards.contains(cardId)) {
                                                selectedCards - cardId
                                            } else {
                                                selectedCards + cardId
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSelectionItem(
    card: CardDefinition,
    isSelected: Boolean,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onToggle(card.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                card.category.color.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = card.category.color,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = card.category.color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

//  REMOVED: CustomTemplate data class - redundant with FlightTemplate