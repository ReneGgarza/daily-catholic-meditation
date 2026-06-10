package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isPremium by viewModel.isPremium.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val profileName by viewModel.profileName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paz / Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Placeholder for Settings screen content
            // Will display profile, reminders, premium settings
        }
    }
}
