package dev.bikram.filepipe.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rules ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE rules SET sortOrder = id")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "media_organizer.db")
            .addMigrations(migration4To5)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideRunHistoryDao(db: AppDatabase): RunHistoryDao = db.runHistoryDao()

    @Provides
    fun provideFileMovedDao(db: AppDatabase): FileMovedDao = db.fileMovedDao()
}
