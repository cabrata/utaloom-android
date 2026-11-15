package com.metrolist.music.ui.screens.wrapped

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WrappedSharingTest {
    @Test
    fun sharesDistinctReadableCachePngsWithGrantsWithoutRecyclingInput() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(255, 137, 87))
        val sharedUris = mutableListOf<Uri>()

        try {
            repeat(2) {
                withTimeout(10_000) {
                    val sharing = async(Dispatchers.Default) { shareWrapped(context, bitmap, 2026) }
                    while (!sharing.isCompleted) {
                        shadowOf(Looper.getMainLooper()).idle()
                        delay(10)
                    }
                    sharing.await()
                }

                val chooser = requireNotNull(shadowOf(context).nextStartedActivity)
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
                val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("image/png", send.type)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val uri = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
                assertEquals("content", uri.scheme)
                assertEquals("${context.packageName}.FileProvider", uri.authority)
                assertTrue(requireNotNull(uri.path).contains("/images/utaloom-wrapped-2026-"))
                assertEquals(uri, requireNotNull(send.clipData).getItemAt(0).uri)

                context.contentResolver.openInputStream(uri).use { stream ->
                    val decoded = requireNotNull(BitmapFactory.decodeStream(stream))
                    try {
                        assertEquals(bitmap.width, decoded.width)
                        assertEquals(bitmap.height, decoded.height)
                        assertEquals(bitmap.getPixel(0, 0), decoded.getPixel(0, 0))
                    } finally {
                        decoded.recycle()
                    }
                }
                assertFalse(bitmap.isRecycled)
                sharedUris += uri
            }
            assertNotEquals(sharedUris[0], sharedUris[1])
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun cacheWriteFailureEscapesWithoutLaunchingChooser() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val blockedCache = File.createTempFile("blocked-wrapped-cache-", ".tmp", application.cacheDir)
        val context = object : ContextWrapper(application) {
            override fun getCacheDir(): File = blockedCache
        }
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            val failure = runCatching { shareWrapped(context, bitmap, 2026) }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertFalse(bitmap.isRecycled)
            assertEquals(null, shadowOf(application).nextStartedActivity)
        } finally {
            bitmap.recycle()
            blockedCache.delete()
        }
    }
}
