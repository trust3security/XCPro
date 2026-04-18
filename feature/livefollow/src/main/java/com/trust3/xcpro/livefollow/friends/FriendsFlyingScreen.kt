package com.trust3.xcpro.livefollow.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsFlyingScreen(
    onNavigateBack: () -> Unit,
    selectedWatchKey: String?,
    onOpenWatch: (FriendsFlyingPilotSelection) -> Unit,
    viewModel: FriendsFlyingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = true
        )
    )
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(viewModel) {
        scaffoldState.bottomSheetState.expand()
        viewModel.onSheetShown()
    }

    LaunchedEffect(viewModel, onOpenWatch) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FriendsFlyingEvent.OpenWatch -> {
                    scaffoldState.bottomSheetState.partialExpand()
                    onOpenWatch(event.pilot)
                }
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetPeekHeight = FriendsFlyingSheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetShadowElevation = 12.dp,
        sheetDragHandle = null,
        sheetContent = {
            FriendsFlyingSheet(
                uiState = uiState,
                selectedWatchKey = selectedWatchKey,
                isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded,
                selectedTabIndex = selectedTabIndex,
                searchQuery = searchQuery,
                maxSheetHeight = friendsFlyingExpandedSheetMaxHeight(
                    screenHeight = configuration.screenHeightDp.dp
                ),
                onTabSelected = { selectedTabIndex = it },
                onSearchQueryChange = { searchQuery = it },
                onClearSearch = { searchQuery = "" },
                onExpandRequest = {
                    coroutineScope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                },
                onRefresh = viewModel::refresh,
                onPilotSelected = viewModel::selectPilot
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
