package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithStems(
    @Embedded val project: PraiseProject,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val stems: List<StemTrack>
)
