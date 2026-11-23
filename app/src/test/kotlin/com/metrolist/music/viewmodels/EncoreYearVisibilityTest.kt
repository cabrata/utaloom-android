package com.metrolist.music.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoreYearVisibilityTest {
    @Test
    fun `new year shows recap again after viewing previous year`() {
        assertFalse(shouldShowEncore(seenYear = 2025, currentYear = 2025, enabled = false))
        assertTrue(shouldShowEncore(seenYear = 2025, currentYear = 2026, enabled = false))
        assertTrue(shouldShowEncore(seenYear = 2026, currentYear = 2026, enabled = true))
    }
}
