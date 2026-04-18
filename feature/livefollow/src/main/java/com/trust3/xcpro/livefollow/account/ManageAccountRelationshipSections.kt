package com.trust3.xcpro.livefollow.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RelationshipSearchCard(
    uiState: XcAccountUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSendFollowRequest: (String) -> Unit
) {
    SectionCard(title = "Search pilots") {
        Text(
            text = "Find pilots by handle and send follow requests from this signed-in lane.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (uiState.needsProfileCompletion) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Complete your pilot profile before sending follow requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Handle search") },
            supportingText = {
                Text("Enter at least 2 characters.")
            },
            singleLine = true,
            enabled = !uiState.isLoading && !uiState.isUpdatingRelationships
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSearch,
            enabled = uiState.searchEnabled
        ) {
            Text(if (uiState.isSearchingUsers) "Searching..." else "Search")
        }
        when {
            uiState.searchResults.isNotEmpty() -> {
                Spacer(modifier = Modifier.height(16.dp))
                uiState.searchResults.forEachIndexed { index, pilot ->
                    SearchResultRow(
                        pilot = pilot,
                        canSendFollowRequests = uiState.canSendFollowRequests,
                        onSendFollowRequest = onSendFollowRequest
                    )
                    if (index != uiState.searchResults.lastIndex) {
                        HorizontalDivider(modifier = Modifier.height(16.dp))
                    }
                }
            }

            uiState.hasSearchedUsers -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No pilots matched that handle search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun IncomingRequestsCard(
    uiState: XcAccountUiState,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    SectionCard(title = "Incoming requests") {
        if (uiState.incomingFollowRequests.isEmpty()) {
            Text(
                text = "No incoming follow requests right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        uiState.incomingFollowRequests.forEachIndexed { index, request ->
            FollowRequestRow(
                request = request,
                primaryLabel = "Accept",
                secondaryLabel = "Decline",
                controlsEnabled = !uiState.isUpdatingRelationships && !uiState.isLoading,
                onPrimary = { onAccept(request.requestId) },
                onSecondary = { onDecline(request.requestId) }
            )
            if (index != uiState.incomingFollowRequests.lastIndex) {
                HorizontalDivider(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
internal fun OutgoingRequestsCard(
    uiState: XcAccountUiState
) {
    SectionCard(title = "Outgoing requests") {
        if (uiState.outgoingFollowRequests.isEmpty()) {
            Text(
                text = "No outgoing follow requests right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        uiState.outgoingFollowRequests.forEachIndexed { index, request ->
            FollowRequestRow(
                request = request,
                primaryLabel = null,
                secondaryLabel = null,
                controlsEnabled = false,
                onPrimary = {},
                onSecondary = {}
            )
            if (index != uiState.outgoingFollowRequests.lastIndex) {
                HorizontalDivider(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    pilot: XcSearchPilot,
    canSendFollowRequests: Boolean,
    onSendFollowRequest: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pilot.handle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        pilot.displayName?.let {
            LabeledValue(label = "Display name", value = it)
        }
        pilot.compNumber?.let {
            LabeledValue(label = "Competition number", value = it)
        }
        Text(
            text = pilot.relationshipState.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (pilot.relationshipState) {
            XcRelationshipState.NONE,
            XcRelationshipState.FOLLOWED_BY -> {
                FilledTonalButton(
                    onClick = { onSendFollowRequest(pilot.userId) },
                    enabled = canSendFollowRequests
                ) {
                    Text(
                        if (pilot.relationshipState == XcRelationshipState.FOLLOWED_BY) {
                            "Follow back"
                        } else {
                            "Request follow"
                        }
                    )
                }
            }

            XcRelationshipState.OUTGOING_PENDING -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Request sent")
                }
            }

            XcRelationshipState.INCOMING_PENDING,
            XcRelationshipState.FOLLOWING,
            XcRelationshipState.MUTUAL -> Unit
        }
    }
}

@Composable
private fun FollowRequestRow(
    request: XcFollowRequestItem,
    primaryLabel: String?,
    secondaryLabel: String?,
    controlsEnabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = request.counterpart.handle ?: request.counterpart.userId,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        request.counterpart.displayName?.let {
            LabeledValue(label = "Display name", value = it)
        }
        request.counterpart.compNumber?.let {
            LabeledValue(label = "Competition number", value = it)
        }
        Text(
            text = request.relationshipState.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (primaryLabel != null || secondaryLabel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (primaryLabel != null) {
                    Button(
                        onClick = onPrimary,
                        enabled = controlsEnabled
                    ) {
                        Text(primaryLabel)
                    }
                }
                if (secondaryLabel != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        enabled = controlsEnabled
                    ) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}
