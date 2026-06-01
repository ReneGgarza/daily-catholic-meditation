package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CatholicContent
import com.example.data.DiaryEntry
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic Crash Interception & Logging
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sharedPrefs = getSharedPreferences("daily_catholic_prefs", Context.MODE_PRIVATE)
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()
                sharedPrefs.edit().putString("last_crash_trace", stackTraceString).commit()
            } catch (e: Exception) {
                // Silent catch
            }
            originalHandler?.uncaughtException(thread, throwable)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var crashTrace by remember { mutableStateOf<String?>(null) }
                
                // Read and reset the crash log on launch
                LaunchedEffect(Unit) {
                    val sharedPrefs = getSharedPreferences("daily_catholic_prefs", Context.MODE_PRIVATE)
                    val trace = sharedPrefs.getString("last_crash_trace", null)
                    if (!trace.isNullOrEmpty()) {
                        crashTrace = trace
                        // Clear so it doesn't loop forever
                        sharedPrefs.edit().remove("last_crash_trace").apply()
                    }
                }

                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    MainAppScreen()

                    crashTrace?.let { trace ->
                        AlertDialog(
                            onDismissRequest = { crashTrace = null },
                            title = { Text("Recuperación de Caída", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    Text(
                                        text = "La aplicación se cerró debido a un problema inesperado. Comparta esta información con soporte para que la corrijamos:",
                                        fontSize = 12.sp,
                                        modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
                                    )
                                    Box(
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .background(Color.Black.copy(alpha = 0.05f))
                                            .padding(8.dp)
                                    ) {
                                        LazyColumn(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                                            item {
                                                Text(
                                                    text = trace,
                                                    fontSize = 10.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    // Unified Android Post Notifications Permission Launcher
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleReminder(true, viewModel.reminderHour.value, viewModel.reminderMinute.value, context)
        } else {
            Toast.makeText(context, "Permiso de notificación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    val onToggleReminderWithPermission: (Boolean, Int, Int) -> Unit = { enabled, h, m ->
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (hasPermission) {
                    viewModel.toggleReminder(true, h, m, context)
                } else {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.toggleReminder(true, h, m, context)
            }
        } else {
            viewModel.toggleReminder(false, h, m, context)
        }
    }

    // State prefilling for diary integration
    var formTitle by remember { mutableStateOf("") }
    var formContent by remember { mutableStateOf("") }
    var formTopic by remember { mutableStateOf("Oración Diaria") }
    var formMood by remember { mutableStateOf("Paz") }
    var formScore by remember { mutableStateOf(4) }
    var showSpeechDialog by remember { mutableStateOf(false) }

    // Observe state from ViewModel
    val diaryEntries by viewModel.diaryState.collectAsState()
    val currentPrayer by viewModel.currentPrayer.collectAsState()
    val currentLectio by viewModel.currentLectio.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderHour by viewModel.reminderHour.collectAsState()
    val reminderMinute by viewModel.reminderMinute.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()

    // Profile state values compiled reactively
    val profileName by viewModel.profileName.collectAsState()
    val profileVocation by viewModel.profileVocation.collectAsState()
    val profileAvatarUrl by viewModel.profileAvatarUrl.collectAsState()
    val profileAvatarType by viewModel.profileAvatarType.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showLandingPage by remember { mutableStateOf(false) }

    // Randomize daily Bible verse
    val dailyVerse = remember { CatholicContent.dailyVerses.random() }

    // Dynamic Spanish Date for Top Header following Geometric Balance specification
    val todayDateString = remember {
        val sdf = SimpleDateFormat("EEEE, d MMM", Locale.forLanguageTag("es-ES"))
        sdf.format(Date()).uppercase(Locale.forLanguageTag("es-ES"))
    }

    // Handle feedback notifications
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearFeedbackMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todayDateString,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Catholic Meditations",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    
                    // Geometric Rounded Badge Quick Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Landing / Descarga APK Quick Action Box
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF59D)) // Gold accent color
                                .clickable { showLandingPage = true }
                                .testTag("btn_downloads_landing"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Descargar APK / Landing",
                                tint = Color(0xFF0F1E36),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Alerta/Reminders Quick Action Box
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .clickable {
                                    val h = reminderHour
                                    val m = reminderMinute
                                    onToggleReminderWithPermission(!reminderEnabled, h, m)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Suscripción Alarma",
                                tint = if (reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Edit Spiritual Profile Trigger
                        ProfileBubble(
                            avatarUrl = profileAvatarUrl,
                            avatarType = profileAvatarType,
                            name = profileName,
                            onClick = { showProfileDialog = true }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Hoy") },
                    label = { Text("Hoy") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("tab_today")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Mi Diario") },
                    label = { Text("Mi Diario") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("tab_diary")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Share, contentDescription = "Comunidad") },
                    label = { Text("Comunidad") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("tab_community")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Paz") },
                    label = { Text("Paz") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        if (showProfileDialog) {
            EditProfileDialog(
                initialName = profileName,
                initialVocation = profileVocation,
                initialAvatarUrl = profileAvatarUrl,
                initialAvatarType = profileAvatarType,
                onDismiss = { showProfileDialog = false },
                onSave = { n, v, u, t ->
                    viewModel.updateProfile(n, v, u, t)
                    showProfileDialog = false
                }
            )
        }

        // LandingPageScreen is rendered outside Scaffold inside the parent Box layout container below

        if (showSpeechDialog) {
            SpiritualSpeechDialog(
                onDismiss = { showSpeechDialog = false },
                onResult = { result ->
                    formContent = (if (formContent.isNotEmpty()) "$formContent\n" else "") + result
                    showSpeechDialog = false
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> TodayScreen(
                    viewModel = viewModel,
                    dailyVerse = dailyVerse,
                    isGenerating = isGenerating,
                    currentPrayer = currentPrayer,
                    currentLectio = currentLectio,
                    isPremium = isPremium,
                    diaryEntries = diaryEntries,
                    onCopyToDiary = { text, topic ->
                        formContent = text
                        formTitle = "Reflexión: $topic"
                        formTopic = topic
                        currentTab = 1 // Switch to Diary Tab
                        Toast.makeText(context, "Listo para archivar en tu Diario", Toast.LENGTH_SHORT).show()
                    },
                    onNavigateToTab = { currentTab = it },
                    onOpenLanding = { showLandingPage = true }
                )
                1 -> DiaryScreen(
                    viewModel = viewModel,
                    entries = diaryEntries,
                    formTitle = formTitle,
                    onTitleChange = { formTitle = it },
                    formContent = formContent,
                    onContentChange = { formContent = it },
                    formTopic = formTopic,
                    onTopicChange = { formTopic = it },
                    formMood = formMood,
                    onMoodChange = { formMood = it },
                    formScore = formScore,
                    onScoreChange = { formScore = it },
                    onTriggerSpeech = { showSpeechDialog = true }
                )
                2 -> CommunityScreen(
                    viewModel = viewModel,
                    isPremium = isPremium,
                    onNavigateToPremium = { currentTab = 3 }
                )
                3 -> PeaceAndSettingsScreen(
                    viewModel = viewModel,
                    isPremium = isPremium,
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute,
                    onToggleReminder = onToggleReminderWithPermission
                )
            }
        }
    }
        
    if (showLandingPage) {
        LandingPageScreen(onDismiss = { showLandingPage = false })
    }
}
}

@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    dailyVerse: com.example.data.ScriptureVerse,
    isGenerating: Boolean,
    currentPrayer: String,
    currentLectio: com.example.data.LectioDivinaTemplate?,
    isPremium: Boolean,
    diaryEntries: List<DiaryEntry>,
    onCopyToDiary: (String, String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onOpenLanding: () -> Unit = {}
) {
    var selectedPrayerTheme by remember { mutableStateOf("Paz") }
    val prayerThemes = listOf("Paz", "Fortaleza", "Gratitud", "Sanación", "Familia")
    val context = LocalContext.current

    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var currentlySpeakingStep by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        var ttsInstance: android.speech.tts.TextToSpeech? = null
        try {
            ttsInstance = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    try {
                        ttsInstance?.language = java.util.Locale("es", "ES")
                        ttsInstance?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    currentlySpeakingStep = null
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    currentlySpeakingStep = null
                                }
                            }
                        })
                    } catch (e: Exception) {}
                }
            }
            tts = ttsInstance
        } catch (e: Exception) {}

        onDispose {
            try {
                ttsInstance?.stop()
                ttsInstance?.shutdown()
            } catch (e: Exception) {}
        }
    }

    val playStepText: (Int, String) -> Unit = { stepNum, txt ->
        val safeTts = tts
        if (safeTts != null) {
            if (currentlySpeakingStep == stepNum) {
                try {
                    safeTts.stop()
                } catch (e: Exception) {}
                currentlySpeakingStep = null
            } else {
                try {
                    safeTts.stop()
                    currentlySpeakingStep = stepNum
                    val params = android.os.Bundle()
                    safeTts.speak(txt, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "step_$stepNum")
                } catch (e: Exception) {
                    currentlySpeakingStep = null
                }
            }
        } else {
            Toast.makeText(context, "El motor de voz aún no está listo.", Toast.LENGTH_SHORT).show()
        }
    }

    val prayerStreak = remember(diaryEntries) {
        if (diaryEntries.isEmpty()) {
            0
        } else {
            val entriesByDay = diaryEntries.map { entry ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = entry.date
                "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            }.toSet()

            var streak = 0
            val calendar = Calendar.getInstance()
            
            var currentDayString = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            val hasEntryToday = entriesByDay.contains(currentDayString)
            
            if (!hasEntryToday) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                currentDayString = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            }
            
            if (entriesByDay.contains(currentDayString)) {
                var maxIterations = entriesByDay.size + 5
                while (maxIterations > 0) {
                    val checkDayString = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
                    if (entriesByDay.contains(checkDayString)) {
                        streak++
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        maxIterations--
                    } else {
                        break
                    }
                }
            }
            streak
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Geometric Lectio Divina Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF023E73)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lectio_hero_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF023E73), // Serene Holy Blue
                                    Color(0xFF011E3A)  // Deep Meditative Midnight Blue
                                )
                            )
                        )
                ) {
                    // Abstract geometric circles inside background as requested
                    Canvas(
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 30.dp, y = 30.dp)
                    ) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            radius = size.maxDimension / 2
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Badge Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Lectio Divina",
                                color = Color(0xFFFFF59D), // Warm parchment gold accent
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val titleValue = currentLectio?.scripture?.substringBefore('\n') ?: "Juan 15:1-8"
                        val contextPreview = currentLectio?.scripture?.substringAfter('\n', "\"Yo soy la vid verdadera, y mi Padre es el viñador...\"") ?: "\"Yo soy la vid verdadera, y mi Padre es el viñador...\""

                        Text(
                            text = titleValue,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = contextPreview,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2F1FF), // Pristine celeste-white for extreme readability
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Begin/Re-meditate Reflection Button
                        Button(
                            onClick = { viewModel.generateDailyLectioDivina() },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD54F), // Active liturgical yellow/gold
                                contentColor = Color(0xFF1B1A00) // Dark high-contrast label color
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    color = Color(0xFF1B1A00),
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentLectio != null) "Re-meditar con IA" else "Comenzar Reflexión",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Stats & Journal Row (2-column balanced aspect layouts)
        item {
            val totalSaved = diaryEntries.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streak Item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Fervor",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = prayerStreak.toString(),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 36.sp,
                                modifier = Modifier.testTag("prayer_streak_text")
                            )
                            Text(
                                text = "Días seguidos",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Journal Access Item
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNavigateToTab(1) }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Redactar",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Mi Diario",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            val relativeLastTime = remember(diaryEntries) {
                                if (diaryEntries.isEmpty()) {
                                    "Sin registros"
                                } else {
                                    val lastEntry = diaryEntries.maxByOrNull { it.date }
                                    if (lastEntry != null) {
                                        val diffMs = System.currentTimeMillis() - lastEntry.date
                                        val diffMins = (diffMs / (1000 * 60)).toInt()
                                        when {
                                            diffMins < 1 -> "Hace un momento"
                                            diffMins < 60 -> "Hace ${diffMins}m"
                                            diffMins < 1440 -> "Hace ${(diffMins / 60)}h"
                                            else -> "Hace ${(diffMins / 1440)}d"
                                        }
                                    } else {
                                        "Sin registros"
                                    }
                                }
                            }
                            Text(
                                text = relativeLastTime,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Premium Access Banner Card
        item {
            // Colors matching our HTML palette mockup
            val containerColor = Color(0xFFF2E0FF)
            val borderColor = Color(0xFFD0BCFF)
            val textColor = Color(0xFF21005D)
            val iconBgColor = Color(0xFFD0BCFF)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(containerColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onNavigateToTab(2) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconBgColor)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Premium Icon",
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acceso Premium",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Meditaciones offline y exclusivas",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Navegar Premium",
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Portal de Descargas / Landing Page Quick Access Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFFAEC)) // Warm white gold
                    .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(20.dp))
                    .clickable { onOpenLanding() }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFD54F))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Portal de Descarga APK",
                            tint = Color(0xFF132338),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Portal de Descarga APK",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF132338)
                        )
                        Text(
                            text = "Instala en tu móvil o escanea el QR en pantalla",
                            fontSize = 11.sp,
                            color = Color(0xFF132338).copy(alpha = 0.7f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Abrir Portal",
                        tint = Color(0xFF132338),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Generate Prayer Tool Card (Styled beautifully with geometric shapes)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "🕊️ Generador de Oración Católica",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Selecciona tu intención para inspirar una oración con acompañamiento tradicional.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Themes Pills Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    prayerThemes.forEach { theme ->
                        val isSel = selectedPrayerTheme == theme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedPrayerTheme = theme }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.generateDailyPrayer(selectedPrayerTheme) },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_prayer_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Generar Oración de $selectedPrayerTheme", fontWeight = FontWeight.Bold)
                    }
                }

                // Display spawned prayer inside beautiful matching capsule
                if (currentPrayer.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = currentPrayer,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Catholic Prayer", currentPrayer)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(onClick = {
                                shareText(
                                    context = context,
                                    header = "🙏 Oración Católica Inspirada",
                                    text = currentPrayer
                                )
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        TextButton(onClick = { onCopyToDiary(currentPrayer, "Oración: $selectedPrayerTheme") }) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar en mi Diario", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Daily Scripture Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📜 Versículo del Día",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "“${dailyVerse.text}”",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dailyVerse.reference,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dailyVerse.context,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Versículo del Día", "“${dailyVerse.text}” — ${dailyVerse.reference}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar Versículo",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = {
                                shareText(
                                    context = context,
                                    header = "📜 Versículo Católico del Día",
                                    text = "“${dailyVerse.text}”\n\nRef: ${dailyVerse.reference}\n\nContexto: ${dailyVerse.context}"
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir Versículo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Lectio Divina Active Step-by-Step interactive view
        currentLectio?.let { template ->
            item {
                val isDark = isSystemInDarkTheme()
                val containerBgColor = if (isDark) Color(0xFF0E1E34) else Color(0xFFEEF5FD)
                val containerBorderColor = if (isDark) Color(0xFF1E3D6B) else Color(0xFFCCE0F7)
                val titleColor = if (isDark) Color(0xFFFFD54F) else Color(0xFF0B2545) // Warm gold or Deep Navy
                val subtitleColor = if (isDark) Color(0xFF90A4AE) else Color(0xFF455A64)
                val stepTitleColor = if (isDark) Color(0xFFFFE082) else Color(0xFF0D3E73) // Gold or Rich Navy
                val stepBodyColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F2642) // Solid White or Clear Deep Slate Blue for highest contrast
 
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(containerBgColor)
                        .border(1.5.dp, containerBorderColor, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "⛪ Meditación: ${template.title}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = titleColor
                    )
                    Text(
                        text = "Sigue pacientemente la guía litúrgica paso a paso:",
                        fontSize = 11.sp,
                        color = subtitleColor,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Vertical Timeline Stepper List
                    LectioStepItem(
                        stepNumber = 1, 
                        title = "LECTIO (Lectura divina)", 
                        body = template.lectioText, 
                        titleColor = stepTitleColor, 
                        textColor = stepBodyColor,
                        isPlaying = currentlySpeakingStep == 1,
                        onSpeakClick = { playStepText(1, template.lectioText) }
                    )
                    LectioStepItem(
                        stepNumber = 2, 
                        title = "MEDITATIO (Meditación de fe)", 
                        body = template.meditatioText, 
                        titleColor = stepTitleColor, 
                        textColor = stepBodyColor,
                        isPlaying = currentlySpeakingStep == 2,
                        onSpeakClick = { playStepText(2, template.meditatioText) }
                    )
                    LectioStepItem(
                        stepNumber = 3, 
                        title = "ORATIO (Oración al Señor)", 
                        body = template.oratioText, 
                        titleColor = stepTitleColor, 
                        textColor = stepBodyColor,
                        isPlaying = currentlySpeakingStep == 3,
                        onSpeakClick = { playStepText(3, template.oratioText) }
                    )
                    LectioStepItem(
                        stepNumber = 4, 
                        title = "CONTEMPLATIO (Presencia sagrada)", 
                        body = template.contemplatioText, 
                        titleColor = stepTitleColor, 
                        textColor = stepBodyColor,
                        isPlaying = currentlySpeakingStep == 4,
                        onSpeakClick = { playStepText(4, template.contemplatioText) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val fullLectioText = "Lectio Divina: ${template.title}\nScripture: ${template.scripture}\n" +
                                    "Lectio: ${template.lectioText}\nMeditatio: ${template.meditatioText}\n" +
                                    "Oratio: ${template.oratioText}\nContemplatio: ${template.contemplatioText}"
                            onCopyToDiary(fullLectioText, "Lectio Divina: ${template.scripture.substringBefore('\n')}")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF1E3A5F) else Color(0xFFCFE1F5),
                            contentColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF023E73)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Guardar",
                            modifier = Modifier.size(16.dp),
                            tint = if (isDark) Color(0xFF90CAF9) else Color(0xFF023E73)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Guardar Reflexión Completa en el Diario",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Stepper item component with gorgeous vertical connector logic
@Composable
fun LectioStepItem(
    stepNumber: Int, 
    title: String, 
    body: String,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    isPlaying: Boolean = false,
    onSpeakClick: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(titleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber.toString(),
                    color = if (isDark) Color(0xFF132338) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(titleColor.copy(alpha = 0.3f))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = titleColor,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (onSpeakClick != null) {
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Detener audio" else "Escuchar audio",
                            tint = if (isPlaying) Color(0xFFFFD54F) else titleColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = textColor
            )
        }
    }
}

@Composable
fun DiaryScreen(
    viewModel: MainViewModel,
    entries: List<DiaryEntry>,
    formTitle: String,
    onTitleChange: (String) -> Unit,
    formContent: String,
    onContentChange: (String) -> Unit,
    formTopic: String,
    onTopicChange: (String) -> Unit,
    formMood: String,
    onMoodChange: (String) -> Unit,
    formScore: Int,
    onScoreChange: (Int) -> Unit,
    onTriggerSpeech: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMoodFilter by remember { mutableStateOf("Todos") }
    val filterMoods = listOf("Todos", "Paz", "Gratitud", "Búsqueda", "Fortaleza", "Silencio")

    val filteredEntries = remember(entries, searchQuery, selectedMoodFilter) {
        entries.filter { entry ->
            val matchesSearch = entry.title.contains(searchQuery, ignoreCase = true) ||
                    entry.content.contains(searchQuery, ignoreCase = true) ||
                    entry.reflectionTopic.contains(searchQuery, ignoreCase = true)
            
            val matchesMood = selectedMoodFilter == "Todos" || 
                    entry.spiritualMood.equals(selectedMoodFilter, ignoreCase = true)
            
            matchesSearch && matchesMood
        }
    }

    val moods = listOf("Paz", "Gratitud", "Búsqueda", "Fortaleza", "Silencio")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Redact reflection Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "✍️ Nueva Reflexión de Fe",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = formTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Título de Oración o Pasaje") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diary_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = formContent,
                    onValueChange = onContentChange,
                    label = { Text("¿Qué meditaste o le dijiste al Señor?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .testTag("diary_content_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = onTriggerSpeech,
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                                .testTag("diary_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Dictar con voz",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = formTopic,
                    onValueChange = onTopicChange,
                    label = { Text("Tema o Pasaje Bíblico") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diary_topic_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Mi Estado Espiritual Hoy:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    moods.forEach { mood ->
                        val isSel = formMood == mood
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(100.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onMoodChange(mood) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mood,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Conexión Espiritual:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Row {
                        (1..5).forEach { rate ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "$rate estrellas",
                                tint = if (rate <= formScore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { onScoreChange(rate) }
                                    .size(28.dp)
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.saveDiaryEntry(formTitle, formContent, formTopic, formMood, formScore)
                        onTitleChange("")
                        onContentChange("")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_diary_button"),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Guardar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clavar en el Diario", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Title section
        item {
            Column {
                Text(
                    text = "📚 Mis Oraciones Archivadas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                if (entries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Search bar - Suggestion 2 (Search and Filter)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar reflexión, tema o pasaje...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diary_search_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Horizontal scrollable Filter Chips
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        items(filterMoods) { mood ->
                            val isSel = selectedMoodFilter == mood
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSel) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    .clickable { selectedMoodFilter = mood }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mood,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty logs state
        if (entries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Corazón vacío",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aún no has registrado oraciones.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Inicia con la Lectio Divina o genera oraciones para archivar tu primer paso espiritual hoy.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                }
            }
        } else if (filteredEntries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "No se encontraron resultados",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No se encontraron oraciones.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Intenta modificando tu término de búsqueda o cambiando el filtro seleccionado.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                }
            }
        }

        items(filteredEntries) { entry ->
            DiaryListCard(entry = entry, onDelete = { viewModel.deleteDiaryEntry(entry) })
        }
    }
}

@Composable
fun DiaryListCard(entry: DiaryEntry, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val formattedDate = remember(entry.date) {
        val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
        sdf.format(Date(entry.date))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("diary_item_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_diary_entry")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = formattedDate,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Badges & Stars Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mood Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Espíritu: ${entry.spiritualMood}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Topic Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = entry.reflectionTopic,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Stars rating
                Row {
                    repeat(entry.progressScore) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Estrella",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = entry.content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                modifier = Modifier.animateContentSize()
            )

            if (!expanded && entry.content.length > 120) {
                Text(
                    text = "Ver reflexión completa...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun PeaceAndSettingsScreen(
    viewModel: MainViewModel,
    isPremium: Boolean,
    reminderEnabled: Boolean,
    reminderHour: Int,
    reminderMinute: Int,
    onToggleReminder: (Boolean, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var inputHour by remember { mutableStateOf(reminderHour.toString()) }
    var inputMinute by remember { mutableStateOf(reminderMinute.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Daily Alarm Reminder Configuration Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⏰ Alerta de Oración Diaria",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mantén la constancia en tu fe diaria configurando una alarma mansa.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { checked ->
                            val h = inputHour.toIntOrNull() ?: 8
                            val m = inputMinute.toIntOrNull() ?: 30
                            onToggleReminder(checked, h, m)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                        ),
                        modifier = Modifier.testTag("reminder_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (reminderEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputHour,
                            onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) inputHour = it },
                            label = { Text("Hora (023)") },
                            modifier = Modifier.weight(1f).testTag("reminder_hour_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                        )

                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)

                        OutlinedTextField(
                            value = inputMinute,
                            onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) inputMinute = it },
                            label = { Text("Minutos") },
                            modifier = Modifier.weight(1f).testTag("reminder_minute_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val h = inputHour.toIntOrNull()?.coerceIn(0, 23) ?: 8
                            val m = inputMinute.toIntOrNull()?.coerceIn(0, 59) ?: 30
                            inputHour = h.toString()
                            inputMinute = m.toString()
                            onToggleReminder(true, h, m)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_reminder_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Actualizar Hora de Oración", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Premium subscription setup Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPremium) "👑 Premium Activo" else "⭐️ Suscripción Premium",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isPremium) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("ACTIVO", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Acompaña tu camino espiritual con acceso ilimitado a material piadoso, meditaciones bíblicas sin conexión y acompañamiento de fe diario.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("¿Qué incluye Premium?", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)

                Column(
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PremiumFeatureRow(text = "🛡️ Oración y Meditación offline ilimitada")
                    PremiumFeatureRow(text = "✨ Reflexiones guiadas interactivas de IA")
                    PremiumFeatureRow(text = "📚 Acceso a biblioteca de oraciones clásicas")
                    PremiumFeatureRow(text = "📊 Gráficos de progresos mensuales detallados")
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (!isPremium) {
                    Button(
                        onClick = { viewModel.unlockPremium(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("buy_premium_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Acceder a Premium - $2.99 USD/Mes", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.unlockPremium(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("cancel_premium_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Desactivar Cuenta Premium", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumFeatureRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Check Icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

// ==========================================
// NEW USER PROFILE & COMMUNITY FEED SCREENS
// ==========================================

@Composable
fun ProfileBubble(
    avatarUrl: String,
    avatarType: Int,
    name: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable(onClick = onClick)
            .testTag("top_profile_bubble"),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotEmpty()) {
            coil.compose.AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            val (icon, color) = getAvatarIconAndColor(avatarType)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Avatar de fe",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun getAvatarIconAndColor(type: Int): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> {
    return when(type) {
        0 -> Pair(Icons.Default.Star, Color(0xFFE5A11C)) // Estrella divina
        1 -> Pair(Icons.Default.Favorite, Color(0xFFE91E63)) // Amor mansa
        2 -> Pair(Icons.Default.Face, Color(0xFF2196F3)) // Rostro de paz
        3 -> Pair(Icons.Default.Check, Color(0xFF4CAF50)) // Sello justo
        4 -> Pair(Icons.Default.Home, Color(0xFF9C27B0)) // Hogar sagrado
        else -> Pair(Icons.Default.AccountCircle, Color(0xFF795548))
    }
}

@Composable
fun EditProfileDialog(
    initialName: String,
    initialVocation: String,
    initialAvatarUrl: String,
    initialAvatarType: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var vocation by remember { mutableStateOf(initialVocation) }
    var avatarUrl by remember { mutableStateOf(initialAvatarUrl) }
    var avatarType by remember { mutableStateOf(initialAvatarType) }

    val vocations = listOf("Laico", "Catequista", "Seminarista", "Religioso/a", "Sacerdote")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📝 Mi Perfil de Fe",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    placeholder = { Text("Ej: Juan de la Cruz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Vocation selector
                Text("Vocación / Camino:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(vocations) { item ->
                        val isSelected = vocation == item
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { vocation = item }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = item,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Custom Photo URL field
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("URL de Fotografía (Opcional)") },
                    placeholder = { Text("https://ejemplo.com/mifoto.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_avatar_url_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Built-in spiritual Avatars choice
                Text("O Elige una Insignia Espiritual:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    repeat(5) { typeIndex ->
                        val (icon, color) = getAvatarIconAndColor(typeIndex)
                        val isSelected = avatarType == typeIndex && avatarUrl.isEmpty()
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.15f))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    avatarType = typeIndex
                                    avatarUrl = "" // prefer built-in
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Avatar $typeIndex",
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onSave(name.trim(), vocation, avatarUrl.trim(), avatarType)
                    }
                },
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: MainViewModel,
    isPremium: Boolean,
    onNavigateToPremium: () -> Unit
) {
    val intentions by viewModel.communityIntentions.collectAsState()
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    var showPostDialog by remember { mutableStateOf(false) }

    val categories = listOf("Todos", "Salud", "Familia", "Acción de Gracias", "Paz", "Conversión")

    if (!isPremium) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(32.dp)
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Comunidad Global",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "⛪ Comunión de Oración Global",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "La oración compartida une corazones. Conéctate con seminaristas, laicos y religiosos de todo el mundo en el feed universal de intenciones de fe.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumBenefitRow(icon = Icons.Default.Add, text = "Comparte tus intenciones personales con fieles devotos.")
                            PremiumBenefitRow(icon = Icons.Default.Favorite, text = "Reacciona con un 'Amén' y comprométete a rezar.")
                            PremiumBenefitRow(icon = Icons.Default.Star, text = "Enciende una luz de auxilio en plegaria por tus hermanos.")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onNavigateToPremium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_community_button"),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "⭐️ Activar Suscripción Premium",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    } else {
        val filteredIntentions = remember(intentions, selectedCategoryFilter) {
            if (selectedCategoryFilter == "Todos") {
                intentions
            } else {
                intentions.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
            }
        }

        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Categories list filter
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                items(categories) { cat ->
                    val isSel = selectedCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(100.dp)
                            )
                            .clickable { selectedCategoryFilter = cat }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPostDialog = true }
                            .testTag("post_intention_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Escribir",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "🙏 ¿Tienes una intención de oración?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Toca aquí para compartirla con tus hermanos de fe.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                if (filteredIntentions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Vacío",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Aún no hay intenciones aquí.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "¡Comparte tu intención y abre este espacio espiritual!",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                } else {
                    items(filteredIntentions, key = { it.id }) { item ->
                        CommunityIntentionCard(
                            item = item,
                            onAmen = { viewModel.toggleAmen(item.id) },
                            onPray = { viewModel.togglePray(item.id) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Intención de Oración", item.content)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Intención de oración copiada", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (showPostDialog) {
            PostIntentionDialog(
                onDismiss = { showPostDialog = false },
                onPost = { text, category, isAnon ->
                    viewModel.postIntention(text, category, isAnon)
                    showPostDialog = false
                }
            )
        }
    }
}

@Composable
fun CommunityIntentionCard(
    item: com.example.data.CommunityIntention,
    onAmen: () -> Unit,
    onPray: () -> Unit,
    onCopy: () -> Unit
) {
    val formattedTime = remember(item.timestamp) {
        val seconds = (System.currentTimeMillis() - item.timestamp) / 1000
        when {
            seconds < 60 -> "Hace un momento"
            seconds < 3600 -> "Hace ${seconds / 60} min"
            seconds < 86400 -> "Hace ${seconds / 3600} h"
            else -> {
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                sdf.format(Date(item.timestamp))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("community_intention_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.avatarUrl.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = item.avatarUrl,
                        contentDescription = "Foto",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    val (icon, color) = getAvatarIconAndColor(item.avatarType)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Voz de fe",
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.userName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.userVocation} • $formattedTime",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.category,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (item.userHasAmened) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(onClick = onAmen)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (item.userHasAmened) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Amén",
                        tint = if (item.userHasAmened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Amén (${item.amenCount})",
                        fontSize = 11.sp,
                        fontWeight = if (item.userHasAmened) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.userHasAmened) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (item.userHasPrayed) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable(onClick = onPray)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Orando",
                        tint = if (item.userHasPrayed) Color(0xFFE5A93B) else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Orare (${item.prayCount})",
                        fontSize = 11.sp,
                        fontWeight = if (item.userHasPrayed) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.userHasPrayed) Color(0xFFC4891B) else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copiar",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PostIntentionDialog(
    onDismiss: () -> Unit,
    onPost: (String, String, Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Salud") }
    var isAnonymous by remember { mutableStateOf(false) }

    val categories = listOf("Salud", "Familia", "Acción de Gracias", "Paz", "Conversión")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🙏 Compartir Intención",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe tu petición al Señor aquí. Tus hermanos rezarán contigo...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("intention_text_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Categoría:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAnonymous = !isAnonymous }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        modifier = Modifier.testTag("anonymous_checkbox")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Publicar de manera anónima",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.trim().isNotEmpty()) {
                        onPost(text.trim(), selectedCategory, isAnonymous)
                    }
                },
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Publicar intención", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PremiumBenefitRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Beneficio",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

fun shareText(context: Context, header: String, text: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, header)
            putExtra(Intent.EXTRA_TEXT, "$header\n\n$text")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir con...")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el menú para compartir", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SpiritualSpeechDialog(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var transcriptionText by remember { mutableStateOf("Toca el botón y cuéntale al Señor tus reflexiones de hoy...") }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    val inspirations = listOf(
        "Gracias Dios mío por este maravilloso día, por la salud y mi familia...",
        "Padre Celestial, te pido hoy fortaleza para superar mis debilidades...",
        "Jesús, en ti confío. Ilumina mi camino de fe y dame paz en el corazón...",
        "Señor, te pido de manera especial por los enfermos y los más necesitados..."
    )

    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            transcriptionText = "Permiso concedido. ¡Toca de nuevo el botón para empezar a dictar!"
        } else {
            transcriptionText = "Acceso al micrófono denegado. Escribe libremente o selecciona una inspiración abajo."
        }
    }

    DisposableEffect(hasMicPermission) {
        if (hasMicPermission && SpeechRecognizer.isRecognitionAvailable(context.applicationContext)) {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer
                
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        transcriptionText = "Escuchando con amor..."
                    }

                    override fun onBeginningOfSpeech() {
                        transcriptionText = "Transcribiendo tu plegaria..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Error de audio."
                            SpeechRecognizer.ERROR_CLIENT -> "Error de servicio."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Faltan permisos de micrófono."
                            SpeechRecognizer.ERROR_NETWORK -> "Error de red."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo agotado."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No se escuchó voz limpia. Intenta de nuevo."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El motor está ocupado."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silencio prolongado."
                            else -> "El dictado no está habilitado."
                        }
                        transcriptionText = "$errorMsg Elige una inspiración abajo o escribe libremente."
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull()
                        if (!bestMatch.isNullOrBlank()) {
                            transcriptionText = bestMatch
                        } else {
                            transcriptionText = "No logramos entender el audio, intenta de nuevo."
                        }
                        isListening = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            transcriptionText = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (e: Throwable) {
                speechRecognizer = null
                transcriptionText = "El dictado de voz nativo no se puede iniciar en este dispositivo virtual. Puedes elegir una inspiración de abajo."
            }
        } else if (!hasMicPermission) {
            transcriptionText = "Se requiere acceso al micrófono para el dictado de voz. Toca el botón de abajo para autorizar."
        } else {
            transcriptionText = "El dictado nativo de voz no está disponible en este dispositivo.\n\nPuedes seleccionar una inspiración de plegaria a continuación:"
        }

        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microfono",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Dictado de Plegaria",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isListening) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                        }
                        
                        Text(
                            text = transcriptionText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
                    if (isRecognitionAvailable) {
                        Button(
                            onClick = {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (isListening) {
                                        try {
                                            speechRecognizer?.stopListening()
                                        } catch (e: Throwable) {}
                                        isListening = false
                                    } else {
                                        val recognizer = speechRecognizer
                                        if (recognizer == null) {
                                            transcriptionText = "Preparando reconocedor de voz..."
                                        } else {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                            }
                                            try {
                                                recognizer.startListening(intent)
                                                isListening = true
                                            } catch (e: Throwable) {
                                                transcriptionText = "No se pudo iniciar el dictado de voz nativo."
                                            }
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Check else Icons.Default.Mic,
                                contentDescription = "Accion microfono",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isListening) "Detener Escucha" else "Tocar para Hablar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    text = "🌱 Inspiraciones del Alma (Toca para usar):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    inspirations.forEach { textVal ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    transcriptionText = textVal
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = textVal,
                                fontSize = 11.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (transcriptionText.isNotEmpty() && !transcriptionText.startsWith("Toca") && !transcriptionText.startsWith("El dictado")) {
                        onResult(transcriptionText)
                    } else {
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Insertar en mi Diario", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPageScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloadUrl = "https://ais-pre-juq7nonv6awf36yjvehq5r-77616128690.us-east1.run.app"
    val scrollState = rememberScrollState()

    // Handle standard back button device gesture safely to dismiss overlay
    androidx.activity.compose.BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF031A33), // Deep Liturgical Blue
                        Color(0xFF010E1C)  // Dark Midnight
                    )
                )
            )
            .clickable(enabled = true, onClick = {}) // Consume all clicks to avoid click-through to Scaffold layers
    ) {
                // Background artistic geometric layout elements
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFFFFD54F).copy(alpha = 0.04f),
                        radius = 250.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f)
                    )
                    drawCircle(
                        color = Color(0xFF023E73).copy(alpha = 0.15f),
                        radius = 350.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.4f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Close Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "PORTAL OFICIAL DE DESCARGA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                letterSpacing = 2.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // App Title & Subtitle Branding Title
                    Text(
                        text = "Daily Catholic Meditations",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Start
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tu compañero espiritual inteligente para la oración diaria, Lectio Divina asistida por IA y registro devocional offline.",
                        fontSize = 15.sp,
                        color = Color(0xFFB0C5DE),
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Downloads Hero Grid
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF072448)),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFFFFD54F).copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic custom canvas-drawn QR code
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val squareSize = size.width / 13
                                        val paintColor = Color(0xFF031A33)
                                        
                                        // Draw the three corner finders
                                        drawRect(
                                            color = paintColor,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 4, squareSize * 4),
                                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f)
                                        )
                                        drawRect(
                                            color = Color.White,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 2, squareSize * 2),
                                            topLeft = androidx.compose.ui.geometry.Offset(squareSize, squareSize)
                                        )
                                        
                                        drawRect(
                                            color = paintColor,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 4, squareSize * 4),
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width - squareSize * 4, 0f)
                                        )
                                        drawRect(
                                            color = Color.White,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 2, squareSize * 2),
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width - squareSize * 3, squareSize)
                                        )
                                        
                                        drawRect(
                                            color = paintColor,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 4, squareSize * 4),
                                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - squareSize * 4)
                                        )
                                        drawRect(
                                            color = Color.White,
                                            size = androidx.compose.ui.geometry.Size(squareSize * 2, squareSize * 2),
                                            topLeft = androidx.compose.ui.geometry.Offset(squareSize, size.height - squareSize * 3)
                                        )
                                        
                                        // Stylized pixel-art dots
                                        val seed = 42L
                                        val random = java.util.Random(seed)
                                        for (x in 0 until 13) {
                                            for (y in 0 until 13) {
                                                if ((x < 5 && y < 5) || (x > 7 && y < 5) || (x < 5 && y > 7)) continue
                                                if (x in 5..7 && y in 5..7) continue
                                                if (random.nextBoolean()) {
                                                    drawRect(
                                                        color = paintColor,
                                                        size = androidx.compose.ui.geometry.Size(squareSize * 0.9f, squareSize * 0.9f),
                                                        topLeft = androidx.compose.ui.geometry.Offset(x * squareSize, y * squareSize)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Gold circle in the center
                                        drawCircle(
                                            color = Color(0xFFFFD54F),
                                            radius = squareSize * 1.5f,
                                            center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                        )
                                        
                                        // Vertical Cross
                                        drawRect(
                                            color = Color(0xFF031A33),
                                            size = androidx.compose.ui.geometry.Size(squareSize * 0.4f, squareSize * 1.6f),
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - squareSize * 0.2f, size.height / 2 - squareSize * 0.8f)
                                        )
                                        // Horizontal Cross
                                        drawRect(
                                            color = Color(0xFF031A33),
                                            size = androidx.compose.ui.geometry.Size(squareSize * 1.2f, squareSize * 0.4f),
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - squareSize * 0.6f, size.height / 2 - squareSize * 0.5f)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Descargar en tu Celular",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Escanea la pantalla con la cámara de tu móvil para descargar el APK directamente.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB0C5DE),
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action: Download APK Button
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se pudo abrir el navegador", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFD54F),
                                    contentColor = Color(0xFF001021)
                                ),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("landing_download_apk_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Descargar APK Directo",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Alternative Share / Copy Link Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("APK Link", downloadUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Enlace de descarga copiado", Toast.LENGTH_SHORT).show()
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar Enlace", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        shareText(
                                            context = context,
                                            header = "Daily Catholic Meditations - Descarga APK",
                                            text = "Instala la app completa de Meditaciones Católicas Diarias, Lectio Divina asistida por IA y Diario Espiritual. Descarga el APK oficial aquí: $downloadUrl"
                                        )
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Features Overview Section (Landing specifications)
                    Text(
                        text = "✨ ¿Qué obtienes al instalarla?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FeatureRowItem(
                            icon = Icons.Default.Favorite,
                            title = "Lectio Divina con IA",
                            desc = "Lectura, meditación, oración y contemplación inspirada de forma personalizada mediante IA (Gemini)."
                        )
                        FeatureRowItem(
                            icon = Icons.Default.Edit,
                            title = "Diario Litúrgico Seguro",
                            desc = "Guarda tus reflexiones privadas de forma completamente local en base de datos SQLite segura y privada."
                        )
                        FeatureRowItem(
                            icon = Icons.Default.Share,
                            title = "Comunidad Global",
                            desc = "Conéctate para apoyarte mutuamente compartiendo intenciones de oración con fieles sinceros de todo el mundo."
                        )
                        FeatureRowItem(
                            icon = Icons.Default.Notifications,
                            title = "Alarmas de Oración mansas",
                            desc = "Configura alertas diarias silenciosas para mantener encendida la chispa de la devoción diaria."
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Installation Instructions Panel
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "📲 ¿Cómo Instalar un APK?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            InstallStepRow(
                                stepNumber = "1",
                                title = "Descarga",
                                desc = "Haz clic en 'Descargar APK Directo' o escanea el código QR para bajar el archivo seguro."
                            )
                            InstallStepRow(
                                stepNumber = "2",
                                title = "Autoriza",
                                desc = "Si el sistema lo solicita, activa 'Instalar apps desconocidas' en tu navegador o explorador de archivos."
                            )
                            InstallStepRow(
                                stepNumber = "3",
                                title = "Instala",
                                desc = "Abre el archivo descargado, toca 'Instalar' y el sistema configurará la app para ti en segundos."
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Safe & Verified Footer Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verificado",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Archivo Seguro y Certificado",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                            Text(
                                text = "Libre de malware, anuncios y telemetría intrusiva. Protegido bajo entorno seguro de AI Studio.",
                                fontSize = 11.sp,
                                color = Color(0xFFC8E6C9),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Professional Landing Footer
                    Text(
                        text = "Daily Catholic Meditations v1.0.0 (API 24+)\nDesarrollado en AI Studio Build • 2026",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }

@Composable
fun FeatureRowItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFFB0C5DE),
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun InstallStepRow(stepNumber: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFD54F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color(0xFF031A33),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color(0xFFB0C5DE),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
