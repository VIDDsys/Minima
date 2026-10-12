package com.minima.chat.core.data.di

import android.content.Context
import androidx.room.Room
import com.minima.chat.core.data.db.DatabaseBackup
import com.minima.chat.core.data.db.MessageDao
import com.minima.chat.core.data.db.MinimaDatabase
import com.minima.chat.core.data.db.MinimaMigrations
import com.minima.chat.core.data.db.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): MinimaDatabase {
        // Snapshot DB file before Room applies migrations (chat-preserving upgrades).
        runCatching { DatabaseBackup.backupIfUpgrading(context) }
        val db = Room.databaseBuilder(context, MinimaDatabase::class.java, DatabaseBackup.DB_NAME)
            .addMigrations(*MinimaMigrations.ALL)
            .build()
        DatabaseBackup.markOpened(context, DatabaseBackup.TARGET_VERSION)
        return db
    }

    @Provides fun provideSessionDao(db: MinimaDatabase): SessionDao = db.sessionDao()
    @Provides fun provideMessageDao(db: MinimaDatabase): MessageDao = db.messageDao()
}
