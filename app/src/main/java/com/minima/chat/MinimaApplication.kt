package com.minima.chat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.minima.chat.core.agent.notify.AgentNotifier
import com.minima.chat.core.agent.schedule.ScheduledNoteScheduler
import com.minima.chat.core.agent.skills.SkillsLoader
import com.minima.chat.core.data.workspace.PublicMinimaWorkspace
import com.minima.chat.core.data.repository.SettingsRepository
import com.minima.chat.core.network.api.OkHttpClientHolder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MinimaApplication : Application(), Configuration.Provider {
    @Inject lateinit var skillsLoader: SkillsLoader
    @Inject lateinit var agentNotifier: AgentNotifier
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var scheduledNoteScheduler: ScheduledNoteScheduler
    @Inject lateinit var okHttpClientHolder: OkHttpClientHolder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            skillsLoader.ensureBundledSkills()
            agentNotifier.ensureChannel()
            PublicMinimaWorkspace.ensureCreated(this)
        }
        appScope.launch {
            runCatching { PublicMinimaWorkspace.ensureCreated(this@MinimaApplication) }
        }
        appScope.launch {
            runCatching {
                val enabled = settingsRepository.settings.first().scheduledNoteEnabled
                scheduledNoteScheduler.setEnabled(enabled)
            }
        }
        appScope.launch {
            settingsRepository.settings.collectLatest { s ->
                runCatching { okHttpClientHolder.applyCertificatePins(s.certPins) }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
