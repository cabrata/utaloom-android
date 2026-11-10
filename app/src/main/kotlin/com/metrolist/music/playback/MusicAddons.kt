/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit
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
    )

    @Serializable
    internal data class Track(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val duration: Double? = null,
        val streamURL: String? = null,
    )

    @Serializable
    private data class SearchResponse(val tracks: List<Track> = emptyList())

    @Serializable
    private data class StreamResponse(
        val url: String? = null,
        val codec: String? = null,
        val manifest: String? = null,
        val encrypted: Boolean = false,
        val sampleRate: Double? = null,
        val bitDepth: Double? = null,
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

    private fun fromManifest(base: String, previous: MusicAddon?): MusicAddon {
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

    /** Stream for this track from the first enabled add-on that has it, in list order. */
    suspend fun resolve(
        addons: List<MusicAddon>,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): Stream? = withContext(Dispatchers.IO) {
        addons.filter { it.enabled }.firstNotNullOfOrNull { addon ->
            try {
                resolveFrom(addon, title, artists, durationSec)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("MusicAddons").w("%s failed: %s", addon.name, e.message)
                null
            }
        }
    }

    private fun resolveFrom(
        addon: MusicAddon,
        title: String,
        artists: List<String>,
        durationSec: Int?,
    ): Stream? {
        val tracks = get<SearchResponse>(addon.url, listOf("search"), addon.params + ("q" to searchQuery(title, artists))).tracks
        val match = pickMatch(tracks, title, artists, durationSec) ?: return null
        val stream = runCatching { get<StreamResponse>(addon.url, listOf("stream", match.id), addon.params) }.getOrNull()
        if (stream?.encrypted == true || stream?.manifest?.lowercase() !in setOf(null, "", "none")) return null
        // HTTPS only: the player's data source would also open file:// and content:// URIs.
        val url = (stream?.url ?: match.streamURL)?.takeIf { it.toHttpUrlOrNull()?.isHttps == true } ?: return null

        val format = listOfNotNull(
            stream?.codec?.uppercase(),
            stream?.bitDepth?.let { "${it.toInt()}-bit" },
            stream?.sampleRate?.let { "${(if (it < 1000) it else it / 1000).toString().removeSuffix(".0")} kHz" },
        ).joinToString(" ")
        return Stream(
            url = url,
            label = listOf(addon.name, format).filter { it.isNotBlank() }.joinToString(" · "),
            // Quality is part of the key: another tier is another file and must not share cached bytes.
            cacheKey = "${addon.url}/${match.id}?${addon.quality}",
        )
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
    ): Track? {
        val wantedTitle = titleKey(title)
        val wantedArtists = artists.map(::letters).filter { it.isNotEmpty() }
        return tracks
            .filter { track ->
                val drift = if (durationSec != null && track.duration != null) abs(track.duration - durationSec) else null
                val artist = letters(track.artist)
                val artistMatches = when {
                    // Without a credit to compare, only a confirmed length tells recordings apart.
                    artist.isEmpty() || wantedArtists.isEmpty() -> drift != null
                    else -> wantedArtists.any { it in artist || artist in it }
                }
                track.id.isNotBlank() && titleKey(track.title) == wantedTitle && artistMatches &&
                    (drift == null || drift <= DURATION_TOLERANCE_SEC)
            }.minByOrNull { track ->
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

    private inline fun <reified T> get(
        base: String,
        segments: List<String>,
        params: Map<String, String>,
    ): T {
        val url = base.toHttpUrl().newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        baseClient.newBuilder().proxy(YouTube.proxy).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return json.decodeFromString(response.body?.string().orEmpty())
        }
    }
}
