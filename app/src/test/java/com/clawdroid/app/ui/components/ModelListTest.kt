package com.clawdroid.app.ui.components

import com.clawdroid.app.core.config.OpenCodeZen
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelListTest {

    private val isFree: (String) -> Boolean = OpenCodeZen::isFree

    @Test
    fun partitionsFreeFirstAndCapsPaid() {
        val models = listOf("gpt-4o", "big-pickle", "m1-free", "m2", "m3")
        val out = partitionZenModels(models, isFree, paidLimit = 2)

        assertEquals(listOf("big-pickle", "m1-free"), out.free)
        assertEquals(listOf("gpt-4o", "m2"), out.paid)
    }

    @Test
    fun queryFiltersCaseInsensitively() {
        val models = listOf("big-pickle", "llama-3.3-70b-versatile", "gpt-4o")

        assertEquals(listOf("llama-3.3-70b-versatile"), visibleModelOptions(models, "LLAMA"))
    }

    @Test
    fun blankQueryReturnsCappedList() {
        val models = (0 until 80).map { "model-$it" }

        assertEquals(60, visibleModelOptions(models, "").size)
    }

    @Test
    fun shippedFreeDetectionIsCaseInsensitive() {
        assertEquals(true, OpenCodeZen.isFree("X-FREE"))
        assertEquals(true, OpenCodeZen.isFree("MODEL-FREE"))
        assertEquals(false, OpenCodeZen.isFree("gpt-4o"))
    }
}
