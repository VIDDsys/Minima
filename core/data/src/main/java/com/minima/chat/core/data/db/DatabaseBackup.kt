package com.minima.chat.core.data.db

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies minima.db (+ wal/shm) into filesDir/db_backups before a schema bump.
 * Does not replace Export sessions JSON — this is a last-resort file snapshot.
 */
object DatabaseBackup {
    const val PREFS = "minima_db_meta"
    const val KEY_SCHEMA = "opened_schema_version"
    const val DB_NAME = "minima.db"
    const val TARGET_VERSION = 6

    /**
     * If a previous install opened an older schema and the db file exists,
     * snapshot it once per bump. Safe to call before Room.databaseBuilder.build().
     */
    fun backupIfUpgrading(context: Context, targetVersion: Int = TARGET_VERSION): File? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_SCHEMA, 0)
        if (last <= 0 || last >= targetVersion) return null
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return null
        val dir = File(context.filesDir, "db_backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dest = File(dir, "minima_v${last}_to_v${targetVersion}_$stamp.db")
        dbFile.copyTo(dest, overwrite = true)
        File(dbFile.path + "-wal").takeIf { it.exists() }?.copyTo(File(dest.path + "-wal"), true)
        File(dbFile.path + "-shm").takeIf { it.exists() }?.copyTo(File(dest.path + "-shm"), true)
        return dest
    }

    fun markOpened(context: Context, version: Int = TARGET_VERSION) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SCHEMA, version)
            .apply()
    }

    fun latestBackup(context: Context): File? {
        val dir = File(context.filesDir, "db_backups")
        if (!dir.isDirectory) return null
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".db") }
            ?.maxByOrNull { it.lastModified() }
    }
}
