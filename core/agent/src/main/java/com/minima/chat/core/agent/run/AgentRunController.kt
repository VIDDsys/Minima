package com.minima.chat.core.agent.run

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide gate so notification Stop and in-chat Stop cancel the **same** run.
 * ChatViewModel binds [stop]; [AgentForegroundService] invokes [requestStop].
 */
@Singleton
class AgentRunController @Inject constructor() {
    private val handler = AtomicReference<(() -> Unit)?>(null)

    fun bind(stop: () -> Unit) {
        handler.set(stop)
    }

    fun unbind(stop: (() -> Unit)? = null) {
        if (stop == null) {
            handler.set(null)
            return
        }
        handler.compareAndSet(stop, null)
    }

    /** Same path as composer Stop. Idempotent if unbound. */
    fun requestStop() {
        handler.get()?.invoke()
    }

    fun isBound(): Boolean = handler.get() != null
}
