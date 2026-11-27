/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

@Serializable
data class MusicAddon(
    val url: String,
    val name: String,
    val enabled: Boolean = true,
    val params: Map<String, String> = emptyMap(),
    val version: String = "",
    val qualities: List<Quality> = emptyList(),
    val downloadsAllowed: Boolean = true,
) {
    @Serializable
    data class Quality(val value: String, val label: String)

    val quality: String? get() = params[MusicAddons.QUALITY]
}

class AddonAuthRequiredException : Exception("Add-on requires BitChord authentication")

/**
 * Client for BitChord-compatible music add-ons: plain HTTPS servers answering
 * `/manifest.json`, `/search?q=` and `/stream/{id}` with JSON.
 */
object MusicAddons {
    data class Stream(
        val url: String,
        val label: String,
        val cacheKey: String,
        val source: String,
        val codec: String? = null,
        val bitDepth: Int? = null,
        val sampleRateHz: Int? = null,
        val bitrateKbps: Int? = null,
        /** The add-on's own name for this rendition, e.g. `LOSSLESS`. */
        val quality: String? = null,
        val isDolbyAtmos: Boolean = false,
        val manifestMimeType: String? = null,
    )

    @Serializable
    internal data class Track(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val duration: Double? = null,
        val streamURL: String? = null,
        val audioQuality: String? = null,
        val audioMode: String? = null,
        val audioModes: List<String> = emptyList(),
        val format: String? = null,
        val atmos: Boolean? = null,
    ) {
        /** Some catalogues (Tidal) publish the Atmos mix as its own track, so only the row can say so. */
        val isDolbyAtmos: Boolean
            get() = atmos == true || ATMOS_HINT.containsMatchIn("$audioQuality $audioMode ${audioModes.joinToString(" ")} $format")
    }

    @Serializable
    private data class SearchResponse(val tracks: List<Track> = emptyList())

    @Serializable
    private data class StreamResponse(
        val url: String? = null,
        val codec: String? = null,
        val fileCodec: String? = null,
        val container: String? = null,
        val containerFormat: String? = null,
        val manifest: String? = null,
        val mediaType: String? = null,
        val mimeType: String? = null,
        val encrypted: JsonElement? = null,
        val sampleRate: Double? = null,
        val bitrate: Double? = null,
        val format: String? = null,
        val quality: String? = null,
        val streamQuality: String? = null,
        val audioQuality: String? = null,
        val bitDepth: Double? = null,
        val audioMode: String? = null,
        val audioModes: List<String> = emptyList(),
        val atmos: Boolean? = null,
        val error: String? = null,
    )

    @Serializable
    private data class Manifest(
        val id: String = "",
        val name: String = "",
        val version: String = "",
        val allowDownloads: JsonPrimitive? = null,
        val bitchordAuth: JsonPrimitive? = null,
        val settings: List<Setting> = emptyList(),
    )

    @Serializable
    private data class Setting(
        val key: String = "",
        @SerialName("default") val defaultValue: JsonPrimitive? = null,
        val options: List<Option> = emptyList(),
    )

    @Serializable
    private data class Option(val value: JsonPrimitive? = null, val label: String = "")

    const val QUALITY = "quality"
    private const val ATMOS = "atmos"

    // Add-ons name the immersive mix in their codec, format or quality fields, each in its own spelling.
    internal val ATMOS_HINT = Regex("atmos|dolby|eac3[_-]?joc|ec-?3", RegexOption.IGNORE_CASE)

    private const val DASH_MIME = "application/dash+xml"
    // Media3 Util.inferContentTypeForUriAndMimeType matches "application/x-mpegURL" case-sensitively.
    private const val HLS_MIME = "application/x-mpegURL"
    private const val MAX_STREAM_ATTEMPTS = 3

