package com.nodex.client.core.data.converters

import androidx.room.TypeConverter
import com.nodex.client.domain.model.AuthType

class RoomConverters {
    @TypeConverter
    fun fromAuthType(value: AuthType): String {
        return value.name
    }

    @TypeConverter
    fun toAuthType(value: String): AuthType {
        return try {
            AuthType.valueOf(value)
        } catch (e: Exception) {
            AuthType.NONE // Default fallback
        }
    }
}
