package com.aeibi.avd.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.aeibi.avd.core.database.project.ProjectDao
import com.aeibi.avd.core.database.project.ProjectIconRow
import com.aeibi.avd.core.database.project.ProjectRow
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [ProjectRow::class, ProjectIconRow::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, "avd.db").build()

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao = database.projectDao()
}
