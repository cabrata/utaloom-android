package com.metrolist.innertube

import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.pages.ChartsPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartsPageTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val sectionList = """
        {"contents": [
          {"musicShelfRenderer": {"shelfDivider": {}}},
          {"musicCarouselShelfRenderer": {
            "header": {"musicCarouselShelfBasicHeaderRenderer": {"title": {"runs": [{"text": "Video charts"}]}}},
            "itemSize": "COLLECTION_STYLE_ITEM_SIZE_MEDIUM",
            "contents": [{"musicTwoRowItemRenderer": {
              "title": {"runs": [{"text": "Trending 20 Indonesia"}]},
              "subtitle": {"runs": [{"text": "Chart"}, {"text": " • "}, {"text": "YouTube Charts"}]},
              "thumbnailRenderer": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://yt3.googleusercontent.com/86xv8o0Eb2vyMJU0WkYWmVT1_CEbRrN1h3YZbFxAN8COUa3DIhdwEJIA0TBiqcz9OgsO-IyI0W4=s576"}]}}},
              "navigationEndpoint": {"browseEndpoint": {
                "browseId": "VLOLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PLAYLIST"}}
              }},
              "thumbnailOverlay": {"musicItemThumbnailOverlayRenderer": {"content": {"musicPlayButtonRenderer": {"playNavigationEndpoint": {"watchPlaylistEndpoint": {"playlistId": "OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo", "params": "wAEB"}}}}}}
            }}]
          }},
          {"musicCarouselShelfRenderer": {
            "header": {"musicCarouselShelfBasicHeaderRenderer": {"title": {"runs": [{"text": "Top artists"}]}}},
            "itemSize": "COLLECTION_STYLE_ITEM_SIZE_MEDIUM",
            "numItemsPerColumn": "4",
            "contents": [{"musicResponsiveListItemRenderer": {
              "flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Denny Caknan"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "7.13M subscribers"}]}}}
              ],
              "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://lh3.googleusercontent.com/woWOuYcLDhSRRyFn6kNHJtaiGpD3dW2SnYZanXVdqe96T9Qs1Bt1GchfV73HsIJq2R013krAX3DyLA=w120-h120-p-l90-rj"}]}}},
              "navigationEndpoint": {"browseEndpoint": {
                "browseId": "UCrXRY_7SVVmc6TykwhRGUNQ",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}}
              }}
            }}]
          }}
        ], "continuations": [{"nextContinuationData": {"continuation": "next-charts"}}]}
    """.trimIndent()

    @Test
    fun publicChartsRetainsPlaylistTilesAndArtistRowsAcrossSupportedRoots() {
        val tab = """{"tabRenderer": {"content": {"sectionListRenderer": $sectionList}}}"""
        val roots = listOf(
            """"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {}}, $tab]}""",
            """"twoColumnBrowseResultsRenderer": {"tabs": [null, $tab]}""",
            """"twoColumnBrowseResultsRenderer": {"secondaryContents": {"sectionListRenderer": $sectionList}}""",
            """"sectionListRenderer": $sectionList""",
        )
        roots.forEach { root ->
            val page = parse(""""contents": {$root}""")
            assertEquals(listOf("Video charts", "Top artists"), page.sections.map { it.title })
            assertEquals("next-charts", page.continuation)
            assertEquals(ChartsPage.ChartType.TOP, page.sections[1].chartType)
            val playlist = page.sections[0].items.single() as PlaylistItem
            assertEquals("Trending 20 Indonesia", playlist.title)
            assertEquals("OLAK5uy_n7Ig_LAUbKE6_ZeQ1pwHmJcEhwX7BekBo", playlist.id)
            assertEquals(playlist.id, playlist.playEndpoint?.playlistId)
            assertTrue(playlist.thumbnail!!.endsWith("=s576"))
            val artist = page.sections[1].items.single() as ArtistItem
            assertEquals("Denny Caknan", artist.title)
            assertEquals("UCrXRY_7SVVmc6TykwhRGUNQ", artist.id)
            assertTrue(artist.thumbnail!!.startsWith("https://lh3.googleusercontent.com/"))
        }
    }

    @Test
    fun sectionContinuationsRetainItemsAndTokensButTerminalPagesRemainEmpty() {
        val page = parse(""""continuationContents": {"sectionListContinuation": $sectionList}""")
        assertEquals(listOf("Video charts", "Top artists"), page.sections.map { it.title })
        assertEquals("next-charts", page.continuation)
        val terminal = parse(""""continuationContents": {"sectionListContinuation": {"contents": []}}""")
        assertTrue(terminal.sections.isEmpty())
        assertNull(terminal.continuation)
    }

    @Test
    fun twoColumnSongsInMusicShelvesAreNotDiscarded() {
        val page = parse("""
            "contents": {"sectionListRenderer": {"contents": [{"musicShelfRenderer": {
              "title": {"runs": [{"text": "Top songs"}]},
              "contents": [{"musicResponsiveListItemRenderer": {
                "playlistItemData": {"videoId": "chart-song"},
                "flexColumns": [
                  {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Chart song"}]}}},
                  {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Performer", "navigationEndpoint": {"browseEndpoint": {"browseId": "UCperformer"}}}]}}}
                ],
                "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/chart-song/hqdefault.jpg"}]}}}
              }}]
            }}]}}
        """.trimIndent())
        val song = page.sections.single().items.single() as SongItem
        assertEquals("chart-song", song.id)
        assertEquals("Chart song", song.title)
        assertEquals("Performer", song.artists.single().name)
        assertEquals("UCperformer", song.artists.single().id)
    }

    @Test
    fun missingOrUnsupportedInitialResponsesFailExplicitly() {
        val missing = runCatching { parse("\"contents\": {}") }.exceptionOrNull()
        assertTrue(missing is IllegalStateException)
        assertEquals("Missing charts section list", missing?.message)
        val unsupported = runCatching {
            parse(""""contents": {"sectionListRenderer": {"contents": [{"musicShelfRenderer": {}}]}}""")
        }.exceptionOrNull()
        assertTrue(unsupported is IllegalStateException)
        assertEquals("No supported chart sections", unsupported?.message)
    }

    private fun parse(fields: String): ChartsPage = YouTube.parseChartsPage(
        json.decodeFromString<BrowseResponse>("""{"responseContext": {}, $fields}"""),
    )
}
