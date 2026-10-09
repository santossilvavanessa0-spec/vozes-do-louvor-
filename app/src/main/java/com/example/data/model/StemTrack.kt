package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "praise_stems",
    foreignKeys = [
        ForeignKey(
            entity = PraiseProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class StemTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val type: StemType,
    val title: String,
    val description: String,
    val audioFilePath: String,
    val defaultVolume: Float = 1.0f,
    val frequencyRange: String = ""
)
