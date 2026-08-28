package com.hearai.app.data.db

import androidx.room.TypeConverter

/** Room can't persist List<String> natively — store as a simple delimited string. */
class Converters {
    @TypeConverter
    fun fromLanguageList(languages: List<String>): String = languages.joinToString(",")

    @TypeConverter
    fun toLanguageList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(",")
}
