package com.example.ui1.screens

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
import com.example.dfcards.*
import com.trust3.xcpro.core.flight.RealTimeFlightData

@Composable
fun TemplateEditorModal(
    selectedCardIds: Set<String>,
    existingTemplate: FlightTemplate? = null,
    liveFlightData: RealTimeFlightData? = null,
    hiddenCardIds: Set<String> = emptySet(),
    onSaveTemplate: (String, List<String>) -> Unit,
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

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                onSaveTemplate(templateName, selectedCards.toList())
                            }
                        },
                        enabled = isValid
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isEditing) "Save Changes" else "Save Template",
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
                    .padding(12.dp)
            ) {
                Text(
                    text = "Name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    placeholder = { Text("e.g., My Competition Setup") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Cards (${selectedCards.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardCategory.values().forEach { category ->
                        val categoryCards = CardLibrary.getCardsByCategory(category, hiddenCardIds)
                        if (categoryCards.isNotEmpty()) {
                            item {
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = category.color,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(categoryCards.chunked(3)) { cardRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cardRow.forEach { card ->
                                        CompactCardItem(
                                            card = card,
                                            isSelected = selectedCards.contains(card.id),
                                            liveFlightData = liveFlightData,
                                            onToggle = {
                                                selectedCards = if (selectedCards.contains(card.id)) {
                                                    selectedCards - card.id
                                                } else {
                                                    selectedCards + card.id
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - cardRow.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
