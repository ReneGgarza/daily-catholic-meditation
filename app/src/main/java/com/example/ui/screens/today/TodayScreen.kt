package com.example.ui.screens.today

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onBack: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val currentPrayer by viewModel.currentPrayer.collectAsState()
    val currentLectio by viewModel.currentLectio.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearFeedbackMessage()
        }
    }
    
    val todayDateString = remember {
        val sdf = SimpleDateFormat("EEEE, d MMM", Locale.forLanguageTag("es-ES"))
        sdf.format(Date()).uppercase(Locale.forLanguageTag("es-ES"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(todayDateString, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Hoy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Placeholder for Today screen content
            // This will contain the prayer and lectio divina sections from the original MainActivity
        }
    }
}
