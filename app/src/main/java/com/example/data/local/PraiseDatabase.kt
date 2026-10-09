package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.PraiseProject
import com.example.data.model.StemTrack

@Database(
    entities = [PraiseProject::class, StemTrack::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PraiseDatabase : RoomDatabase() {

    abstract fun praiseDao(): PraiseDao

    companion object {
        @Volatile
        private var INSTANCE: PraiseDatabase? = null

        fun getInstance(context: Context): PraiseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PraiseDatabase::class.java,
                    "vozes_do_louvor.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
