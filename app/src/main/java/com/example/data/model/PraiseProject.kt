package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StemType {
    SOLO,           // Voz Principal / Solo
    BACK_VOCAL,     // Back Vocal Completo (todas as vozes de apoio sem o solo)
    SOPRANO,        // Soprano do Back Vocal (linha feminina aguda)
    CONTRALTO,      // Contralto do Back Vocal (linha feminina média/grave)
    TENOR,          // Tenor do Back Vocal (linha masculina aguda)
    BAIXO,          // Baixo do Back Vocal (linha masculina grave)
    VOCAL_FULL,     // Vocal Completo Original
    INSTRUMENTAL    // Acompanhamento instrumental sem vozes
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
