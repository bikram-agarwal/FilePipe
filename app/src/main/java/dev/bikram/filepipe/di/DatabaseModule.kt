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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        migrateLegacyDatabaseNameIfNeeded(context)
        return Room
            .databaseBuilder(context, AppDatabase::class.java, APP_DATABASE_NAME)
            .addMigrations(migration2To3, migration5To6, migration6To7, migration7To8)
            .fallbackToDestructiveMigration(dropAllTables = true)
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
