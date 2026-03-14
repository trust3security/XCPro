package com.example.xcpro.profiles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccount(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hasScrolled = remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset > 0 }
    }
    val appBarElevation by animateDpAsState(targetValue = if (hasScrolled.value) 4.dp else 0.dp)
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    val navigateToProfileEditor: () -> Unit = {
        val activeProfileId = uiState.activeProfile?.id
        if (activeProfileId != null) {
            navController.navigate("profile_settings/$activeProfileId")
        } else {
            navController.navigate("profile_selection")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(appBarElevation),
                title = { Text(text = "Manage Account") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.popBackStack("map", inclusive = false)
                        }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .wrapContentHeight()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .heightIn(max = 800.dp),
                    state = listState
                ) {
                    item {
                        AccountActionRow(
                            title = "Edit Profile",
                            icon = Icons.Outlined.AccountCircle,
                            onClick = navigateToProfileEditor
                        )
                    }
                    item {
                        AccountActionRow(
                            title = "Change Password",
                            icon = Icons.Outlined.Lock,
                            onClick = { }
                        )
                    }
                    item {
                        AccountActionRow(
                            title = "View Terms & Conditions",
                            icon = Icons.Outlined.Description,
                            onClick = { }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
                    item {
                        AccountActionRow(
                            title = "Sign Out",
                            icon = Icons.Outlined.PowerSettingsNew,
                            onClick = { }
                        )
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
                    item {
                        ManageAccountVersionFooter(
                            versionText = "Version 1.0.0",
                            copyrights = " 2024 Your Company",
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ManageAccountVersionFooter(
    versionText: String,
    copyrights: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            Box(modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f)
                )
                Text(
                    copyrights,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.44f)
                )
            }
        }
    }
}
