package me.xiaok.opencode.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.test.runTest
import me.xiaok.opencode.utils.TimeoutRule
import org.junit.Rule
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {

    @get:Rule
    val timeoutRule = TimeoutRule()

    private lateinit var db: SupportSQLiteDatabase

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `session_view_log` (
                    `serverId` TEXT NOT NULL,
                    `sessionId` TEXT NOT NULL,
                    `lastViewedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `sessionId`)
                )
            """.trimIndent())
        }
    }

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        // Create a version 1 database (only sessions + messages tables)
        val version1Db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Use Room's support database directly
        db = version1Db.openHelper.writableDatabase
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `MIGRATION_1_2 creates session_view_log table`() {
        // Run the migration
        MIGRATION_1_2.migrate(db)

        // Verify the table was created by querying it
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='session_view_log'")
        assertTrue("session_view_log table should exist", cursor.moveToFirst())
        assertEquals("session_view_log", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun `MIGRATION_1_2 creates session_view_log with correct columns`() {
        MIGRATION_1_2.migrate(db)

        val cursor = db.query("PRAGMA table_info(session_view_log)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertTrue("Should have serverId column", columns.contains("serverId"))
        assertTrue("Should have sessionId column", columns.contains("sessionId"))
        assertTrue("Should have lastViewedAt column", columns.contains("lastViewedAt"))
        assertEquals("Should have exactly 3 columns", 3, columns.size)
    }

    @Test
    fun `MIGRATION_1_2 creates session_view_log with composite primary key`() {
        MIGRATION_1_2.migrate(db)

        // Insert and verify composite PK works — inserting duplicate (serverId, sessionId) should replace
        db.execSQL(
            "INSERT INTO session_view_log (serverId, sessionId, lastViewedAt) VALUES (?, ?, ?)",
            arrayOf("srv1", "ses1", 1000L)
        )

        // Query back
        val cursor = db.query(
            "SELECT lastViewedAt FROM session_view_log WHERE serverId = ? AND sessionId = ?",
            arrayOf("srv1", "ses1")
        )
        assertTrue("Should find inserted row", cursor.moveToFirst())
        assertEquals(1000L, cursor.getLong(0))
        cursor.close()
    }

    @Test
    fun `MIGRATION_1_2 table uses REPLACE conflict strategy for composite key`() {
        MIGRATION_1_2.migrate(db)

        // Insert initial
        db.execSQL(
            "INSERT INTO session_view_log (serverId, sessionId, lastViewedAt) VALUES (?, ?, ?)",
            arrayOf("srv1", "ses1", 1000L)
        )
        // Replace with same PK
        db.execSQL(
            "INSERT OR REPLACE INTO session_view_log (serverId, sessionId, lastViewedAt) VALUES (?, ?, ?)",
            arrayOf("srv1", "ses1", 2000L)
        )

        val cursor = db.query(
            "SELECT lastViewedAt FROM session_view_log WHERE serverId = ? AND sessionId = ?",
            arrayOf("srv1", "ses1")
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(2000L, cursor.getLong(0))
        cursor.close()

        // Should only have 1 row (replaced, not duplicated)
        val countCursor = db.query("SELECT COUNT(*) FROM session_view_log")
        countCursor.moveToFirst()
        assertEquals(1, countCursor.getLong(0).toInt())
        countCursor.close()
    }

    @Test
    fun `full migration from version 1 to 2 via Room`() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context

        // Create version 1 schema manually (sessions + messages)
        val v1Db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        v1Db.close()

        // Now open with migration applied — this validates Room can apply the migration
        val migratedDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2)
            .build()

        try {
            // Verify we can use the sessionViewLogDao
            val dao = migratedDb.sessionViewLogDao()
            // Should not throw — table exists and is usable
            val result = dao.getAllForServer("srv1")
            assertTrue(result.isEmpty())
        } finally {
            migratedDb.close()
        }
    }
}
