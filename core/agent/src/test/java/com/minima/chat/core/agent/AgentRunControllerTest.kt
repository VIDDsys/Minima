package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.run.AgentRunController
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AgentRunControllerTest {
    @Test
    fun requestStopInvokesBoundHandler() {
        val c = AgentRunController()
        val n = AtomicInteger(0)
        val stop = { n.incrementAndGet(); Unit }
        c.bind(stop)
        assertThat(c.isBound()).isTrue()
        c.requestStop()
        c.requestStop()
        assertThat(n.get()).isEqualTo(2)
    }

    @Test
    fun unboundRequestStopIsNoOp() {
        val c = AgentRunController()
        c.requestStop()
        assertThat(c.isBound()).isFalse()
    }

    @Test
    fun unbindClearsSameHandler() {
        val c = AgentRunController()
        val stop = { }
        c.bind(stop)
        c.unbind(stop)
        assertThat(c.isBound()).isFalse()
        c.requestStop() // no crash
    }

    @Test
    fun unbindOtherHandlerDoesNotClear() {
        val c = AgentRunController()
        val a = { }
        val b = { }
        c.bind(a)
        c.unbind(b)
        assertThat(c.isBound()).isTrue()
        c.unbind(null)
        assertThat(c.isBound()).isFalse()
    }
}
