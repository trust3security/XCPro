package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui1.icons.Reply_all

internal const val SETTINGS_TOP_APP_BAR_NAV_BACK_TAG = "settings_top_app_bar_nav_back"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    title: String,
    onNavigateUp: (() -> Unit)?,
    onSecondaryNavigate: (() -> Unit)?,
    onNavigateToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            Row {
                if (onNavigateUp != null) {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.testTag(SETTINGS_TOP_APP_BAR_NAV_BACK_TAG)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
                if (onSecondaryNavigate != null) {
                    IconButton(onClick = onSecondaryNavigate) {
                        Icon(
                            imageVector = Reply_all,
                            contentDescription = "Secondary back",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onNavigateToMap) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Go to Map"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}


