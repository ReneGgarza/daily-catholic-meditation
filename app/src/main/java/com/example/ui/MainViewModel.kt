package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
import kotlin.math.sin

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

    // Profile state
    private val _profileName = MutableStateFlow("Hermano Peregrino")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileVocation = MutableStateFlow("Laico")
    val profileVocation: StateFlow<String> = _profileVocation.asStateFlow()

    private val _profileAvatarUrl = MutableStateFlow("")
    val profileAvatarUrl: StateFlow<String> = _profileAvatarUrl.asStateFlow()

    private val _profileAvatarType = MutableStateFlow(0)
    val profileAvatarType: StateFlow<Int> = _profileAvatarType.asStateFlow()

    // Community Feed state
    private val _communityIntentions = MutableStateFlow<List<com.example.data.CommunityIntention>>(emptyList())
    val communityIntentions: StateFlow<List<com.example.data.CommunityIntention>> = _communityIntentions.asStateFlow()

    init {
        // Load initial values from SharedPreferences
        _isPremium.value = sharedPrefs.getBoolean("is_premium_unlocked", false)
        _reminderEnabled.value = sharedPrefs.getBoolean("reminder_enabled", false)
        _reminderHour.value = sharedPrefs.getInt("reminder_hour", 8)
        _reminderMinute.value = sharedPrefs.getInt("reminder_minute", 30)

        // Load profile values
        _profileName.value = sharedPrefs.getString("profile_name", "Hermano Peregrino") ?: "Hermano Peregrino"
        _profileVocation.value = sharedPrefs.getString("profile_vocation", "Laico") ?: "Laico"
        _profileAvatarUrl.value = sharedPrefs.getString("profile_avatar_url", "") ?: ""
        _profileAvatarType.value = sharedPrefs.getInt("profile_avatar_type", 0)

        // Prepopulate Community Feed
        val defaultFeed = listOf(
            com.example.data.CommunityIntention(
                userName = "Hermano Carlos",
                userVocation = "Seminarista",
                avatarType = 1,
                avatarUrl = "",
                category = "Sanación",
                content = "Pido oraciones fervientes por la salud de mi abuela Josefa, quien se encuentra hospitalizada por neumonía. Que el Señor le conceda fortaleza y una mansa recuperación.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15, // 15 mins ago
                amenCount = 14,
                prayCount = 8
            ),
            com.example.data.CommunityIntention(
                userName = "Elena de la Cruz",
                userVocation = "Catequista",
                avatarType = 2,
                avatarUrl = "",
                category = "Familia",
                content = "Doy gracias a Dios por el primer aniversario de nuestro grupo parroquial de jóvenes. Pedimos oraciones para que el Espíritu Santo siga guiando sus corazones hacia Cristo.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2, // 2 hours ago
                amenCount = 28,
                prayCount = 12
            ),
            com.example.data.CommunityIntention(
                userName = "Padre Mateo",
                userVocation = "Sacerdote",
                avatarType = 3,
                avatarUrl = "",
                category = "Paz",
                content = "Oremos por todas las misiones evangelizadoras del mundo y por la paz en los hogares que sufren de discordia familiar. Que la Virgen María cobije a cada uno.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 5, // 5 hours ago
                amenCount = 42,
                prayCount = 31
            ),
            com.example.data.CommunityIntention(
                userName = "Sofía Ramos",
                userVocation = "Laica",
                avatarType = 4,
                avatarUrl = "",
                category = "Conversión",
                content = "Pido de todo corazón que oren por la conversión de mi hermano menor. Que el Buen Pastor le salga al encuentro y abra sus ojos espirituales a Su infinito amor.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 12, // 12 hours ago
                amenCount = 19,
                prayCount = 15
            )
        )
        _communityIntentions.value = defaultFeed

        // Generate dynamic initial items
        generateDailyLectioDivina(isInitial = true)
        generateDailyPrayer("Paz", isInitial = true)
    }

    fun updateProfile(name: String, vocation: String, url: String, type: Int) {
        _profileName.value = name
        _profileVocation.value = vocation
        _profileAvatarUrl.value = url
        _profileAvatarType.value = type

        sharedPrefs.edit().apply {
            putString("profile_name", name)
            putString("profile_vocation", vocation)
            putString("profile_avatar_url", url)
            putInt("profile_avatar_type", type)
            apply()
        }
        _feedbackMessage.value = "Perfil espiritual actualizado con éxito"
    }

    fun postIntention(content: String, category: String, isAnonymous: Boolean) {
        val userNameToUse = if (isAnonymous) "Anónimo" else _profileName.value
        val userVocationToUse = if (isAnonymous) "Peregrino" else _profileVocation.value
        val avatarTypeToUse = if (isAnonymous) 0 else _profileAvatarType.value
        val avatarUrlToUse = if (isAnonymous) "" else _profileAvatarUrl.value

        val newIntention = com.example.data.CommunityIntention(
            userName = userNameToUse,
            userVocation = userVocationToUse,
            avatarType = avatarTypeToUse,
            avatarUrl = avatarUrlToUse,
            category = category,
            content = content
        )
        _communityIntentions.value = listOf(newIntention) + _communityIntentions.value
        _feedbackMessage.value = "Intención compartida en la comunidad de oración"
    }

    fun toggleAmen(intentionId: String) {
        _communityIntentions.value = _communityIntentions.value.map { intention ->
            if (intention.id == intentionId) {
                val hasAmened = !intention.userHasAmened
                val adjust = if (hasAmened) 1 else -1
                intention.copy(
                    userHasAmened = hasAmened,
                    amenCount = intention.amenCount + adjust
                )
            } else {
                intention
            }
        }
    }

    fun togglePray(intentionId: String) {
        _communityIntentions.value = _communityIntentions.value.map { intention ->
            if (intention.id == intentionId) {
                val hasPrayed = !intention.userHasPrayed
                val adjust = if (hasPrayed) 1 else -1
                intention.copy(
                    userHasPrayed = hasPrayed,
                    prayCount = intention.prayCount + adjust
                )
            } else {
                intention
            }
        }
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

    // ==========================================
    // AUDIO ACCOMPANIMENT MANAGEMENT (SYNTH ENGINE)
    // ==========================================
    data class CompanionTrack(
        val id: Int,
        val name: String,
        val description: String,
        val icon: String
    )

    val companionTracks = listOf(
        CompanionTrack(1, "Sinfonía del Silencio (432Hz)", "Ondas sonoras puras afinadas en 432 Hz Solfeggio para inducir paz contemplativa.", "🧘"),
        CompanionTrack(2, "Arpa del Corazón Pío", "Arpegio continuo en escala pentatónica mayor que evoca el canto de los salmos.", "🕊️"),
        CompanionTrack(3, "Respiro del Huerto Exterior", "Simulación acústica de oscilación armónica de 6 segundos para ritmo de plegaria.", "🌲")
    )

    private val synthEngine = SpiritualSynth()

    private val _isPlayingAudio = MutableStateFlow(false)
    val isPlayingAudio: StateFlow<Boolean> = _isPlayingAudio.asStateFlow()

    private val _currentTrackId = MutableStateFlow(0) // 0 = Silencio (Off)
    val currentTrackId: StateFlow<Int> = _currentTrackId.asStateFlow()

    private val _audioVolume = MutableStateFlow(0.3f)
    val audioVolume: StateFlow<Float> = _audioVolume.asStateFlow()

    fun selectTrack(trackId: Int) {
        if (!_isPremium.value) {
            _feedbackMessage.value = "Activa Premium para desbloquear la música de acompañamiento espiritual"
            return
        }

        if (trackId == 0) {
            synthEngine.stop()
            _isPlayingAudio.value = false
            _currentTrackId.value = 0
            _feedbackMessage.value = "Audio de acompañamiento silenciado"
        } else {
            _currentTrackId.value = trackId
            _isPlayingAudio.value = true
            synthEngine.start(viewModelScope, trackId, _audioVolume.value)
            val trackName = companionTracks.find { it.id == trackId }?.name ?: ""
            _feedbackMessage.value = "Reproduciendo: $trackName"
        }
    }

    fun toggleAudioPlayback() {
        if (!_isPremium.value) {
            _feedbackMessage.value = "Activa Premium para desbloquear la música de acompañamiento espiritual"
            return
        }

        if (_isPlayingAudio.value) {
            synthEngine.stop()
            _isPlayingAudio.value = false
            _feedbackMessage.value = "Audio de acompañamiento pausado"
        } else {
            val trackToPlay = if (_currentTrackId.value == 0) 1 else _currentTrackId.value
            _currentTrackId.value = trackToPlay
            _isPlayingAudio.value = true
            synthEngine.start(viewModelScope, trackToPlay, _audioVolume.value)
            val trackName = companionTracks.find { it.id == trackToPlay }?.name ?: ""
            _feedbackMessage.value = "Reproduciendo: $trackName"
        }
    }

    fun changeAudioVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _audioVolume.value = clamped
        synthEngine.setVolume(clamped)
    }

    override fun onCleared() {
        super.onCleared()
        synthEngine.stop()
    }
}

