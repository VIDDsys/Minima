package com.minima.chat.core.agent.schedule

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.minima.chat.core.agent.notify.AgentNotifier
import com.minima.chat.core.agent.tools.workspaceRoot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class ScheduledNoteWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val agentNotifier: AgentNotifier,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Never throw out of doWork — missing sandbox must not crash the process.
        val root = try {
            workspaceRoot(applicationContext)
        } catch (t: Throwable) {
            notifyQuiet("Scheduled note skipped", "Sandbox unavailable: ${t.message ?: t.javaClass.simpleName}")
            return Result.failure()
        }

        return when (val outcome = ScheduledNoteLogic.writeScheduledNote(root)) {
            is ScheduledNoteLogic.Outcome.Written -> {
                notifyQuiet("Minima", "Scheduled note: ${outcome.file.name}")
                Result.success()
            }
            is ScheduledNoteLogic.Outcome.Unavailable -> {
                // Permanent for this run — retrying forever when disk is gone is worse than failing once.
                notifyQuiet("Scheduled note skipped", outcome.reason.take(120))
                Result.failure()
            }
        }
    }

    private fun notifyQuiet(title: String, body: String) {
        try {
            agentNotifier.notifyCompleted(title, body)
        } catch (_: Throwable) {
            // Notification permission / OEM quirks must not fail the worker.
        }
    }

    companion object {
        const val UNIQUE_NAME = "minima_scheduled_note"
    }
}

@Singleton
class ScheduledNoteScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun setEnabled(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val req = PeriodicWorkRequestBuilder<ScheduledNoteWorker>(24, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(
                ScheduledNoteWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        } else {
            wm.cancelUniqueWork(ScheduledNoteWorker.UNIQUE_NAME)
        }
    }
}
