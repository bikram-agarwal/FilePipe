package dev.bikram.filepipe.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.bikram.filepipe.APP_DATABASE_NAME
import dev.bikram.filepipe.AppDatabase
import dev.bikram.filepipe.LEGACY_APP_DATABASE_NAME
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.data.repository.legacyFilenamePatternAsLines
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val migration2To3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE run_history ADD COLUMN cancelledUnprocessedCount INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE run_history ADD COLUMN operationMode TEXT NOT NULL DEFAULT 'MOVE'",
                )
                db.execSQL(
                    "ALTER TABLE run_history ADD COLUMN copyCreatedDestFolderUris TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL("ALTER TABLE rules ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE rules SET sortOrder = id")
            }
        }

    private val migration5To6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN recreateDestinationSubfolders INTEGER NOT NULL DEFAULT 0",
                )
                // Preserve the previous recursive rule behavior for existing users.
                db.execSQL(
                    "UPDATE rules SET recreateDestinationSubfolders = CASE WHEN scanSubdirectories = 1 THEN 1 ELSE 0 END",
                )
            }
        }

    private val migration6To7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN trashedAt INTEGER")
            }
        }

    private val migration7To8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN cardModeOverride INTEGER NOT NULL DEFAULT 0")
            }
        }

    private val migration8To9 =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN orientation TEXT")
            }
        }

    private val migration9To10 =
        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN isRegexPattern INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rules ADD COLUMN isExcludeRegexPattern INTEGER NOT NULL DEFAULT 0")
            }
        }

    private val migration10To11 =
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN lastRunStartedAt INTEGER")
                // Seed from existing history so the "last run" sort keeps working for current users.
                db.execSQL(
                    """
                    UPDATE rules SET lastRunStartedAt =
                        (SELECT MAX(startedAt) FROM run_history WHERE run_history.ruleId = rules.id)
                    """.trimIndent(),
                )
            }
        }

    internal val migration11To12 =
        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE files_moved ADD COLUMN undoStatus TEXT NOT NULL DEFAULT 'PENDING'",
                )
                db.execSQL(
                    """
                    UPDATE files_moved SET undoStatus = 'UNDONE'
                    WHERE success = 1 AND skipped = 0
                        AND runHistoryId IN (
                            SELECT id FROM run_history
                            WHERE status = 'UNDONE' OR isReversed = 1
                        )
                    """.trimIndent(),
                )
                // Individual outcomes were not recorded before v12. Treat every successful file
                // in a partial undo as interrupted so retry can reconcile it from disk state.
                db.execSQL(
                    """
                    UPDATE files_moved SET undoStatus = 'IN_PROGRESS'
                    WHERE success = 1 AND skipped = 0
                        AND runHistoryId IN (
                            SELECT id FROM run_history WHERE status = 'PARTIAL_UNDONE'
                        )
                    """.trimIndent(),
                )
            }
        }

    /**
     * Adds the rule group column, and rewrites filename patterns as one pattern per line.
     *
     * The second part is data only. Every pattern box now holds one pattern per line
     * (splitPatternLines). A wildcard filename pattern used to be comma separated, so its commas become
     * line breaks, which reads exactly as before; a RegEx one was a single expression, already one line
     * (legacyFilenamePatternAsLines). Exclude patterns were always stored as a list, so they need
     * nothing. Backups written before this change get the same conversion on import
     * (RuleBackupDto.patternsOnePerLine).
     */
    internal val migration12To13 =
        object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN groupName TEXT")

                val linesById = mutableMapOf<Long, String>()
                db.query("SELECT id, filenamePattern FROM rules WHERE isRegexPattern = 0 AND filenamePattern LIKE '%,%'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val pattern = cursor.getString(1) ?: continue
                        val lines = legacyFilenamePatternAsLines(pattern, isRegexPattern = false)
                        if (lines != pattern) linesById[cursor.getLong(0)] = lines
                    }
                }
                linesById.forEach { (id, lines) ->
                    db.execSQL("UPDATE rules SET filenamePattern = ? WHERE id = ?", arrayOf<Any>(lines, id))
                }
            }
        }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        migrateLegacyDatabaseNameIfNeeded(context)
        return Room
            .databaseBuilder(context, AppDatabase::class.java, APP_DATABASE_NAME)
            .addMigrations(
                migration2To3,
                migration5To6,
                migration6To7,
                migration7To8,
                migration8To9,
                migration9To10,
                migration10To11,
                migration11To12,
                migration12To13,
            )
            // Fail closed if a migration path is missing. Recreating this database would silently
            // erase rules and run history, while a failed open leaves the original file recoverable.
            .build()
    }

    @Provides
    fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideRunHistoryDao(db: AppDatabase): RunHistoryDao = db.runHistoryDao()

    @Provides
    fun provideFileMovedDao(db: AppDatabase): FileMovedDao = db.fileMovedDao()

    private fun migrateLegacyDatabaseNameIfNeeded(context: Context) {
        val legacyDatabase = context.getDatabasePath(LEGACY_APP_DATABASE_NAME)
        val targetDatabase = context.getDatabasePath(APP_DATABASE_NAME)
        if (!legacyDatabase.exists() || targetDatabase.exists()) return

        legacyDatabase.parentFile?.mkdirs()
        checkpointLegacyDatabase(legacyDatabase)

        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val legacySidecar = File("${legacyDatabase.path}$suffix")
            val targetSidecar = File("${targetDatabase.path}$suffix")
            if (legacySidecar.exists() && !targetSidecar.exists()) {
                legacySidecar.moveTo(targetSidecar)
            }
        }
        legacyDatabase.moveTo(targetDatabase)
    }

    private fun checkpointLegacyDatabase(databaseFile: File) {
        runCatching {
            SQLiteDatabase
                .openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { database ->
                    database.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray()).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
        }
    }

    private fun File.moveTo(target: File) {
        if (!renameTo(target)) {
            error("Unable to rename database from $path to ${target.path}")
        }
    }
}
