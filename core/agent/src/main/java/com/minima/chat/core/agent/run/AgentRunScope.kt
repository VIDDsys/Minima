package com.minima.chat.core.agent.run

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped agent work survives ChatViewModel / Nav dispose.
 * Runs keep going while the app process is alive; only explicit Stop cancels.
 */
@Singleton
class AgentRunScope @Inject constructor() {
    private val supervisor = SupervisorJob()
    val scope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Default)

    private val jobRef = AtomicReference<Job?>(null)
    private val sessionRef = AtomicReference<String?>(null)

    fun track(sessionId: String, job: Job) {
        sessionRef.set(sessionId)
        jobRef.set(job)
        job.invokeOnCompletion {
            jobRef.compareAndSet(job, null)
            sessionRef.compareAndSet(sessionId, null)
        }
    }

    fun isRunningFor(sessionId: String): Boolean {
        val job = jobRef.get() ?: return false
        return job.isActive && sessionRef.get() == sessionId
    }

    fun isAnyRunning(): Boolean = jobRef.get()?.isActive == true

    fun activeSessionId(): String? =
        sessionRef.get()?.takeIf { jobRef.get()?.isActive == true }

    fun activeJob(): Job? = jobRef.get()?.takeIf { it.isActive }

    fun cancelActive() {
        jobRef.getAndSet(null)?.cancel()
        sessionRef.set(null)
    }
}
