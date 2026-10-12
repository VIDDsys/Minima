package com.minima.chat.core.agent.run

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exclusive gate for chat send / regenerate so two runs cannot overlap.
 * [tryBegin] must be paired with [finish] for the same generation, or [cancelAll].
 *
 * Stop uses [beginStopHold] / [endStopHold] so cleanup can join the dying job and
 * apply a post-join createdAt ceiling without letting a new send start mid-cleanup.
 */
class ChatRunGate {
    private val active = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val stopHeld = AtomicBoolean(false)

    /** @return generation token if acquired, null if another run is active. */
    fun tryBegin(): Int? {
        if (!active.compareAndSet(false, true)) return null
        return generation.incrementAndGet()
    }

    /** Release only if [gen] is still the current run. */
    fun finish(gen: Int) {
        if (generation.get() == gen) {
            active.set(false)
        }
    }

    /** Invalidate the current run (Stop) and free the gate. */
    fun cancelAll() {
        generation.incrementAndGet()
        stopHeld.set(false)
        active.set(false)
    }

    /**
     * Invalidate [finish] for the current run but keep the gate closed until
     * [endStopHold]. Blocks [tryBegin] during Stop join + tool cancel.
     */
    fun beginStopHold() {
        generation.incrementAndGet()
        stopHeld.set(true)
        active.set(true)
    }

    /** Release after Stop cleanup (or onCleared). Idempotent if [cancelAll] won. */
    fun endStopHold() {
        if (stopHeld.compareAndSet(true, false)) {
            active.set(false)
        }
    }

    fun isActive(): Boolean = active.get()

    fun isStopHeld(): Boolean = stopHeld.get()
}
