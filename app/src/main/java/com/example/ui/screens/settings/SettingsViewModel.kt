package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiaryRepository
import com.example.receiver.ReminderHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val reminderHelper: ReminderHelper
) : ViewModel() {

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(8)
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(30)
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

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

    fun updateProfile(name: String, vocation: String, url: String, type: Int) {
        _profileName.value = name
        _profileVocation.value = vocation
        _profileAvatarUrl.value = url
        _profileAvatarType.value = type
        _feedbackMessage.value = "Perfil espiritual actualizado con éxito"
    }

    fun toggleReminder(enabled: Boolean, hour: Int, minute: Int, context: android.content.Context) {
        _reminderEnabled.value = enabled
        _reminderHour.value = hour
        _reminderMinute.value = minute

        if (enabled) {
            ReminderHelper.scheduleDailyReminder(context, hour, minute)
            _feedbackMessage.value = "Recordatorio diario activado a las ${String.format("%02d:%02d", hour, minute)}"
        } else {
            ReminderHelper.cancelDailyReminder(context)
            _feedbackMessage.value = "Recordatorio diario desactivado"
        }
    }

    fun unlockPremium(success: Boolean) {
        viewModelScope.launch {
            _isPremium.value = success
            _feedbackMessage.value = if (success) {
                "¡Bienvenido a Premium! Gracias por tu generoso apoyo a las misiones de fe."
            } else {
                "Suscripción Premium cancelada"
            }
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }
}
