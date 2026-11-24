package com.metrolist.music.ui.screens.encore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoreBestPartTest {

    // 200 s song in 2 s segments: a slow fall from the opening, with a chorus bump at 80–100 s.
    private fun chorusCurve() = Heatmap(
        segmentMs = 2_000,
        intensity = List(100) { i ->
            val trend = 0.5f - i * 0.002f
            if (i in 40..49) trend + 0.4f else trend
        },
    )

    @Test
    fun peakFindsWhereTheChorusStarts() {
        val peak = EncoreBestPart.peakMs(chorusCurve())
        assertNotNull(peak)
        assertTrue("peak at $peak", peak!! in 76_000L..82_000L)
    }

    @Test
    fun flatCurveHasNoPeak() {
        assertNull(EncoreBestPart.peakMs(Heatmap(2_000, List(100) { 0.4f })))
    }

    @Test
    fun pickStartsALittleBeforeThePeak() {
        val curve = chorusCurve()
        assertEquals(EncoreBestPart.peakMs(curve)!! - EncoreBestPart.LEAD_MS, EncoreBestPart.pick(curve))
    }

    @Test
    fun fallbackSkipsTheIntro() {
        assertEquals(45_000L, EncoreBestPart.pick(null))
        assertEquals(60_000L, EncoreBestPart.fallbackMs(200_000))
        assertEquals(0L, EncoreBestPart.fallbackMs(30_000))
    }

    @Test
    fun parsesHeatMarkersAnywhereInTheAnswer() {
        val markers = (0 until 12).joinToString(",") { i ->
            """{"startMillis":"${i * 5_000}","durationMillis":"5000","intensityScoreNormalized":${i / 12.0}}"""
        }
        val answer = Json.parseToJsonElement(
            """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":
            {"markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[$markers]}}}}]}}}""",
        ) as JsonObject
        val heat = EncoreBestPart.parseHeatmap(answer)!!
        assertEquals(5_000L, heat.segmentMs)
        assertEquals(12, heat.intensity.size)
    }

    @Test
    fun answerWithoutMarkersHasNoHeatmap() {
        assertNull(EncoreBestPart.parseHeatmap(Json.parseToJsonElement("""{"contents":{}}""") as JsonObject))
    }
}
