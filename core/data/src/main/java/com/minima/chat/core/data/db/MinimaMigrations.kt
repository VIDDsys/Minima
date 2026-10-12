package com.minima.chat.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Non-destructive schema upgrades.
 *
 * Historical shape (reconstructed from feature timeline):
 * - v1: sessions(id, title, createdAt, updatedAt) +
 *       messages(id, sessionId, role, content, createdAt) + index(sessionId)
 * - v2: tool card columns on messages + index(toolCallId)
 * - v3: sessions.pinned, sessions.archived
 * - v4: messages.inputTokens, messages.outputTokens (usage estimate storage)
 * - v5: messages.agentStep (1-based tool iteration within a user turn)
 * - v6: messages.attachmentPath, messages.attachmentMime (vision)
 */
object MinimaMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
            db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN toolArgs TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN toolResult TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN toolStatus TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_toolCallId` ON `messages` (`toolCallId`)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE sessions ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN agentStep INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN attachmentMime TEXT")
        }
    }

    /** All stepwise migrations for databaseBuilder.addMigrations(*ALL). */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
