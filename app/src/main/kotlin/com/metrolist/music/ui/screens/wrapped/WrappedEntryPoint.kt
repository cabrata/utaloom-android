/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

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
internal interface WrappedEntryPoint {
    fun databaseDao(): DatabaseDao
    fun database(): MusicDatabase
    @ApplicationScope fun applicationScope(): CoroutineScope
}

internal fun provideWrappedManager(context: Context): WrappedManager {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WrappedEntryPoint::class.java
    )
    return WrappedManager(
        databaseDao = entryPoint.databaseDao(),
        context = context.applicationContext
    )
}
