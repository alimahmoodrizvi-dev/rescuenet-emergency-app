package com.rescuenet.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 -> 3: family contacts can have a phone number.
 *
 * Without this migration Room would fall back to wiping the whole local database (see
 * fallbackToDestructiveMigration in DatabaseModule) — taking any reports still waiting to be
 * sent with it. Adding a nullable column keeps every existing row; their number is just empty.
 * The column must stay exactly "TEXT, nullable, no default" to match FamilyMemberEntity.phone,
 * or Room refuses to open the database.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE family_members ADD COLUMN phone TEXT")
    }
}
