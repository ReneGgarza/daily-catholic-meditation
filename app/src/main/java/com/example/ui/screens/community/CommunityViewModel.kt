package com.example.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CommunityIntention
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor() : ViewModel() {

    private val _communityIntentions = MutableStateFlow<List<CommunityIntention>>(emptyList())
    val communityIntentions: StateFlow<List<CommunityIntention>> = _communityIntentions.asStateFlow()

    init {
        // Prepopulate Community Feed
        val defaultFeed = listOf(
            CommunityIntention(
                userName = "Hermano Carlos",
                userVocation = "Seminarista",
                avatarType = 1,
                avatarUrl = "",
                category = "Sanación",
                content = "Pido oraciones fervientes por la salud de mi abuela Josefa, quien se encuentra hospitalizada por neumonía. Que el Señor le conceda fortaleza y una mansa recuperación.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                amenCount = 14,
                prayCount = 8
            ),
            CommunityIntention(
                userName = "Elena de la Cruz",
                userVocation = "Catequista",
                avatarType = 2,
                avatarUrl = "",
                category = "Familia",
                content = "Doy gracias a Dios por el primer aniversario de nuestro grupo parroquial de jóvenes. Pedimos oraciones para que el Espíritu Santo siga guiando sus corazones hacia Cristo.",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
                amenCount = 28,
                prayCount = 12
            )
        )
        _communityIntentions.value = defaultFeed
    }

    fun postIntention(content: String, category: String, isAnonymous: Boolean, profileName: String, profileVocation: String, avatarType: Int, avatarUrl: String) {
        val userNameToUse = if (isAnonymous) "Anónimo" else profileName
        val userVocationToUse = if (isAnonymous) "Peregrino" else profileVocation
        val avatarTypeToUse = if (isAnonymous) 0 else avatarType
        val avatarUrlToUse = if (isAnonymous) "" else avatarUrl

        val newIntention = CommunityIntention(
            userName = userNameToUse,
            userVocation = userVocationToUse,
            avatarType = avatarTypeToUse,
            avatarUrl = avatarUrlToUse,
            category = category,
            content = content
        )
        _communityIntentions.value = listOf(newIntention) + _communityIntentions.value
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
}
