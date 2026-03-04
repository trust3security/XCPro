package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.example.dfcards.CardPreferences
import com.example.dfcards.CardPreferences.CardAnchor
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val viewModel: LayoutViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sliderValue by remember { mutableStateOf(uiState.cardsAcrossPortrait.toFloat()) }
    var anchor by remember { mutableStateOf(uiState.anchorPortrait) }
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        scope.launch {
            navController.popBackStack("map", inclusive = false)
            drawerState.open()
        }
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }

    LaunchedEffect(uiState.cardsAcrossPortrait) {
        sliderValue = uiState.cardsAcrossPortrait.toFloat()
    }
    LaunchedEffect(uiState.anchorPortrait) {
        anchor = uiState.anchorPortrait
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Layouts",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Layouts", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose how many DF cards span the screen in portrait. Smaller cards squeeze more data into view; larger cards improve readability.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cards across (portrait): ${sliderValue.roundToInt()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            val snapped = value.roundToInt().coerceIn(
                                CardPreferences.MIN_CARDS_ACROSS_PORTRAIT,
                                CardPreferences.MAX_CARDS_ACROSS_PORTRAIT
                            )
                            sliderValue = snapped.toFloat()
                            if (snapped != uiState.cardsAcrossPortrait) {
                                viewModel.setCardsAcrossPortrait(snapped)
                            }
                        },
                        valueRange = CardPreferences.MIN_CARDS_ACROSS_PORTRAIT.toFloat()..
                            CardPreferences.MAX_CARDS_ACROSS_PORTRAIT.toFloat(),
                        steps = CardPreferences.MAX_CARDS_ACROSS_PORTRAIT - CardPreferences.MIN_CARDS_ACROSS_PORTRAIT - 1
                    )
                    Text(
                        text = "Applied to new and auto-generated layouts; existing cards reflow when layouts refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Card position anchor",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnchorButton(
                            label = "Top",
                            icon = Icons.Filled.VerticalAlignTop,
                            selected = anchor == CardAnchor.TOP,
                            onClick = {
                                anchor = CardAnchor.TOP
                                viewModel.setAnchorPortrait(CardAnchor.TOP)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AnchorButton(
                            label = "Bottom",
                            icon = Icons.Filled.VerticalAlignBottom,
                            selected = anchor == CardAnchor.BOTTOM,
                            onClick = {
                                anchor = CardAnchor.BOTTOM
                                viewModel.setAnchorPortrait(CardAnchor.BOTTOM)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (anchor == CardAnchor.TOP)
                            "Cards start at the top edge with no gap."
                        else
                            "Cards sit on the bottom edge with no gap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
