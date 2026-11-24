/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 * Ported from BitChord (GPL-3.0), https://github.com/kushagrasinghx/BitChord.
 */

package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Fetches a googlevideo stream as a run of bounded ranges instead of one open-ended read.
 *
 * googlevideo paces an open-ended response down to roughly playback speed (~15 kB/s), so the
 * player can never build a cushion and every network hiccup becomes an audible stall. The same
 * bytes asked for as bounded ranges arrive at line rate (MB/s). Everything above this sees one
 * continuous stream of the length it asked for.
 *
 * The total size comes from the URL's `clen` parameter; URLs without it (add-ons, local files,
 * URLs already carrying a `range` parameter) are forwarded untouched.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long,
) : DataSource {
    private var baseSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var rangeBytes = chunkBytes
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        baseSpec = dataSpec
        position = dataSpec.position

        val total = dataSpec.uri.takeIf { it.isHierarchical && it.getQueryParameter("range") == null }
            ?.getQueryParameter("clen")?.toLongOrNull()
        if (total == null) {
            passthrough = true
            chunkOpen = true
            return upstream.open(dataSpec)
        }

        passthrough = false
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else minOf(total, position + dataSpec.length)
        bytesRemaining = (end - position).coerceAtLeast(0L)
        rangeBytes = minOf(chunkBytes, rangeBytesFor(dataSpec.uri))
        if (bytesRemaining > 0) openChunk()
        return bytesRemaining
    }

    private fun openChunk() {
        val length = minOf(rangeBytes, bytesRemaining)
        upstream.open(
            requireNotNull(baseSpec).buildUpon()
                .setPosition(position)
                .setLength(length)
                .build(),
        )
        chunkRemaining = length
        chunkOpen = true
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // A range that ends early is re-opened for the part that didn't arrive, which is also how
        // the step to the next range happens. The attempt limit stops a server sending nothing
        // from spinning here forever.
        repeat(MAX_EMPTY_RANGES) {
            if (chunkRemaining == 0L) {
                closeChunk()
                openChunk()
            }
            val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
            if (read != C.RESULT_END_OF_INPUT) {
                position += read
                chunkRemaining -= read
                bytesRemaining -= read
                return read
            }
            chunkRemaining = 0L
        }
        return C.RESULT_END_OF_INPUT
    }

    private fun closeChunk() {
        if (chunkOpen) {
            upstream.close()
            chunkOpen = false
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: baseSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        baseSpec = null
        bytesRemaining = 0L
        chunkRemaining = 0L
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }

    companion object {
        const val DEFAULT_CHUNK_BYTES = 1024L * 1024
        private const val NARROW_CHUNK_BYTES = 512L * 1024
        private const val MAX_EMPTY_RANGES = 3

        /** ANDROID_VR and TVHTML5_SIMPLY URLs refuse ranges larger than 512 KiB. */
        internal fun rangeBytesFor(uri: Uri): Long {
            val client = uri.getQueryParameter("c")?.uppercase() ?: return DEFAULT_CHUNK_BYTES
            return if (client == "ANDROID_VR" || client.startsWith("TVHTML5_SIMPLY")) NARROW_CHUNK_BYTES else DEFAULT_CHUNK_BYTES
        }
    }
}