    private val KBPS_LABEL = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE)
    private val KHZ_LABEL = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE)
    private val BIT_DEPTH_LABEL = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE)
    private val HI_RES_HINT = Regex("""hi[-_ ]?res|24[-_ ]?bit""", RegexOption.IGNORE_CASE)
    private val LOSSLESS_LABEL = Regex("""lossless|hi[-_ ]?res""", RegexOption.IGNORE_CASE)
    private val LOSSLESS_CODECS = setOf(
        "flac", "alac", "wav", "pcm", "ape", "aiff",
        "audio/flac", "audio/x-flac", "audio/alac", "audio/wav", "audio/x-wav", "audio/raw",
    )
    private val SELF_DESCRIBING_CONTAINERS = setOf("flac", "wav", "mp3", "aiff", "alac")
    private val AUDIO_CODECS = setOf(
        "flac", "alac", "wav", "pcm", "ape", "aiff", "mp3", "aac", "he-aac",
        "m4a", "mp4", "ogg", "opus", "vorbis", "webm", "eac3-joc", "eac3",
    )
    private val NON_ATMOS_CODECS = setOf(
        "flac", "alac", "wav", "pcm", "ape", "aiff", "mp3", "aac", "he-aac", "opus", "vorbis",
    )
    private val ATMOS_CODECS = setOf("eac3-joc", "eac3")

    private const val DURATION_TOLERANCE_SEC = 3.0
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val FEATURING = Regex("\\s[(\\[]?(feat|ft|featuring)\\b.*", RegexOption.IGNORE_CASE)
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    // shortcut: a fixed English word list decides "same recording"; port BitChord's
    // TrackMatcher if wrong versions slip through in other languages.
    private val VERSION_WORDS = setOf(
        "live", "remix", "instrumental", "acoustic", "karaoke", "demo", "edit", "cover",
        "sped", "slowed", "nightcore", "reverb", "mix", "unplugged", "orchestral",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val baseClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun decode(saved: String?): List<MusicAddon> =
        saved?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<List<MusicAddon>>(it) }.getOrNull() }
            .orEmpty()

    fun encode(addons: List<MusicAddon>): String = json.encodeToString(addons)

    /** The add-on root for whatever link the user pasted, or null when it is not an HTTPS address. */
    internal fun normalizeUrl(raw: String): String? {
        val base = raw.trim().removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/")
        return base.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
    }

    /** Reads the manifest behind [rawUrl] and returns the add-on ready to be stored. */
    suspend fun add(rawUrl: String): MusicAddon = withContext(Dispatchers.IO) {
        val base = normalizeUrl(rawUrl) ?: throw IOException("Not an HTTPS add-on address")
        fromManifest(base, previous = null)
    }

    /**
     * Health check: re-reads the manifest and runs a search. Returns [addon] refreshed
     * from the manifest (its switch and chosen quality kept), or throws what went wrong.
     */
    suspend fun check(addon: MusicAddon): MusicAddon = withContext(Dispatchers.IO) {
        val refreshed = fromManifest(addon.url, previous = addon)
        get<SearchResponse>(refreshed.url, listOf("search"), refreshed.params + ("q" to "test"))
        refreshed
    }

    private suspend fun fromManifest(base: String, previous: MusicAddon?): MusicAddon {
        val manifest = get<Manifest>(base, listOf("manifest.json"), emptyMap())
        if (manifest.id.isBlank()) throw IOException("Add-on manifest has no id")
        // BitChord-signed tokens cannot be produced outside the BitChord app.
        if (manifest.bitchordAuth?.content?.lowercase() in setOf("true", "1")) throw AddonAuthRequiredException()

        val params = manifest.settings
            .filter { it.key.isNotBlank() && it.defaultValue != null }
            .associate { it.key to it.defaultValue!!.content }
            .toMutableMap()
        val qualities = manifest.settings.firstOrNull { it.key == QUALITY }?.options.orEmpty().mapNotNull { option ->
            option.value?.content?.let { MusicAddon.Quality(it, option.label.ifBlank { it }) }
        }
        params[QUALITY] = previous?.quality?.takeIf { chosen -> qualities.isEmpty() || qualities.any { it.value == chosen } }
            ?: qualities.firstOrNull { "lossless" in it.value.lowercase() }?.value
            ?: params[QUALITY]
            ?: "LOSSLESS"

        return MusicAddon(
            url = base,
            name = manifest.name.ifBlank { base.toHttpUrl().host },
            enabled = previous?.enabled ?: true,
            params = params,
            version = manifest.version,
            qualities = qualities,
            downloadsAllowed = manifest.allowDownloads?.content?.lowercase() !in setOf("0", "false"),
        )
    }

    suspend fun resolveUpgrade(
        addons: List<MusicAddon>,
        title: String,
        artists: List<String>,
        durationSec: Int,
        playingBitrate: Int?,
        atmos: Boolean,
    ): Stream? = withContext(Dispatchers.IO) {
        val enabled = addons.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext null
        // Wait for every add-on (BitChord waitForAll): a fast Hi-Res server must not cancel a slower Atmos one.
        val candidates = coroutineScope {
            enabled.map { addon ->
                async {
                    try {
                        resolveFrom(addon, title, artists, durationSec, atmos)?.takeIf { worthUpgrading(it, playingBitrate) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("MusicAddons").w("%s failed: %s", addon.name, e.message)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        var best: Stream? = null
        for (candidate in candidates) {
            if (best == null || isBetter(candidate, best)) best = candidate
        }
        best
    }

    private fun isLossless(stream: Stream): Boolean =
        !stream.isDolbyAtmos && stream.codec?.lowercase() in LOSSLESS_CODECS

    internal fun isBetter(candidate: Stream, current: Stream): Boolean {
        if (candidate.isDolbyAtmos != current.isDolbyAtmos) return candidate.isDolbyAtmos
        val candLossless = isLossless(candidate)
        val currLossless = isLossless(current)
        if (candLossless != currLossless) return candLossless
        if (candLossless) {
            val depth = (candidate.bitDepth ?: 0).compareTo(current.bitDepth ?: 0)
            if (depth != 0) return depth > 0
            val rate = (candidate.sampleRateHz ?: 0).compareTo(current.sampleRateHz ?: 0)
            if (rate != 0) return rate > 0
        }
        return (candidate.bitrateKbps ?: 0) > (current.bitrateKbps ?: 0)
    }

    internal fun worthUpgrading(stream: Stream, playingBitrate: Int?): Boolean {
        if (stream.isDolbyAtmos) return true
        if (isLossless(stream)) return true
        // A narrow bitrate gain across codecs is not worth a mid-song interruption.
        return playingBitrate != null && stream.bitrateKbps?.let { it - playingBitrate / 1000 >= 96 } == true
    }

    private suspend fun resolveFrom(
        addon: MusicAddon,
        title: String,
        artists: List<String>,
        durationSec: Int,
        atmos: Boolean,
    ): Stream? {
        val params = requestParams(addon, atmos)
        val searchParams = params + buildMap {
            put("q", searchQuery(title, artists))
            title.trim().takeIf { it.isNotEmpty() }?.let { put("title", it) }
            artists.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { put("artist", it) }
        }
        val tracks = get<SearchResponse>(addon.url, listOf("search"), searchParams).tracks
        val (immersive, stereo) = tracks.partition { it.isDolbyAtmos }
        val immersiveMatches = if (atmos) pickMatches(immersive, title, artists, durationSec, requireDuration = true) else emptyList()
        val stereoMatches = pickMatches(stereo, title, artists, durationSec, requireDuration = true)
            .sortedByDescending { track ->
                when {
                    HI_RES_HINT.containsMatchIn("${track.audioQuality} ${track.format}") -> 2
                    LOSSLESS_LABEL.containsMatchIn("${track.audioQuality} ${track.format}") -> 1
                    else -> 0
                }
            }
        val candidates = (immersiveMatches.take(2) + stereoMatches).take(MAX_STREAM_ATTEMPTS)
        for (match in candidates) {
            val stream = try {
                get<StreamResponse>(addon.url, listOf("stream", match.id), params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("MusicAddons").d("%s stream failed for %s: %s", addon.name, match.id, e.message)
                null
            }
            if (isEncrypted(stream?.encrypted)) continue
            if (stream != null && stream.url.isNullOrBlank() && !stream.error.isNullOrBlank()) {
                Timber.tag("MusicAddons").w("%s no stream for %s: %s", addon.name, match.id, stream.error)
            }
            // HTTPS only: the player's data source would also open file:// and content:// URIs.
            val url = (stream?.url?.takeIf { it.isNotBlank() } ?: match.streamURL)
                ?.takeIf { it.toHttpUrlOrNull()?.isHttps == true } ?: continue
            buildStream(addon, match, stream, url, atmos)?.let { return it }
        }
        return null
    }

    private fun buildStream(
        addon: MusicAddon,
        match: Track,
        stream: StreamResponse?,
        url: String,
        atmos: Boolean,
    ): Stream? {
        val qualityText = if (stream != null) {
            "${stream.quality.orEmpty()} ${stream.streamQuality.orEmpty()} ${stream.audioQuality.orEmpty()} ${stream.format.orEmpty()}"
        } else {
            "${match.audioQuality.orEmpty()} ${match.format.orEmpty()}"
        }
        val statedCodec = normalizeCodec(stream?.codec?.ifBlank { null } ?: stream?.fileCodec?.ifBlank { null })
        val formatCodec = normalizeCodec(stream?.format?.ifBlank { null })
        val explicitNonAtmosAudio = statedCodec in NON_ATMOS_CODECS || formatCodec in NON_ATMOS_CODECS
        val isDolbyAtmos = stream?.atmos == true || (
            !explicitNonAtmosAudio && (
                ATMOS_HINT.containsMatchIn(
                    "${stream?.codec.orEmpty()} ${stream?.fileCodec.orEmpty()} $qualityText ${stream?.audioMode.orEmpty()} ${stream?.audioModes?.joinToString(" ").orEmpty()}",
                ) || match.isDolbyAtmos
            )
        )
        // Unwanted or undecodable here: the player would skip the song rather than fail loudly.
        if (isDolbyAtmos && !atmos) return null

        val manifestMimeType = manifestMimeType(stream, match.format, url)
        val statedContainer = (stream?.container?.ifBlank { null } ?: stream?.containerFormat?.ifBlank { null })
            ?.trim()?.lowercase()
        val mimeCodec = stream?.mimeType?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.startsWith("audio/") }
            ?.let(::normalizeCodec)
        val rawCodec = statedCodec?.takeIf { it in AUDIO_CODECS }
            ?: statedContainer?.takeIf { it in SELF_DESCRIBING_CONTAINERS }
            ?: mimeCodec?.takeIf { it in AUDIO_CODECS }
            ?: if (isDolbyAtmos) {
                "eac3-joc"
            } else if (LOSSLESS_LABEL.containsMatchIn(qualityText)) {
                "flac"
            } else {
                formatCodec?.takeIf { it in AUDIO_CODECS }
                    ?: statedContainer?.takeIf { it in AUDIO_CODECS }
                    ?: urlExtensionCodec(url)
            }
        val codec = if (isDolbyAtmos && (rawCodec == null || rawCodec in ATMOS_CODECS)) "eac3-joc" else rawCodec

        // Add-ons report sample rate in Hz or kHz (or free-text quality label) and bitrate in bps or kbps.
        val sampleRateHz = stream?.sampleRate?.takeIf { it > 0 }?.let { if (it < 1000) it * 1000 else it }?.toInt()
            ?: KHZ_LABEL.find(qualityText)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 1000).toInt() }
        val bitDepth = stream?.bitDepth?.takeIf { it > 0 }?.toInt()
            ?: BIT_DEPTH_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 8..32 }
        val bitrateKbps = stream?.bitrate?.takeIf { it > 0 }?.let { if (it > 3_000) it / 1000 else it }?.toInt()
            ?: KBPS_LABEL.find(qualityText)?.groupValues?.get(1)?.toIntOrNull()
        val reportedQuality = stream?.quality?.ifBlank { null }
            ?: stream?.streamQuality?.ifBlank { null }
            ?: stream?.audioQuality?.ifBlank { null }
            ?: match.audioQuality?.ifBlank { null }

        val format = listOfNotNull(
            codec?.uppercase(),
            bitDepth?.let { "$it-bit" },
            sampleRateHz?.let { "${(it / 1000.0).toString().removeSuffix(".0")} kHz" },
        ).joinToString(" ")
        return Stream(
            url = url,
            label = listOf(addon.name, format).filter { it.isNotBlank() }.joinToString(" · "),
            source = addon.name,
            codec = codec,
            quality = reportedQuality,
            bitDepth = bitDepth,
            sampleRateHz = sampleRateHz,
            bitrateKbps = bitrateKbps,
            isDolbyAtmos = isDolbyAtmos,
            manifestMimeType = manifestMimeType,
            // Quality and manifest are part of the key: another tier is another file and must not share cached bytes.
            cacheKey = "${addon.url}/${match.id}?${addon.quality}:$reportedQuality:$codec:$sampleRateHz:$bitDepth:$bitrateKbps:$isDolbyAtmos:${manifestMimeType.orEmpty()}",
        )
    }

    private fun isEncrypted(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: return false
        if (primitive is JsonNull) return false
        primitive.booleanOrNull?.let { return it }
        val text = primitive.content.trim()
        return text.isNotEmpty() && !text.equals("false", ignoreCase = true) && !text.equals("none", ignoreCase = true)
    }

    private fun manifestMimeType(stream: StreamResponse?, fallbackFormat: String?, url: String): String? {
        if (stream?.manifest?.equals("none", ignoreCase = true) == true ||
            stream?.mediaType?.equals("none", ignoreCase = true) == true
        ) {
            return null
        }
        val stated = listOf(
            stream?.manifest,
            stream?.mediaType,
            stream?.mimeType?.substringBefore(';')?.trim(),
            stream?.format,
            fallbackFormat,
        ).firstNotNullOfOrNull { raw ->
            when (raw?.trim()?.lowercase()) {
                "dash", "mpd", "application/dash+xml" -> DASH_MIME
                "hls", "m3u8", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> HLS_MIME
                else -> null
            }
        }
        if (stated != null) return stated
        return when (url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "mpd" -> DASH_MIME
            "m3u8" -> HLS_MIME
            else -> null
        }
    }

    private fun normalizeCodec(raw: String?): String? {
        val cleaned = raw?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (cleaned) {
            "dash", "hls", "mpd", "m3u8", "none",
            "application/dash+xml", "application/x-mpegurl", "application/vnd.apple.mpegurl",
            -> null
            "eac3-joc", "eac3_joc", "ec3-joc", "ec3_joc", "ec+3", "audio/eac3-joc" -> "eac3-joc"
            "eac3", "ec-3", "ec3", "audio/eac3" -> "eac3"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/alac" -> "alac"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/raw" -> "pcm"
            "audio/mpeg" -> "mp3"
            "audio/aac", "audio/mp4a-latm" -> "aac"
            "audio/opus" -> "opus"
            "audio/vorbis" -> "vorbis"
            else -> if (cleaned.startsWith("mp4a")) "aac" else cleaned.substringAfter('/')
        }
    }

    private fun urlExtensionCodec(url: String): String? =
        url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_CODECS }

    /**
     * The add-on's settings plus the standard `atmos` hint. `auto` asks for the Atmos mix and falls back to
     * stereo; an add-on that declared its own `atmos` default keeps it, unless Atmos is off, which pins it off.
     */
    internal fun requestParams(addon: MusicAddon, atmos: Boolean): Map<String, String> = when {
        atmos -> mapOf(ATMOS to "auto") + addon.params
        ATMOS in addon.params -> addon.params + (ATMOS to "0")
        else -> addon.params
    }

    internal fun searchQuery(title: String, artists: List<String>): String =
        "${title.replace(FEATURING, "")} ${artists.firstOrNull().orEmpty()}".trim()

    /**
     * The first row that is the same recording: same title, same version markers
     * (live, remix, ...), a shared artist and a matching length.
     */
    internal fun pickMatch(
        tracks: List<Track>,
        title: String,
        artists: List<String>,
        durationSec: Int?,
        requireDuration: Boolean = false,
    ): Track? = pickMatches(tracks, title, artists, durationSec, requireDuration).firstOrNull()

    internal fun pickMatches(
        tracks: List<Track>,
        title: String,
        artists: List<String>,
        durationSec: Int?,
        requireDuration: Boolean = false,
    ): List<Track> {
        val wantedTitle = titleKey(title)
        val wantedArtists = artists.map(::letters).filter { it.isNotEmpty() }
        return tracks
            .filter { track ->
                val drift = if (durationSec != null && track.duration != null) abs(track.duration - durationSec) else null
                val artist = letters(track.artist)
                if (requireDuration && (drift == null || drift > 2.0 || artist.isEmpty() || wantedArtists.isEmpty())) {
                    return@filter false
                }
                val artistMatches = when {
                    // Without a credit to compare, only a confirmed length tells recordings apart.
                    artist.isEmpty() || wantedArtists.isEmpty() -> drift != null
                    else -> wantedArtists.any { it in artist || artist in it }
                }
                track.id.isNotBlank() && titleKey(track.title) == wantedTitle && artistMatches &&
                    (drift == null || drift <= DURATION_TOLERANCE_SEC)
            }.sortedBy { track ->
                if (durationSec != null && track.duration != null) abs(track.duration - durationSec) else Double.MAX_VALUE
            }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKD).replace(COMBINING_MARKS, "").lowercase()

    private fun letters(text: String): String = fold(text).filter(Char::isLetterOrDigit)

    private fun titleKey(raw: String): Pair<String, Set<String>> {
        val text = fold(raw)
        val core = text.replace(FEATURING, "").substringBefore('(').substringBefore('[').substringBefore(" - ")
            .filter(Char::isLetterOrDigit)
            .ifEmpty { text.filter(Char::isLetterOrDigit) }
        return core to text.split(WORD_SPLIT).filter { it in VERSION_WORDS }.toSet()
    }

    private suspend inline fun <reified T> get(
        base: String,
        segments: List<String>,
        params: Map<String, String>,
    ): T {
        val url = base.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val call = baseClient.newBuilder().proxy(YouTube.proxy).build().newCall(request)
        val body = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            it.body?.string().orEmpty()
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }
        return json.decodeFromString(body)
    }
}
