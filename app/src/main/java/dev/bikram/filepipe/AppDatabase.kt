package dev.bikram.filepipe

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.data.local.database.Converters
import dev.bikram.filepipe.data.local.entity.FileMovedEntity
import dev.bikram.filepipe.data.local.entity.RuleEntity
import dev.bikram.filepipe.data.local.entity.RunHistoryEntity

@Database(
    entities = [RuleEntity::class, RunHistoryEntity::class, FileMovedEntity::class],
    // Literal required by Room KSP. Keep in sync with [dev.bikram.filepipe.domain.export.APP_DATABASE_SCHEMA_VERSION].
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun runHistoryDao(): RunHistoryDao
    abstract fun fileMovedDao(): FileMovedDao
}
