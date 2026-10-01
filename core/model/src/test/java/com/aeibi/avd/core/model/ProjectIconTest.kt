package com.aeibi.avd.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIconTest {
    @Test
    fun `copies input and output bytes`() {
        val input = byteArrayOf(1, 2, 3)
        val icon = ProjectIcon.fromPng(input)
        input[0] = 9

        val output = icon.copyPngBytes()
        output[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), icon.copyPngBytes())
        assertEquals(ProjectIcon.fromPng(byteArrayOf(1, 2, 3)), icon)
    }
}
