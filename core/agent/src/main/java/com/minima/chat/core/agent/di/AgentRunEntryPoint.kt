package com.minima.chat.core.agent.di

import com.minima.chat.core.agent.run.AgentRunController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AgentRunEntryPoint {
    fun agentRunController(): AgentRunController
}
