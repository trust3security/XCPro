package com.trust3.xcpro.livefollow.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccount(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: XcAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = { Text("Manage Account") },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }
                    ) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            navController.popBackStack("map", inclusive = false)
                        }
                    ) {
                        Text("Home")
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
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ScreenIntroCard()
                }
                if (uiState.isLoading) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (uiState.statusMessage != null || uiState.errorMessage != null) {
                    item {
                        MessageCard(
                            statusMessage = uiState.statusMessage,
                            errorMessage = uiState.errorMessage,
                            onDismiss = viewModel::dismissStatus
                        )
                    }
                }
                if (!uiState.isSignedIn) {
                    item {
                        SignedOutCard(
                            uiState = uiState,
                            onSignIn = { method ->
                                when (method) {
                                    XcAccountSignInMethod.GOOGLE -> {
                                        scope.launch {
                                            when (val result = requestXcGoogleIdToken(context)) {
                                                XcGoogleIdTokenRequestResult.Cancelled -> Unit
                                                is XcGoogleIdTokenRequestResult.Failure -> {
                                                    viewModel.showStatusMessage(result.message)
                                                }

                                                is XcGoogleIdTokenRequestResult.Success -> {
                                                    viewModel.signInWithGoogleIdToken(result.idToken)
                                                }

                                                is XcGoogleIdTokenRequestResult.Unavailable -> {
                                                    viewModel.showStatusMessage(result.message)
                                                }
                                            }
                                        }
                                    }

                                    else -> viewModel.signIn(method)
                                }
                            }
                        )
                    }
                } else if (uiState.userId == null) {
                    item {
                        SignedInUnavailableCard(
                            onRefresh = viewModel::refresh,
                            onSignOut = viewModel::signOut
                        )
                    }
                } else {
                    item {
                        AccountSummaryCard(
                            uiState = uiState,
                            onRefresh = viewModel::refresh,
                            onSignOut = viewModel::signOut
                        )
                    }
                    item {
                        ProfileEditorCard(
                            uiState = uiState,
                            onHandleChanged = viewModel::onHandleChanged,
                            onDisplayNameChanged = viewModel::onDisplayNameChanged,
                            onCompNumberChanged = viewModel::onCompNumberChanged,
                            onSave = viewModel::saveProfile
                        )
                    }
                    item {
                        PrivacyCard(
                            uiState = uiState,
                            onDiscoverabilitySelected = viewModel::onDiscoverabilitySelected,
                            onFollowPolicySelected = viewModel::onFollowPolicySelected,
                            onDefaultLiveVisibilitySelected = viewModel::onDefaultLiveVisibilitySelected,
                            onConnectionListVisibilitySelected = viewModel::onConnectionListVisibilitySelected,
                            onSave = viewModel::savePrivacy
                        )
                    }
                    item {
                        RelationshipSearchCard(
                            uiState = uiState,
                            onQueryChanged = viewModel::onSearchQueryChanged,
                            onSearch = viewModel::searchUsers,
                            onSendFollowRequest = viewModel::sendFollowRequest
                        )
                    }
                    item {
                        IncomingRequestsCard(
                            uiState = uiState,
                            onAccept = viewModel::acceptFollowRequest,
                            onDecline = viewModel::declineFollowRequest
                        )
                    }
                    item {
                        OutgoingRequestsCard(uiState = uiState)
                    }
                }
            }
        }
    }
}
