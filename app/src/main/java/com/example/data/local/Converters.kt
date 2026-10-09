package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.StemType

class Converters {
    @TypeConverter
    fun fromStemType(value: StemType): String = value.name

    @TypeConverter
    fun toStemType(value: String): StemType = try {
        StemType.valueOf(value)
    } catch (_: Exception) {
        StemType.VOCAL_FULL
    }
}
