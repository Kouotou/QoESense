package com.example.qoesense.data.local

import androidx.room.TypeConverter
import com.example.qoesense.data.model.AppSessionType
import com.example.qoesense.data.model.TelemetryCapabilityTier

class TelemetryConverters {

    @TypeConverter
    fun fromAppSessionType(value: AppSessionType): String = value.name

    @TypeConverter
    fun toAppSessionType(value: String): AppSessionType = AppSessionType.valueOf(value)

    @TypeConverter
    fun fromCapabilityTier(value: TelemetryCapabilityTier): String = value.name

    @TypeConverter
    fun toCapabilityTier(value: String): TelemetryCapabilityTier = TelemetryCapabilityTier.valueOf(value)
}
