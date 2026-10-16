package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.Layout
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.R
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeToImageTest {
    @Test
    fun keepsBrandThreadsOutsideContentForEveryBackground() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("woven with Utaloom", context.getString(R.string.utaloom_lyrics_signature))

        for (style in LyricsBackgroundStyle.entries) {
            for (background in listOf(Color.BLACK, Color.WHITE)) {
                val image = ComposeToImage.createLyricsImage(
                    context = context,
                    coverArtUrl = null,
                    songTitle = "A long song title that should stay within the header",
                    artistName = "Utaloom",
                    lyrics = "Benang nada\nMenemani harimu\nنسج الألحان\n音楽を紡ぐ\nSing along",
                    width = 340,
                    height = 340,
                    backgroundColor = background,
                    backgroundStyle = style,
                    textColor = if (background == Color.BLACK) Color.WHITE else Color.BLACK,
                    secondaryTextColor = if (background == Color.BLACK) Color.LTGRAY else Color.DKGRAY,
                    lyricsAlignment = Layout.Alignment.ALIGN_CENTER,
                )
                try {
                    assertEquals(2160, image.width)
                    assertEquals(2160, image.height)
                    val scale = image.width / 340f
                    assertEquals(Color.rgb(244, 119, 87), image.getPixel((12 * scale).toInt(), image.height / 2))
                    assertEquals(Color.rgb(255, 198, 163), image.getPixel((22 * scale).toInt(), image.height / 2))
                    assertEquals(background, image.getPixel(image.width / 2, (18 * scale).toInt()))
                    for ((x, y) in listOf(0 to 0, 2159 to 0, 0 to 2159, 2159 to 2159)) {
                        assertEquals(0, Color.alpha(image.getPixel(x, y)))
                    }
                    assertEquals(255, Color.alpha(image.getPixel(image.width / 2, image.height / 2)))
                    for (y in 104 until 278) {
                        for (x in 28 until 312) {
                            val pixel = image.getPixel((x * scale).toInt(), (y * scale).toInt())
                            assertTrue("Thread overlaps lyrics at ($x, $y)", pixel != Color.rgb(244, 119, 87) && pixel != Color.rgb(255, 198, 163))
                        }
                    }
                    System.getenv("UTALOOM_LYRICS_PREVIEW_DIR")?.let { directory ->
                        val file = File(directory, "lyrics-${style.name.lowercase()}-${if (background == Color.BLACK) "dark" else "light"}.png")
                        file.parentFile?.mkdirs()
                        file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    }
                } finally {
                    image.recycle()
                }
            }
        }
    }

    @Test
    fun sharesBrandedPngWithArtworkThroughBothStoragePaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coverFile = File(context.cacheDir, "lyrics-test-cover.png")
        val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.rgb(40, 82, 120))
        coverFile.outputStream().use { assertTrue(cover.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cover.recycle()

        val alignments = listOf(Layout.Alignment.ALIGN_NORMAL, Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_OPPOSITE)
        for ((style, alignment) in LyricsBackgroundStyle.entries.zip(alignments)) {
            val image = ComposeToImage.createLyricsImage(
                context = context,
                coverArtUrl = coverFile.toURI().toString(),
                songTitle = "Benang Nada",
                artistName = "Utaloom",
                lyrics = "Musik yang menemani harimu\nنسج الألحان\n音楽を紡ぐ",
                width = 340,
                height = 340,
                backgroundStyle = style,
                lyricsAlignment = alignment,
            )
            try {
                val scale = image.width / 340f
                assertEquals(Color.rgb(40, 82, 120), image.getPixel((60 * scale).toInt(), (60 * scale).toInt()))
                val uri = ComposeToImage.saveBitmapAsFile(context, image, "lyrics-test-${style.name}")
                context.contentResolver.openInputStream(uri).use { stream ->
                    assertNotNull(stream)
                    val decoded = BitmapFactory.decodeStream(stream)
                    assertNotNull(decoded)
                    assertEquals(image.width, decoded.width)
                    assertEquals(image.height, decoded.height)
                    assertEquals(image.getPixel((12 * scale).toInt(), image.height / 2), decoded.getPixel((12 * scale).toInt(), image.height / 2))
                    decoded.recycle()
                }
            } finally {
                image.recycle()
            }
        }
    }
}
