package com.rescuenet.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rescuenet.app.data.local.dao.FamilyMemberDao
import com.rescuenet.app.data.local.dao.IncidentDao
import com.rescuenet.app.data.local.dao.NetworkMessageDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.dao.UserProfileDao
import com.rescuenet.app.data.local.entity.FamilyMemberEntity
import com.rescuenet.app.data.local.entity.IncidentEntity
import com.rescuenet.app.data.local.entity.NetworkMessageEntity
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        IncidentEntity::class,
        FamilyMemberEntity::class,
        SyncQueueEntity::class,
        UserProfileEntity::class,
        NetworkMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RescueNetDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun networkMessageDao(): NetworkMessageDao

    companion object {
        const val DATABASE_NAME = "rescuenet.db"
    }
}
