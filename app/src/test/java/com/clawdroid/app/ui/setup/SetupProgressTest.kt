package com.clawdroid.app.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupProgressTest {

    @Test
    fun firstStepShowsPartialProgress() {
        assertEquals(0.5f, setupProgressFraction(2, 6), 0.001f)
        assertEquals(50, setupProgressPercent(2, 6))
    }

    @Test
    fun lastStepReachesHundredPercent() {
        assertEquals(100, setupProgressPercent(5, 6))
    }

    @Test
    fun singleStepCountsAsComplete() {
        assertEquals(100, setupProgressPercent(0, 1))
    }
}
