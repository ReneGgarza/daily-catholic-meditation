package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.Content
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.CatholicContent
import com.example.data.DiaryEntry
import com.example.data.DiaryRepository
import com.example.data.LectioDivinaTemplate
import com.example.receiver.ReminderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("daily_catholic_prefs", Context.MODE_PRIVATE)
    private val diaryDao = AppDatabase.getDatabase(application).diaryDao()
    private val repository = DiaryRepository(diaryDao)

    // Spiritual Diary entries (reactive Flow)
    val diaryState: StateFlow<List<DiaryEntry>> = repository.allEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI state for content generation
    private val _currentPrayer = MutableStateFlow("")
    val currentPrayer: StateFlow<String> = _currentPrayer.asStateFlow()

    private val _currentLectio = MutableStateFlow<LectioDivinaTemplate?>(null)
    val currentLectio: StateFlow<LectioDivinaTemplate?> = _currentLectio.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Premium state
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Reminder state
    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(8)
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(30)
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    // Error messages/Info feedback states
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        // Load initial values from SharedPreferences
        _isPremium.value = sharedPrefs.getBoolean("is_premium_unlocked", false)
        _reminderEnabled.value = sharedPrefs.getBoolean("reminder_enabled", false)
        _reminderHour.value = sharedPrefs.getInt("reminder_hour", 8)
        _reminderMinute.value = sharedPrefs.getInt("reminder_minute", 30)

        // Generate dynamic initial items
        generateDailyLectioDivina(isInitial = true)
        generateDailyPrayer("Paz", isInitial = true)
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    // Checking if the real Gemini Key is present (excluding default placeholders)
    fun isApiKeyValid(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.contains("PLACEHOLDER")
    }

    // Generate custom prayer
    fun generateDailyPrayer(topic: String, isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) {
                _isGenerating.value = true
            }
            val appKey = BuildConfig.GEMINI_API_KEY

            if (isApiKeyValid()) {
                val promptText = "Genera una oración católica guiada y pía sobre el tema: $topic. " +
                        "Que sea inspiradora, formal, en español, con un tono de devoción profunda, salmos o santos de la Iglesia. " +
                        "Si el tema es libre, genera una oración para fortalecer la vida diaria. " +
                        "Mantén la extensión en unos 3 o 4 párrafos hermosos y devocionales."

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = "Eres un respetado teólogo católico y director espiritual piadoso.")))
                )

                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(appKey, request)
                    }
                    val textRes = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (textRes.isNullOrEmpty()) {
                        throw Exception("No content spawned")
                    }
                    _currentPrayer.value = textRes
                    if (!isInitial) {
                        _feedbackMessage.value = "Oración generada con Espíritu Divino de IA"
                    }
                } catch (e: Exception) {
                    fallbackPrayer(topic, "Error de red: Mostrando devocional cargado offline")
                } finally {
                    _isGenerating.value = false
                }
            } else {
                fallbackPrayer(topic, if (isInitial) null else "Modo Sin Conexión / Gratuito Activo (Oraciones locales)")
                _isGenerating.value = false
            }
        }
    }

    private fun fallbackPrayer(topic: String, notice: String?) {
        val selections = CatholicContent.classicPrayers.filter {
            it.category.equals(topic, ignoreCase = true) || it.category == "Clásicas"
        }
        val p = selections.randomOrNull() ?: CatholicContent.classicPrayers.random()
        _currentPrayer.value = p.text + "\n\n(Ofrecido en el devocional: ${p.title})"
        if (notice != null) {
            _feedbackMessage.value = notice
        }
    }

    // Generate Lectio Divina (Reading + Reflection)
    fun generateDailyLectioDivina(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (!isInitial) {
                _isGenerating.value = true
            }
            val appKey = BuildConfig.GEMINI_API_KEY

            if (isApiKeyValid()) {
                val promptText = "Por favor genera una meditación guiada de Lectio Divina católica completa y estructurada " +
                        "basada en un pasaje evangélico profundo de la liturgia. Devuelve la respuesta estructurada EXACTAMENTE con " +
                        "las siguientes secciones claramente divididas: " +
                        "\"TÍTULO: [Pon el título de la meditación]\" " +
                        "\"EVANGELIO: [Cita literal de la escritura de un versículo profundo]\" " +
                        "\"LECTIO (Leer): [Texto explicativo de la lectura consciente]\" " +
                        "\"MEDITATIO (Meditar): [Guía espiritual para reflexionar su impacto personal]\" " +
                        "\"ORATIO (Orar): [Súplica o oración al Señor de respuesta]\" " +
                        "\"CONTEMPLATIO (Contemplar): [Indicación para reposar en silencio piadoso]\" " +
                        "Usa un tono humilde, santo y místico en español."

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = promptText)))),
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = "Eres un monje de clausura benedictino que guía Lectio Divina para almas piadosas.")))
                )

                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.service.generateContent(appKey, request)
                    }
                    val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (rawText.isNullOrEmpty()) {
                        throw Exception("Empty content spawned")
                    }
                    val parsedTemplate = parseRawLectioText(rawText)
                    _currentLectio.value = parsedTemplate
                    if (!isInitial) {
                        _feedbackMessage.value = "Lectio Divina generada con IA"
                    }
                } catch (e: Exception) {
                    fallbackLectio("Nota: Cargando Lectio Divina local offline")
                } finally {
                    _isGenerating.value = false
                }
            } else {
                fallbackLectio(if (isInitial) null else "Lectio Divina Offline del día")
                _isGenerating.value = false
            }
        }
    }

    private fun fallbackLectio(notice: String?) {
        val randomTemplate = CatholicContent.lectioDivinaTemplates.random()
        _currentLectio.value = randomTemplate
        if (notice != null) {
            _feedbackMessage.value = notice
        }
    }

    private fun parseRawLectioText(rawText: String): LectioDivinaTemplate {
        // Simple parsers finding tags
        fun extractSection(text: String, startTag: String, endTags: List<String>): String {
            val startIndex = text.indexOf(startTag)
            if (startIndex == -1) return ""
            val realStart = startIndex + startTag.length
            
            var nearestEnd = text.length
            for (tag in endTags) {
                val idx = text.indexOf(tag, realStart)
                if (idx != -1 && idx < nearestEnd) {
                    nearestEnd = idx
                }
            }
            return text.substring(realStart, nearestEnd).trim()
        }

        val tags = listOf("TÍTULO:", "EVANGELIO:", "LECTIO:", "MEDITATIO:", "ORATIO:", "CONTEMPLATIO:")
        val title = extractSection(rawText, "TÍTULO:", tags).removePrefix("TÍTULO:").trim()
        val evang = extractSection(rawText, "EVANGELIO:", tags)
        val lectio = extractSection(rawText, "LECTIO (Leer):", tags).ifBlank { extractSection(rawText, "LECTIO:", tags) }
        val meditatio = extractSection(rawText, "MEDITATIO (Meditar):", tags).ifBlank { extractSection(rawText, "MEDITATIO:", tags) }
        val oratio = extractSection(rawText, "ORATIO (Orar):", tags).ifBlank { extractSection(rawText, "ORATIO:", tags) }
        val contemplatio = extractSection(rawText, "CONTEMPLATIO (Contemplar):", tags).ifBlank { extractSection(rawText, "CONTEMPLATIO:", tags) }

        return LectioDivinaTemplate(
            title = title.ifBlank { "Lectio Divina Diaria" },
            scripture = evang.ifBlank { "Pasaje diario de contemplación cristiana" },
            lectioText = lectio.ifBlank { "Continúa leyendo las escrituras piadosamente." },
            meditatioText = meditatio.ifBlank { "Medita cómo estas verdades eternas operan en tu día a día." },
            oratioText = oratio.ifBlank { "Dirígete al Creador con alabanzas y peticiones de conversión." },
            contemplatioText = contemplatio.ifBlank { "Descansa en la presencia del Señor en profundo silencio pasivo." }
        )
    }

    // Diary Operations (Room Save/Delete)
    fun saveDiaryEntry(title: String, content: String, topic: String, mood: String, score: Int) {
        viewModelScope.launch {
            if (title.isBlank() || content.isBlank()) {
                _feedbackMessage.value = "Por favor completa el título y la reflexión"
                return@launch
            }
            val newEntry = DiaryEntry(
                title = title.trim(),
                content = content.trim(),
                spiritualMood = mood,
                reflectionTopic = topic,
                progressScore = score
            )
            repository.insert(newEntry)
            _feedbackMessage.value = "¡Entrada de diario guardada piadosamente!"
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            _feedbackMessage.value = "Entrada de diario eliminada"
        }
    }

    // Settings & Reminders management
    fun toggleReminder(enabled: Boolean, hour: Int, minute: Int, context: Context) {
        _reminderEnabled.value = enabled
        _reminderHour.value = hour
        _reminderMinute.value = minute

        sharedPrefs.edit()
            .putBoolean("reminder_enabled", enabled)
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        if (enabled) {
            ReminderHelper.scheduleDailyReminder(context, hour, minute)
            _feedbackMessage.value = "Recordatorio diario activado a las ${String.format("%02d:%02d", hour, minute)}"
        } else {
            ReminderHelper.cancelDailyReminder(context)
            _feedbackMessage.value = "Recordatorio diario desactivado"
        }
    }

    // Premium Subscription
    fun unlockPremium(success: Boolean) {
        viewModelScope.launch {
            if (success) {
                _isPremium.value = true
                sharedPrefs.edit().putBoolean("is_premium_unlocked", true).apply()
                _feedbackMessage.value = "¡Bienvenido a Premium! Gracias por tu generoso apoyo a las misiones de fe."
            } else {
                _isPremium.value = false
                sharedPrefs.edit().putBoolean("is_premium_unlocked", false).apply()
                _feedbackMessage.value = "Suscripción Premium cancelada"
            }
        }
    }
}
