package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StemType {
    SOPRANO,
    CONTRALTO,
    TENOR,
    BAIXO,
    VOCAL_FULL,
    INSTRUMENTAL
}

@Entity(tableName = "praise_projects")
data class PraiseProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val musicalKey: String = "G",
    val notes: String = "",
    val durationSeconds: Int = 180,
    val originalFileName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val selectedModel: String = "Demucs v4 Hybrid AI",
    val audioFormat: String = "MP3 (320 kbps)",
    val isSample: Boolean = false
)
