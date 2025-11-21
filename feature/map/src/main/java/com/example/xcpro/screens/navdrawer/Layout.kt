package com.example.ui1.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.example.dfcards.CardPreferences
import com.example.dfcards.CardPreferences.CardAnchor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardPreferences = remember { CardPreferences(context) }
    val storedCardsAcross by cardPreferences.getCardsAcrossPortrait()
        .collectAsState(initial = CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT)
    val storedAnchor by cardPreferences.getCardsAnchorPortrait()
        .collectAsState(initial = CardPreferences.DEFAULT_ANCHOR_PORTRAIT)
    var sliderValue by remember { mutableStateOf(storedCardsAcross.toFloat()) }
    var anchor by remember { mutableStateOf(storedAnchor) }

    LaunchedEffect(storedCardsAcross) {
        sliderValue = storedCardsAcross.toFloat()
    }
    LaunchedEffect(storedAnchor) {
        anchor = storedAnchor
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                ),
                title = { Text("Layouts", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.popBackStack("map", inclusive = false) }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Home"
                        )
                    }
                }
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
                            if (snapped != storedCardsAcross) {
                                scope.launch {
                                    cardPreferences.setCardsAcrossPortrait(snapped)
                                }
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
                                scope.launch { cardPreferences.setCardsAnchorPortrait(CardAnchor.TOP) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AnchorButton(
                            label = "Bottom",
                            icon = Icons.Filled.VerticalAlignBottom,
                            selected = anchor == CardAnchor.BOTTOM,
                            onClick = {
                                anchor = CardAnchor.BOTTOM
                                scope.launch { cardPreferences.setCardsAnchorPortrait(CardAnchor.BOTTOM) }
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
