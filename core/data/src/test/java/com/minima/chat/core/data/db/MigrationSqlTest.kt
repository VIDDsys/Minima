package com.minima.chat.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Creates a legacy SQLite schema, applies MinimaMigrations, verifies chat rows survive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationSqlTest {
    private var db: MinimaDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun migrate_v1_to_v6_preserves_sessions_and_messages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("minima-mig-test.db")

        openLegacy(context, "minima-mig-test.db", version = 1) { sqlite ->
            createV1(sqlite)
            sqlite.execSQL(
                "INSERT INTO sessions (id, title, createdAt, updatedAt) VALUES ('s1', 'Hello', 1, 2)",
            )
            sqlite.execSQL(
                "INSERT INTO messages (id, sessionId, role, content, createdAt) VALUES ('m1', 's1', 'User', 'hi there', 3)",
            )
        }

        db = Room.databaseBuilder(context, MinimaDatabase::class.java, "minima-mig-test.db")
            .addMigrations(*MinimaMigrations.ALL)
            .allowMainThreadQueries()
            .build()

        val sessions = db!!.sessionDao().listAll()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].id).isEqualTo("s1")
        assertThat(sessions[0].title).isEqualTo("Hello")
        assertThat(sessions[0].pinned).isFalse()
        assertThat(sessions[0].archived).isFalse()

        val messages = db!!.messageDao().listBySession("s1")
        assertThat(messages).hasSize(1)
        assertThat(messages[0].content).isEqualTo("hi there")
        assertThat(messages[0].kind).isEqualTo("text")
        assertThat(messages[0].inputTokens).isEqualTo(0)
        assertThat(messages[0].outputTokens).isEqualTo(0)
        assertThat(messages[0].agentStep).isEqualTo(0)
        assertThat(messages[0].attachmentPath).isNull()
        assertThat(messages[0].attachmentMime).isNull()
    }

    @Test
    fun migrate_v3_to_v4_adds_token_columns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("minima-mig-v3.db")

        openLegacy(context, "minima-mig-v3.db", version = 3) { sqlite ->
            createV3(sqlite)
            sqlite.execSQL(
                "INSERT INTO sessions (id, title, createdAt, updatedAt, pinned, archived) VALUES ('s2', 'Pinned', 10, 11, 1, 0)",
            )
            sqlite.execSQL(
                """
                INSERT INTO messages (id, sessionId, role, content, createdAt, kind, toolName, toolArgs, toolResult, toolStatus, toolCallId)
                VALUES ('m2', 's2', 'Assistant', 'ok', 12, 'text', NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
        }

        db = Room.databaseBuilder(context, MinimaDatabase::class.java, "minima-mig-v3.db")
            .addMigrations(*MinimaMigrations.ALL)
            .allowMainThreadQueries()
            .build()

        val s = db!!.sessionDao().getById("s2")!!
        assertThat(s.pinned).isTrue()
        val m = db!!.messageDao().listBySession("s2").single()
        assertThat(m.inputTokens).isEqualTo(0)
        assertThat(m.content).isEqualTo("ok")
    }

    @Test
    fun migrate_v4_to_v5_adds_agentStep() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("minima-mig-v4.db")

        openLegacy(context, "minima-mig-v4.db", version = 4) { sqlite ->
            // schema seeded by openLegacy(createV4)
            sqlite.execSQL(
                "INSERT INTO sessions (id, title, createdAt, updatedAt, pinned, archived) VALUES ('s3', 'Agent', 20, 21, 0, 0)",
            )
            sqlite.execSQL(
                """
                INSERT INTO messages (id, sessionId, role, content, createdAt, kind, toolName, toolArgs, toolResult, toolStatus, toolCallId, inputTokens, outputTokens)
                VALUES ('m3', 's3', 'Tool', '🔧 now', 22, 'tool', 'now', '{}', 'ok', 'ok', 'call_x', 0, 0)
                """.trimIndent(),
            )
        }

        db = Room.databaseBuilder(context, MinimaDatabase::class.java, "minima-mig-v4.db")
            .addMigrations(*MinimaMigrations.ALL)
            .allowMainThreadQueries()
            .build()

        val m = db!!.messageDao().listBySession("s3").single()
        assertThat(m.agentStep).isEqualTo(0)
        assertThat(m.toolCallId).isEqualTo("call_x")
        assertThat(m.toolName).isEqualTo("now")
    }

    @Test
    fun migrate_v5_to_v6_adds_attachment_columns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("minima-mig-v5.db")

        openLegacy(context, "minima-mig-v5.db", version = 5) { sqlite ->
            sqlite.execSQL(
                "INSERT INTO sessions (id, title, createdAt, updatedAt, pinned, archived) VALUES ('s4', 'Vision', 30, 31, 0, 0)",
            )
            sqlite.execSQL(
                """
                INSERT INTO messages (id, sessionId, role, content, createdAt, kind, toolName, toolArgs, toolResult, toolStatus, toolCallId, inputTokens, outputTokens, agentStep)
                VALUES ('m4', 's4', 'User', 'pic', 32, 'text', NULL, NULL, NULL, NULL, NULL, 0, 0, 0)
                """.trimIndent(),
            )
        }

        db = Room.databaseBuilder(context, MinimaDatabase::class.java, "minima-mig-v5.db")
            .addMigrations(*MinimaMigrations.ALL)
            .allowMainThreadQueries()
            .build()

        val m = db!!.messageDao().listBySession("s4").single()
        assertThat(m.content).isEqualTo("pic")
        assertThat(m.attachmentPath).isNull()
        assertThat(m.attachmentMime).isNull()
    }

    @Test
    fun migrations_cover_1_through_6() {
        val versions = MinimaMigrations.ALL.map { it.startVersion to it.endVersion }
        assertThat(versions).containsExactly(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6).inOrder()
    }

    private fun openLegacy(
        context: Context,
        name: String,
        version: Int,
        block: (SupportSQLiteDatabase) -> Unit,
    ) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // schema filled by caller after open via writableDatabase —
                        // actually onCreate runs on first open; seed there via version-specific create.
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        // Force create empty db at requested user_version, then apply schema + seed in same connection.
        helper.writableDatabase.use { sqlite ->
            // onCreate may have run with empty body; build schema now if tables missing
            val cursor = sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sessions'")
            val exists = cursor.use { it.moveToFirst() }
            if (!exists) {
                when (version) {
                    1 -> createV1(sqlite)
                    3 -> createV3(sqlite)
                    4 -> createV4(sqlite)
                    5 -> createV5(sqlite)
                    else -> error("unsupported seed version $version")
                }
            }
            block(sqlite)
        }
        helper.close()
    }

    private fun createV1(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions` (
              `id` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `messages` (
              `id` TEXT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `role` TEXT NOT NULL,
              `content` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
    }

    private fun createV3(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sessions` (
              `id` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              `pinned` INTEGER NOT NULL DEFAULT 0,
              `archived` INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `messages` (
              `id` TEXT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `role` TEXT NOT NULL,
              `content` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `kind` TEXT NOT NULL DEFAULT 'text',
              `toolName` TEXT,
              `toolArgs` TEXT,
              `toolResult` TEXT,
              `toolStatus` TEXT,
              `toolCallId` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_toolCallId` ON `messages` (`toolCallId`)")
    }

    private fun createV4(db: SupportSQLiteDatabase) {
        createV3(db)
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN inputTokens INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN outputTokens INTEGER NOT NULL DEFAULT 0",
        )
    }

    private fun createV5(db: SupportSQLiteDatabase) {
        createV4(db)
        db.execSQL(
            "ALTER TABLE messages ADD COLUMN agentStep INTEGER NOT NULL DEFAULT 0",
        )
    }
}
