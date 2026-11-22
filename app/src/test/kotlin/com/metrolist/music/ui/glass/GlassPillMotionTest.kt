package com.metrolist.music.ui.glass

import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassPillMotionTest {
    @Test
    fun motionRetargetsSettlesAndStopsAtRest() {
        // A cancelled scope prevents a live frame loop. Tick the production physics deterministically.
        val pill = GlassPillMotion(CoroutineScope(Job().apply { cancel() }), 0).apply { stepDp = 72f }
        var now = 0.0
        fun frames(count: Int) {
            repeat(count) {
                now += 1.0 / 60
                pill.tick(1.0 / 60, now)
                val size = pill.liveSize(Size(72f, 56f), Size(94f, 73f))
                assertTrue(pill.position.isFinite() && size.width.isFinite() && size.height.isFinite())
                assertTrue(size.width > 0f && size.height > 0f)
                assertTrue(pill.material in 0f..1f && pill.glassPresence in 0f..1f)
            }
        }

        pill.animateTo(4)
        frames(8)
        assertFalse(pill.isFlat)
        val travellingPosition = pill.position
        pill.animateTo(1)
        assertEquals(travellingPosition, pill.position, 0f)
        frames(360)
        assertEquals(1f, pill.position, 0.001f)
        assertTrue(pill.isFlat)
        assertFalse(pill.tick(1.0 / 60, now + 1.0 / 60))

        pill.startDrag()
        pill.dragTo(3.25f)
        frames(20)
        pill.release(3)
        frames(360)
        assertEquals(3f, pill.position, 0.001f)
        assertTrue(pill.isFlat)

        pill.reduceMotion = true
        pill.animateTo(0)
        assertEquals(0f, pill.position, 0f)
        assertTrue(pill.isFlat)
    }
}
