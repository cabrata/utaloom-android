/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.encore

import android.content.Context
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface EncoreEntryPoint {
    fun databaseDao(): DatabaseDao
    fun database(): MusicDatabase
    @ApplicationScope fun applicationScope(): CoroutineScope
}

internal fun provideEncoreManager(context: Context): EncoreManager {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        EncoreEntryPoint::class.java
    )
    return EncoreManager(
        databaseDao = entryPoint.databaseDao(),
        context = context.applicationContext
    )
}
