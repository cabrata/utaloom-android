package com.metrolist.music.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartShuffleTest {
    @Test
    fun `inserts a recommendation after every third item and appends leftovers`() {
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3, 7, 4, 5, 6, 8, 9),
            smartShuffleOrder(current = 0, base = listOf(1, 2, 3, 4, 5, 6), recommended = listOf(7, 8, 9)),
        )
    }

    @Test
    fun `current song and every index occur exactly once for small queues`() {
        for (size in 1..20) {
            for (current in 0 until size) {
                val remaining = (0 until size).filter { it != current }
                val (recs, base) = remaining.partition { it % 4 == 0 }
                val order = smartShuffleOrder(current, base, recs)
                assertEquals(current, order.first())
                assertEquals((0 until size).toList(), order.sorted())
            }
        }
        assertArrayEquals(intArrayOf(0, 1, 2), smartShuffleOrder(0, listOf(1, 2), emptyList()))
        assertArrayEquals(intArrayOf(0, 1), smartShuffleOrder(0, emptyList(), listOf(1)))
    }
}
