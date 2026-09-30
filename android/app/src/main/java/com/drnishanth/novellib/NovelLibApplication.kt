package com.drnishanth.novellib

import android.app.Application
import com.drnishanth.novellib.core.database.NovelDatabase
import com.drnishanth.novellib.data.repository.NovelRepository
import com.drnishanth.novellib.data.repository.ProfileRepository
import com.drnishanth.novellib.scraping.engine.RollbackManager
import com.drnishanth.novellib.scraping.registry.SourceDefinitionRepositoryClient

class NovelLibApplication : Application() {

    val database: NovelDatabase by lazy {
        NovelDatabase.getInstance(this)
    }

    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(
            userProfileDao = database.userProfileDao(),
            readerPreferencesDao = database.readerPreferencesDao()
        )
    }

    val rollbackManager: RollbackManager by lazy {
        RollbackManager(sourceDefinitionDao = database.sourceDefinitionDao())
    }

    val sourceDefinitionRepositoryClient: SourceDefinitionRepositoryClient by lazy {
        SourceDefinitionRepositoryClient(sourceDefinitionDao = database.sourceDefinitionDao())
    }

    val novelRepository: NovelRepository by lazy {
        NovelRepository(
            context = this,
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readerPreferencesDao = database.readerPreferencesDao(),
            sourceDefinitionDao = database.sourceDefinitionDao(),
            rollbackManager = rollbackManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NovelLibApplication
            private set
    }
}
