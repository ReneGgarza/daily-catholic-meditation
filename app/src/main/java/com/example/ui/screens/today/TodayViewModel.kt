package com.example.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiaryRepository
import com.example.api.RetrofitClient
import com.example.BuildConfig
import com.example.api.GenerateContentRequest
import com.example.api.Content
import com.example.api.Part
import com.example.api.GenerationConfig
import com.example.data.CatholicContent
import com.example.data.LectioDivinaTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: DiaryRepository
) : ViewModel() {

    // UI state for content generation
    private val _currentPrayer = MutableStateFlow("")
    val currentPrayer: StateFlow<String> = _currentPrayer.asStateFlow()

    private val _currentLectio = MutableStateFlow<LectioDivinaTemplate?>(null)
    val currentLectio: StateFlow<LectioDivinaTemplate?> = _currentLectio.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        generateDailyLectioDivina(isInitial = true)
        generateDailyPrayer("Paz", isInitial = true)
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun isApiKeyValid(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.contains("PLACEHOLDER")
    }

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
}
