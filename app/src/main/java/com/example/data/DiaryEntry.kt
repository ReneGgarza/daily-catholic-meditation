package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val spiritualMood: String, // e.g. "Paz", "Agradecido", "Fortaleza", "Búsqueda", "Tristeza"
    val reflectionTopic: String, // e.g., "Lectio Divina: Lucas 1:38", "Oración Diaria"
    val date: Long = System.currentTimeMillis(),
    val progressScore: Int = 3 // 1 to 5 indicating daily spiritual alignment or satisfaction
) : Serializable
