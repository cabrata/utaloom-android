package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChunkedDataSourceTest {
    private val data = ByteArray(10) { it.toByte() }

    private class Recording(private val upstream: DataSource) : DataSource by upstream {
        val opened = mutableListOf<Pair<Long, Long>>()

        override fun open(dataSpec: DataSpec): Long {
            opened += dataSpec.position to dataSpec.length
            return upstream.open(dataSpec)
        }

        override fun addTransferListener(transferListener: TransferListener) = Unit
    }

    private fun readAll(source: DataSource, spec: DataSpec): ByteArray {
        val length = source.open(spec)
        val out = ByteArray(length.toInt())
        var offset = 0
        while (offset < out.size) {
            val read = source.read(out, offset, out.size - offset)
            if (read == C.RESULT_END_OF_INPUT) break
            offset += read
        }
        assertEquals(out.size, offset)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
        source.close()
        return out
    }

    @Test
    fun `open ended read is split into bounded ranges`() {
        val upstream = Recording(ByteArrayDataSource(data))
        val source = ChunkedDataSource(upstream, chunkBytes = 4)

        val bytes = readAll(source, DataSpec(Uri.parse("https://r1.googlevideo.com/videoplayback?clen=10")))

        assertArrayEquals(data, bytes)
        assertEquals(listOf(0L to 4L, 4L to 4L, 8L to 2L), upstream.opened)
    }

    @Test
    fun `read from an offset stays within the requested length`() {
        val upstream = Recording(ByteArrayDataSource(data))
        val source = ChunkedDataSource(upstream, chunkBytes = 4)
        val spec = DataSpec.Builder()
            .setUri("https://r1.googlevideo.com/videoplayback?clen=10")
            .setPosition(3)
            .setLength(5)
            .build()

        val bytes = readAll(source, spec)

        assertArrayEquals(data.copyOfRange(3, 8), bytes)
        assertEquals(listOf(3L to 4L, 7L to 1L), upstream.opened)
    }

    @Test
    fun `url without clen is passed through`() {
        val upstream = Recording(ByteArrayDataSource(data))
        val source = ChunkedDataSource(upstream, chunkBytes = 4)

        val bytes = readAll(source, DataSpec(Uri.parse("https://example.com/addon.flac")))

        assertArrayEquals(data, bytes)
        assertEquals(listOf(0L to C.LENGTH_UNSET.toLong()), upstream.opened)
    }

    @Test
    fun `narrow clients get 512KiB ranges`() {
        assertEquals(512L * 1024, ChunkedDataSource.rangeBytesFor(Uri.parse("https://x.googlevideo.com/v?c=ANDROID_VR")))
        assertEquals(1024L * 1024, ChunkedDataSource.rangeBytesFor(Uri.parse("https://x.googlevideo.com/v?c=WEB_REMIX")))
    }
}
