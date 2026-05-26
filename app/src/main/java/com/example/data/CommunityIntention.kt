package com.example.data

import java.io.Serializable
import java.util.UUID

data class CommunityIntention(
    val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val userVocation: String,
    val avatarType: Int, // index of built-in spiritual avatars
    val avatarUrl: String, // option for web image URL
    val category: String, // e.g. "Salud", "Familia", "Acción de Gracias", "Paz", "Conversión"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val amenCount: Int = 0,
    val prayCount: Int = 0,
    val userHasAmened: Boolean = false,
    val userHasPrayed: Boolean = false
) : Serializable