// ==========================================
// REAL-TIME AUDIO SYNTHESIZER FOR PIOUS FOCUS
// ==========================================
class SpiritualSynth {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var synthJob: kotlinx.coroutines.Job? = null
    private var currentMode = 1
    private var currentVolume = 0.3f

    fun start(scope: kotlinx.coroutines.CoroutineScope, mode: Int, volume: Float) {
        stop()
        currentMode = mode
        currentVolume = volume
        isPlaying = true

        val sampleRate = 22050
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.setStereoVolume(volume, volume)
            audioTrack?.play()
        } catch (e: Exception) {
            return
        }

        synthJob = scope.launch(Dispatchers.Default) {
            val samples = ShortArray(bufferSize)
            var phase = 0.0
            var harpTimer = 0
            val harpInterval = (sampleRate * 2.5).toInt() // arpeggio speed
            var currentHarpFreq = 0f
            var harpEnvelope = 0f

            // C Major 7/9 Solfeggio Frequencies (432 Hz reference tuning)
            val padFreqs = doubleArrayOf(129.6, 162.0, 194.4, 243.0)

            while (isPlaying) {
                for (i in samples.indices) {
                    val t = phase / sampleRate
                    var value = 0.0

                    when (currentMode) {
                        1 -> { // Peace Solfeggio Pad
                            val lfo = 0.6 + 0.4 * sin(2.0 * Math.PI * 0.08 * t)
                            for (fIdx in padFreqs.indices) {
                                val freq = padFreqs[fIdx]
                                value += sin(2.0 * Math.PI * freq * t + fIdx) * 0.2
                            }
                            value *= lfo
                        }
                        2 -> { // Temple Harp Arpeggio
                            if (harpTimer <= 0) {
                                val pentatonic = floatArrayOf(259.2f, 291.6f, 324.0f, 388.8f, 437.4f, 518.4f)
                                currentHarpFreq = pentatonic.random()
                                harpEnvelope = 1.0f
                                harpTimer = harpInterval
                            }
                            if (harpEnvelope > 0) {
                                value += sin(2.0 * Math.PI * currentHarpFreq * t) * harpEnvelope * 0.3
                                harpEnvelope *= 0.99988f // slow bell decay
                            }
                            value += sin(2.0 * Math.PI * 129.6 * t) * 0.08 // base drone
                            harpTimer--
                        }
                        3 -> { // Ambient breathing guide pulse (6 seconds rhythm)
                            val breathPulse = sin(2.0 * Math.PI * (1.0 / 6.0) * t)
                            val absolutePulse = (breathPulse + 1.0) / 2.0
                            value = sin(2.0 * Math.PI * 136.1 * t) * 0.25 * absolutePulse
                        }
                    }

                    if (value > 1.0) value = 1.0
                    if (value < -1.0) value = -1.0

                    samples[i] = (value * 32767 * currentVolume).toInt().toShort()
                    phase += 1.0
                }

                if (isPlaying && audioTrack != null) {
                    try {
                        audioTrack?.write(samples, 0, samples.size)
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume
        try {
            audioTrack?.setStereoVolume(volume, volume)
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    fun stop() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore safely
        }
        audioTrack = null
    }
}
