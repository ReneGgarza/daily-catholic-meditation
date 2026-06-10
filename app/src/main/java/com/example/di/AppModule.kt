package com.example.di

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.DiaryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(application: Application): AppDatabase {
        return AppDatabase.getDatabase(application)
    }

    @Provides
    @Singleton
    fun provideDiaryDao(database: AppDatabase) = database.diaryDao()

    @Provides
    @Singleton
    fun provideDiaryRepository(dao: com.example.data.DiaryDao) = DiaryRepository(dao)
}
