package com.example.ui.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiaryEntry
import com.example.data.DiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val repository: DiaryRepository
) : ViewModel() {

    val diaryState: StateFlow<List<DiaryEntry>> = repository.allEntries

    fun saveDiaryEntry(title: String, content: String, topic: String, mood: String, score: Int) {
        viewModelScope.launch {
            if (title.isBlank() || content.isBlank()) {
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
        }
    }

    fun deleteDiaryEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.delete(entry)
        }
    }
}
