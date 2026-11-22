package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, manifest = Config.NONE)
class ArtworkUrlMapperTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val mapper = ArtworkUrlMapper()
    private val artwork = "https://lh3.googleusercontent.com/cover=w1080-h1080-l90-rj?token=a=b"

    @Test
    fun `list rows request small artwork while card slots retain sharp artwork`() {
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w160-h160-p-l90-rj?token=a=b",
            mapper.map(artwork, Options(context, size = Size(132, 132))),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w480-h480-p-l90-rj?token=a=b",
            mapper.map(artwork, Options(context, size = Size(418, 418))),
        )
    }

    @Test
    fun `nearby slot sizes share a CDN cache entry`() {
        assertEquals(
            mapper.map(artwork, Options(context, size = Size(132, 132))),
            mapper.map(artwork, Options(context, size = Size(150, 150))),
        )
    }

    @Test
    fun `crop follows rectangular slot and fit preserves source aspect ratio`() {
        val banner = "https://yt3.googleusercontent.com/banner=w1080-h540-l90-rj"
        assertEquals(
            "https://yt3.googleusercontent.com/banner=w480-h240-p-l90-rj",
            mapper.map(banner, Options(context, size = Size(400, 200), scale = Scale.FILL)),
        )
        assertEquals(
            "https://yt3.googleusercontent.com/banner=w480-h240-p-l90-rj",
            mapper.map(banner, Options(context, size = Size(400, 400), scale = Scale.FIT)),
        )
    }

    @Test
    fun `original size and local or signed third party URLs are untouched`() {
        assertEquals(artwork, mapper.map(artwork, Options(context, size = Size.ORIGINAL)))
        listOf(
            "content://media/external/audio/albumart/1",
            "file:///storage/Music/cover.jpg",
            "https://cdn.example.com/cover=w1080-h1080?signature=s512",
            "https://lh3.googleusercontent.com.evil.test/cover=w1080-h1080",
        ).forEach { url ->
            assertEquals(url, mapper.map(url, Options(context, size = Size(132, 132))))
        }
    }

    @Test
    fun `video keeps supplied rendition without optional resolution 404s`() {
        val url = "https://i.ytimg.com/vi/video/hqdefault.jpg?sqp=signature&rs=token"
        assertEquals(url, mapper.map(url, Options(context, size = Size(720, 720))))
    }

    @Test
    fun `height only resolves aspect ratio and large artwork is not capped to thumbnail size`() {
        assertEquals(
            "https://lh3.googleusercontent.com/cover=w256-h256-p-l90-rj?token=a=b",
            mapper.map(artwork, Options(context, size = Size(Dimension.Undefined, Dimension.Pixels(200)))),
        )
        assertEquals(3000, artworkSize(3000))
    }

    @Test
    fun `real Coil request maps resolved slot size before fetching`() = runBlocking {
        var fetchedUrl: String? = null
        val fetcher = object : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
                fetchedUrl = data.toString()
                return object : Fetcher {
                    override suspend fun fetch() = ImageFetchResult(
                        image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).asImage(),
                        isSampled = false,
                        dataSource = DataSource.NETWORK,
                    )
                }
            }
        }
        val loader = ImageLoader.Builder(context)
            .components {
                add(mapper)
                add(fetcher)
            }
            .build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data(artwork).size(132, 132).build())
            assertTrue(result is SuccessResult)
            assertEquals(
                "https://lh3.googleusercontent.com/cover=w160-h160-p-l90-rj?token=a=b",
                fetchedUrl,
            )
        } finally {
            loader.shutdown()
        }
    }
}
