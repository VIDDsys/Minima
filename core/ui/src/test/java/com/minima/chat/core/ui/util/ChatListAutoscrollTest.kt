package com.minima.chat.core.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatListAutoscrollTest {
    @Test
    fun atBottom_sticks() {
        assertThat(ChatListAutoscroll.nextStuck(0, 0, currentlyStuck = false, userScrolling = false)).isTrue()
    }

    @Test
    fun scrolledUp_unsticks() {
        assertThat(ChatListAutoscroll.nextStuck(3, 0, currentlyStuck = true, userScrolling = true)).isFalse()
        assertThat(ChatListAutoscroll.nextStuck(1, 10, currentlyStuck = true, userScrolling = false)).isFalse()
    }

    @Test
    fun shouldAutoScroll_requiresStuckAndItems() {
        assertThat(ChatListAutoscroll.shouldAutoScroll(true, 5)).isTrue()
        assertThat(ChatListAutoscroll.shouldAutoScroll(false, 5)).isFalse()
        assertThat(ChatListAutoscroll.shouldAutoScroll(true, 0)).isFalse()
    }
}
