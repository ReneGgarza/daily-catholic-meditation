package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.today.TodayScreen
import com.example.ui.screens.diary.DiaryScreen
import com.example.ui.screens.community.CommunityScreen
import com.example.ui.screens.settings.SettingsScreen

@Composable
fun MainAppContent() {
    val navController = rememberNavController()
    var crashTrace by remember { mutableStateOf<String?>(null) }
    
    // Read and reset the crash log on launch
    LaunchedEffect(Unit) {
        val sharedPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("daily_catholic_prefs", android.content.Context.MODE_PRIVATE)
        val trace = sharedPrefs.getString("last_crash_trace", null)
        if (!trace.isNullOrEmpty()) {
            crashTrace = trace
            sharedPrefs.edit().remove("last_crash_trace").apply()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "main"
        ) {
            composable("main") {
                MainScreenWithNavigation(
                    onNavigateToToday = { navController.navigate("today") },
                    onNavigateToDiary = { navController.navigate("diary") },
                    onNavigateToCommunity = { navController.navigate("community") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("today") {
                TodayScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("diary") {
                DiaryScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("community") {
                CommunityScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        crashTrace?.let { trace ->
            AlertDialog(
                onDismissRequest = { crashTrace = null },
                title = { Text("Recuperación de Caída") },
                text = {
                    Text(
                        text = "La aplicación se cerró debido a un problema inesperado. Comparta esta información con soporte para que la corrijamos:",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = trace,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { crashTrace = null }) {
                        Text("Aceptar")
                    }
                }
            )
        }
    }
}
