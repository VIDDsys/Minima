package com.minima.chat.core.agent.di

import com.minima.chat.core.agent.tools.NowTool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {
    @Provides
    @Singleton
    fun provideNow(): NowTool = NowTool()
}
