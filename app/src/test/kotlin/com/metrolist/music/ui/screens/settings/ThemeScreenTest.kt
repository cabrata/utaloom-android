package com.metrolist.music.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemeScreenTest {
    @Test
    fun includesUtaloomCoralBrandPalette() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coralPalette = PaletteColors.firstOrNull { it.seedColor == Color(0xFFF47757) }

        assertTrue("Expected Utaloom Coral in PaletteColors", coralPalette != null)
        assertEquals(R.string.palette_utaloom_coral, coralPalette?.nameRes)
        assertEquals("Utaloom Coral", context.getString(R.string.palette_utaloom_coral))
    }
}
