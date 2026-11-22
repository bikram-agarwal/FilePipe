package dev.bikram.filepipe.di

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bikram.filepipe.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migration11To12BackfillsUndoStatuses() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 11).apply {
            execSQL(
                """
                INSERT INTO run_history
                    (id, ruleName, triggeredBy, startedAt, status, totalFilesFound,
                     totalFilesMoved, totalFilesFailed, isReversed)
                VALUES
                    (1, 'Undone', 'MANUAL', 1, 'UNDONE', 1, 1, 0, 1),
                    (2, 'Partial', 'MANUAL', 2, 'PARTIAL_UNDONE', 1, 1, 0, 0),
                    (3, 'Complete', 'MANUAL', 3, 'SUCCESS', 1, 1, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO files_moved
                    (id, runHistoryId, fileName, sourceUri, destinationUri, fileSizeBytes,
                     movedAt, success, skipped)
                VALUES
                    (1, 1, 'undone.txt', 'source-1', 'destination-1', 1, 1, 1, 0),
                    (2, 2, 'partial.txt', 'source-2', 'destination-2', 1, 2, 1, 0),
                    (3, 3, 'pending.txt', 'source-3', 'destination-3', 1, 3, 1, 0)
                """.trimIndent(),
            )
            close()
        }

        val migratedDatabase =
            migrationHelper.runMigrationsAndValidate(
                TEST_DATABASE_NAME,
                12,
                true,
                DatabaseModule.migration11To12,
            )
        val cursor = migratedDatabase.query("SELECT id, undoStatus FROM files_moved ORDER BY id")
        cursor.use {
            check(it.moveToFirst())
            assertEquals("UNDONE", it.getString(1))
            check(it.moveToNext())
            assertEquals("IN_PROGRESS", it.getString(1))
            check(it.moveToNext())
            assertEquals("PENDING", it.getString(1))
        }
    }

    @Test
    fun migration12To13AddsRuleGroupName() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 12).apply {
            execSQL(
                """
                INSERT INTO rules
                    (id, name, sourceFolderPaths, destinationFolderPath, fileExtensions, isEnabled,
                     sortOrder, cardModeOverride, createdAt, updatedAt, conflictPolicy, operationMode,
                     scanSubdirectories, recreateDestinationSubfolders, suppress_missing_source_folder_card,
                     iconKey, excludePatterns, isRegexPattern, isExcludeRegexPattern)
                VALUES
                    (1, 'Existing Rule', '[]', '', '[]', 1, 0, 0, 1, 1, 'RENAME_SUFFIX', 'MOVE',
                     0, 0, 0, 'DEFAULT', '[]', 0, 0)
                """.trimIndent(),
            )
            close()
        }

        val migratedDatabase =
            migrationHelper.runMigrationsAndValidate(
                TEST_DATABASE_NAME,
                13,
                true,
                DatabaseModule.migration12To13,
            )
        val cursor = migratedDatabase.query("SELECT id, groupName FROM rules WHERE id = 1")
        cursor.use {
            check(it.moveToFirst())
            assertEquals(null, it.getString(1))
        }
    }

    @Test
    fun migration12To13PutsWildcardFilenamePatternsOnePerLine() {
        migrationHelper.createDatabase(TEST_DATABASE_NAME, 12).apply {
            // 1: wildcard list, whose commas become lines; 2: RegEx, one expression with literal
            // commas, left alone; 3: wildcard without a comma, left alone.
            listOf(
                Triple(1, "IMG_*, PXL_* ,", 0),
                Triple(2, """^backup, old \d{2,4}""", 1),
                Triple(3, "Screenshot_*", 0),
            ).forEach { (id, pattern, isRegex) ->
                execSQL(
                    """
                    INSERT INTO rules
                        (id, name, sourceFolderPaths, destinationFolderPath, fileExtensions, isEnabled,
                         sortOrder, cardModeOverride, createdAt, updatedAt, conflictPolicy, operationMode,
                         scanSubdirectories, recreateDestinationSubfolders, suppress_missing_source_folder_card,
                         iconKey, filenamePattern, excludePatterns, isRegexPattern, isExcludeRegexPattern)
                    VALUES
                        (?, 'Rule', '[]', '', '[]', 1, 0, 0, 1, 1, 'RENAME_SUFFIX', 'MOVE',
                         0, 0, 0, 'DEFAULT', ?, '[]', ?, 0)
                    """.trimIndent(),
                    arrayOf<Any>(id, pattern, isRegex),
                )
            }
            close()
        }

        val migratedDatabase =
            migrationHelper.runMigrationsAndValidate(
                TEST_DATABASE_NAME,
                13,
                true,
                DatabaseModule.migration12To13,
            )
        val patternsById =
            migratedDatabase.query("SELECT id, filenamePattern FROM rules ORDER BY id").use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getString(1))
                }
            }
        assertEquals("IMG_*\nPXL_*", patternsById[1])
        assertEquals("""^backup, old \d{2,4}""", patternsById[2])
        assertEquals("Screenshot_*", patternsById[3])
    }

    private companion object {
        const val TEST_DATABASE_NAME = "migration-test"
    }
}
