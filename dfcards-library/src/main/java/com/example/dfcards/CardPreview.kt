package com.example.dfcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trust3.xcpro.core.flight.RealTimeFlightData
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CardPreview(
    cardDefinition: CardDefinition,
    isSelected: Boolean,
    onToggle: (CardDefinition) -> Unit,
    liveFlightData: RealTimeFlightData? = null, //  Optional live data
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val cardStrings = rememberCardStrings()
    val cardTimeFormatter = rememberCardTimeFormatter()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150),
        label = "card_scale"
    )

    // Animated alpha for smooth transitions
    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.4f,
        animationSpec = tween(300),
        label = "icon_alpha"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "border_alpha"
    )

    //  Use live data if available, otherwise show blanks (NO SAMPLES)
    val displayData = if (liveFlightData != null) {
        mapCardDefinitionToLiveData(cardDefinition, liveFlightData, cardStrings, cardTimeFormatter)
    } else {
        PreviewDisplayData(
            primaryValue = "--",
            secondaryValue = "N/A",
            isLive = false
        )
    }

    Card(
        modifier = modifier
            .scale(scale)
            .aspectRatio(1.2f)
            .clickable {
                isPressed = true
                onToggle(cardDefinition)
            }
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        colors = CardDefaults.cardColors(
            //  NO GREY PADDING: Always clean white/surface background
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            //  ENHANCED: Perfect flight instrument layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header with icon and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = cardDefinition.icon,
                        contentDescription = null,
                        tint = cardDefinition.category.color,
                        modifier = Modifier.size(16.dp)
                    )

                    //  CLEAN: Only Add/Check icon - NO green GPS dot
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isSelected) "Selected" else "Add",
                        tint = if (isSelected)
                            cardDefinition.category.color
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(16.dp)
                            .alpha(iconAlpha)
                    )
                }

                //  PERFECT FLIGHT UX: Card name + live value prominently displayed
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Card title - GPS ALT, VARIO, etc.
                    Text(
                        text = cardDefinition.title,
                        fontSize = 9.sp, //  Readable size for card identification
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    //  NEW: Use the formatted text function
                    FormattedValueText(
                        value = displayData.primaryValue,
                        cardDefinition = cardDefinition,
                        isLive = displayData.isLive
                    )

                    // Secondary value - GPS, m/s, kt, etc.
                    displayData.secondaryValue?.let { secondary ->
                        Text(
                            text = secondary,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                //  CLEAN: Show data status or category
                Text(
                    text = if (displayData.isLive)
                        "Live Data"
                    else
                        cardDefinition.category.displayName,
                    fontSize = 6.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            //  CLEAN: Just colored border when selected - NO grey background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(borderAlpha)
                    .border(
                        width = 2.dp,
                        color = cardDefinition.category.color,
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            //  REMOVED: No more LIVE badge cluttering the interface
        }
    }

    // Reset pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(150)
            isPressed = false
        }
    }
}

//  Data class for card display information
private data class PreviewDisplayData(
    val primaryValue: String,
    val secondaryValue: String?,
    val isLive: Boolean
)

//  Map card definition to live data - NO SAMPLES, only real data or placeholders
//  SIMPLIFIED: Use centralized mapping
private fun mapCardDefinitionToLiveData(
    cardDefinition: CardDefinition,
    liveData: RealTimeFlightData,
    cardStrings: CardStrings,
    timeFormatter: CardTimeFormatter
): PreviewDisplayData {
    val (primaryValue, secondaryValue) =
        CardLibrary.mapLiveDataToCard(
            cardDefinition.id,
            liveData,
            strings = cardStrings,
            timeFormatter = timeFormatter
        )
    return PreviewDisplayData(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue,
        isLive = true  //  Always true - no filtering
    )
}

//  REMOVE all the helper functions - they're now in CardLibrary
// Remove: parseFlightTimeToHours, calculateGForce, calculateQNH
//  ADD this new function to CardPreview.kt
// In CardPreview.kt - this should already be working
@Composable
private fun FormattedValueText(
    value: String,
    cardDefinition: CardDefinition,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            val parts = value.split(" ")
            if (parts.size >= 2) {
                // Value part (1250, 850, etc.)
                withStyle(style = SpanStyle(
                    fontSize = cardDefinition.primaryFontSize.sp,
                    fontWeight = FontWeight.Bold
                )) {
                    append(parts[0])  //  "1250" in large font
                }
                append(" ")
                // Unit part (ft, m/s, kt, etc.)
                withStyle(style = SpanStyle(
                    fontSize = cardDefinition.unitFontSize.sp,
                    fontWeight = cardDefinition.unitFontWeight,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )) {
                    append(parts.drop(1).joinToString(" "))  //  "ft" in smaller font
                }
            } else {
                // No space found, show as-is
                withStyle(style = SpanStyle(
                    fontSize = cardDefinition.primaryFontSize.sp,
                    fontWeight = FontWeight.Bold
                )) {
                    append(value)
                }
            }
        },
        color = MaterialTheme.colorScheme.onSurface,  //  Always same color
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}


//  Helper functions (consistent with ViewModel)
private fun parseFlightTimeToHours(flightTime: String): Float {
    return try {
        val parts = flightTime.split(":")
        val hours = parts[0].toFloat()
        val minutes = parts[1].toFloat()
        hours + (minutes / 60f)
    } catch (e: Exception) {
        0f
    }
}

private fun calculateGForce(verticalSpeed: Double): Float {
    val baseG = 1.0f
    val additionalG = (kotlin.math.abs(verticalSpeed) / 10.0).toFloat() * 0.1f
    return (baseG + additionalG).coerceIn(0.5f, 3.0f)
}

private fun calculateQNH(baroAltitude: Double, gpsAltitude: Double): Int {
    val standardPressure = 1013.25
    val altitudeDiff = gpsAltitude - baroAltitude
    val pressureAdjustment = altitudeDiff / 8.5

    val qnh = (standardPressure + pressureAdjustment).roundToInt()
    return qnh.coerceIn(950, 1050)
}

