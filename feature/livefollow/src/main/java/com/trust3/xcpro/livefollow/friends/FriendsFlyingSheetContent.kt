package com.trust3.xcpro.livefollow.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val FriendsFlyingSheetPeekHeight = 76.dp

private const val FriendsFlyingExpandedSheetHeightFraction = 0.78f

private enum class FriendsFlyingSheetTab(
    val label: String
) {
    PUBLIC("Public"),
    FOLLOWING("Following")
}

internal fun friendsFlyingExpandedSheetMaxHeight(
    screenHeight: Dp
): Dp = screenHeight * FriendsFlyingExpandedSheetHeightFraction

@Composable
internal fun FriendsFlyingSheet(
    uiState: FriendsFlyingUiState,
    selectedWatchKey: String?,
    isExpanded: Boolean,
    selectedTabIndex: Int,
    searchQuery: String,
    maxSheetHeight: Dp,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onExpandRequest: () -> Unit,
    onRefresh: () -> Unit,
    onPilotSelected: (FriendsFlyingPilotSelection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
    ) {
        FriendsFlyingPeekHeader(
            title = uiState.title,
            pilotCountLabel = uiState.pilotCountLabel,
            isExpanded = isExpanded,
            onClick = onExpandRequest
        )
        if (isExpanded) {
            FriendsFlyingExpandedContent(
                uiState = uiState,
                selectedWatchKey = selectedWatchKey,
                selectedTabIndex = selectedTabIndex,
                searchQuery = searchQuery,
                onTabSelected = onTabSelected,
                onSearchQueryChange = onSearchQueryChange,
                onClearSearch = onClearSearch,
                onRefresh = onRefresh,
                onPilotSelected = onPilotSelected
            )
        }
    }
}

@Composable
private fun FriendsFlyingPeekHeader(
    title: String,
    pilotCountLabel: String?,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        ) {}
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                pilotCountLabel?.let { countLabel ->
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isExpanded) {
                Text(
                    text = "Tap or drag to browse",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FriendsFlyingExpandedContent(
    uiState: FriendsFlyingUiState,
    selectedWatchKey: String?,
    selectedTabIndex: Int,
    searchQuery: String,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPilotSelected: (FriendsFlyingPilotSelection) -> Unit
) {
    val tabs = FriendsFlyingSheetTab.entries
    val safeTabIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val selectedTabState = when (tabs[safeTabIndex]) {
        FriendsFlyingSheetTab.PUBLIC -> uiState.publicTab
        FriendsFlyingSheetTab.FOLLOWING -> uiState.followingTab
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = safeTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = safeTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(text = tab.label) }
                )
            }
        }
        FriendsFlyingBrowseTabContent(
            tabUiState = selectedTabState,
            selectedWatchKey = selectedWatchKey,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onClearSearch = onClearSearch,
            onRefresh = onRefresh,
            onPilotSelected = onPilotSelected
        )
    }
}
