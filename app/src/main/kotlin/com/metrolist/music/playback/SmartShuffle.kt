package com.metrolist.music.playback

/** Current item first, then one recommendation after every three base songs. */
internal fun smartShuffleOrder(
    current: Int,
    base: List<Int>,
    recommended: List<Int>,
): IntArray {
    val order = ArrayList<Int>(base.size + recommended.size + 1)
    order += current
    val recs = recommended.iterator()
    base.forEachIndexed { i, index ->
        order += index
        if ((i + 1) % 3 == 0 && recs.hasNext()) order += recs.next()
    }
    recs.forEachRemaining { order += it }
    return order.toIntArray()
}
