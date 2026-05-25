package com.example

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    // State prefilling for diary integration
    var formTitle by remember { mutableStateOf("") }
    var formContent by remember { mutableStateOf("") }
    var formTopic by remember { mutableStateOf("Oración Diaria") }
    var formMood by remember { mutableStateOf("Paz") }
    var formScore by remember { mutableStateOf(4) }

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

    // Randomize daily Bible verse
    val dailyVerse = remember { CatholicContent.dailyVerses.random() }

    // Dynamic Spanish Date for Top Header following Geometric Balance specification
    val todayDateString = remember {
        val sdf = SimpleDateFormat("EEEE, d MMM", Locale("es", "ES"))
        sdf.format(Date()).uppercase(Locale("es", "ES"))
    }

    // Handle feedback notifications
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearFeedbackMessage()
        }
    }

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
                        // Alerta/Reminders Quick Action Box
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .clickable {
                                    val h = reminderHour
                                    val m = reminderMinute
                                    viewModel.toggleReminder(!reminderEnabled, h, m, context)
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

                        // My Diary Shortcut Box
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                                .clickable {
                                    currentTab = 1 // Quick switch to Mi Diario Tab
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Perfil Diario",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
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
                    onNavigateToTab = { currentTab = it }
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
                    onScoreChange = { formScore = it }
                )
                2 -> PeaceAndSettingsScreen(
                    viewModel = viewModel,
                    isPremium = isPremium,
                    reminderEnabled = reminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            }
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
    onNavigateToTab: (Int) -> Unit
) {
    var selectedPrayerTheme by remember { mutableStateOf("Paz") }
    val prayerThemes = listOf("Paz", "Fortaleza", "Gratitud", "Sanación", "Familia")
    val context = LocalContext.current

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
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lectio_hero_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
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
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Lectio Divina",
                                color = Color.White,
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
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = contextPreview,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Begin/Re-meditate Reflection Button
                        Button(
                            onClick = { viewModel.generateDailyLectioDivina() },
                            enabled = !isGenerating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onSecondary,
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
                            val displayStreak = if (totalSaved == 0) "12" else "${totalSaved * 2 + 10}"
                            Text(
                                text = displayStreak,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 36.sp
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
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Navegar Premium",
                        tint = textColor,
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
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Catholic Prayer", currentPrayer)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
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
                }
            }
        }

        // Lectio Divina Active Step-by-Step interactive view
        currentLectio?.let { template ->
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
                        text = "⛪ Meditación: ${template.title}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sigue pacientemente la guía litúrgica paso a paso:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Vertical Timeline Stepper List
                    LectioStepItem(stepNumber = 1, title = "LECTIO (Lectura divina)", body = template.lectioText)
                    LectioStepItem(stepNumber = 2, title = "MEDITATIO (Meditación de fe)", body = template.meditatioText)
                    LectioStepItem(stepNumber = 3, title = "ORATIO (Oración al Señor)", body = template.oratioText)
                    LectioStepItem(stepNumber = 4, title = "CONTEMPLATIO (Presencia sagrada)", body = template.contemplatioText)

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val fullLectioText = "Lectio Divina: ${template.title}\nScripture: ${template.scripture}\n" +
                                    "Lectio: ${template.lectioText}\nMeditatio: ${template.meditatioText}\n" +
                                    "Oratio: ${template.oratioText}\nContemplatio: ${template.contemplatioText}"
                            onCopyToDiary(fullLectioText, "Lectio Divina: ${template.scripture.substringBefore('\n')}")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Guardar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Reflexión Completa en el Diario", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Stepper item component with gorgeous vertical connector logic
@Composable
fun LectioStepItem(stepNumber: Int, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
    onScoreChange: (Int) -> Unit
) {
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
                        .height(110.dp)
                        .testTag("diary_content_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
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
            Text(
                text = "📚 Mis Oraciones Archivadas",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
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
        }

        items(entries) { entry ->
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
    reminderMinute: Int
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
                            viewModel.toggleReminder(checked, h, m, context)
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
                            viewModel.toggleReminder(true, h, m, context)
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
