package com.rescuenet.app.data.local

import androidx.room.TypeConverter
import com.rescuenet.app.data.model.*

/**
 * Enums are stored as their name string (not ordinal) so adding new enum values later
 * never shifts the meaning of existing rows — important for a schema that will keep
 * evolving across Phases 3-10.
 */
class Converters {

    @TypeConverter
    fun fromIncidentType(value: IncidentType): String = value.name
    @TypeConverter
    fun toIncidentType(value: String): IncidentType = IncidentType.valueOf(value)

    @TypeConverter
    fun fromInjuryLevel(value: InjuryLevel): String = value.name
    @TypeConverter
    fun toInjuryLevel(value: String): InjuryLevel = InjuryLevel.valueOf(value)

    @TypeConverter
    fun fromSeverity(value: Severity?): String? = value?.name
    @TypeConverter
    fun toSeverity(value: String?): Severity? = value?.let { Severity.valueOf(it) }

    @TypeConverter
    fun fromSafetyStatus(value: SafetyStatus): String = value.name
    @TypeConverter
    fun toSafetyStatus(value: String): SafetyStatus = SafetyStatus.valueOf(value)

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name
    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromNeedsList(value: List<ResourceNeed>): String = value.joinToString(",") { it.name }
    @TypeConverter
    fun toNeedsList(value: String): List<ResourceNeed> =
        if (value.isBlank()) emptyList() else value.split(",").map { ResourceNeed.valueOf(it) }
}
