package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.PraiseProject
import com.example.data.model.ProjectWithStems
import com.example.data.model.StemTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface PraiseDao {

    @Query("SELECT * FROM praise_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<PraiseProject>>

    @Transaction
    @Query("SELECT * FROM praise_projects WHERE id = :projectId")
    fun getProjectWithStems(projectId: Long): Flow<ProjectWithStems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: PraiseProject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStems(stems: List<StemTrack>)

    @Update
    suspend fun updateProject(project: PraiseProject)

    @Query("DELETE FROM praise_projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: Long)

    @Query("SELECT COUNT(*) FROM praise_projects")
    suspend fun getProjectCount(): Int
}
