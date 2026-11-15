package com.metrolist.music.ui.screens.wrapped

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Event
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

internal class WrappedUiApplication : Application(), GeneratedComponentManager<WrappedEntryPoint> {
    val room by lazy { Room.inMemoryDatabaseBuilder(this, InternalDatabase::class.java).allowMainThreadQueries().build() }
    val database by lazy { MusicDatabase(room) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Hilt's EntryPoints only accepts components marked as generated.
    private val entryPoint = object : WrappedEntryPoint, GeneratedComponent {
        override fun databaseDao(): DatabaseDao = room.dao
        override fun database(): MusicDatabase = database
        override fun applicationScope(): CoroutineScope = scope
    }
    override fun generatedComponent(): WrappedEntryPoint = entryPoint
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = WrappedUiApplication::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WrappedScreenTest {
    private lateinit var app: WrappedUiApplication
    private lateinit var activity: ComponentActivity
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var nav: NavHostController
    private val year get() = WrappedConstants.YEAR

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = controller.get()
        // Stories auto-advance on the frame clock; TalkBack mode stops that so steps stay deterministic.
        shadowOf(activity.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        app.scope.cancel()
        app.room.close()
    }

    // One test on purpose: Compose's AndroidUiDispatcher keeps the first test's Choreographer,
    // which Robolectric resets, so a second Compose test in this JVM never gets frames.
    @Test
    fun emptyHistoryThenStoriesYearSelectionAndPosterRenderThroughRealScreen() {
        mount()
        awaitText(app.getString(R.string.wrapped_recap_empty_title))
        assertTrue(labels().any { it.contains(year.toString()) })
        click(app.getString(R.string.wrapped_close))
        awaitText("Home")
        assertEquals(null, nav.currentBackStackEntry?.savedStateHandle?.get<Int>("wrapped_seen_year"))

        runBlocking {
            val artist = ArtistEntity("test-artist", "Test artist")
            app.room.dao.insert(artist)
            for (selectedYear in listOf(year - 1, year)) {
                for (index in 1..5) {
                    val song = SongEntity("$selectedYear-$index", "Song $selectedYear $index")
                    app.room.dao.insert(song)
                    app.room.dao.insert(SongArtistMap(song.id, artist.id, 0))
                    app.room.dao.insert(Event(songId = song.id, timestamp = LocalDateTime.of(selectedYear, 1, 1, 0, index), playTime = (6 - index) * 60_000L))
                }
            }
        }
        nav.navigate("wrapped")
        awaitText(app.getString(R.string.wrapped_recap_start))
        click(app.getString(R.string.wrapped_recap_start))
        awaitText(app.getString(R.string.wrapped_recap_time_title))
        click(app.getString(R.string.wrapped_recap_next))
        awaitText(app.getString(R.string.wrapped_recap_top_song))
        assertTrue(labels().any { it.contains("Song $year 1") })
        click(app.getString(R.string.wrapped_recap_next))
        awaitText(app.getString(R.string.wrapped_recap_top_songs))
        click(app.getString(R.string.wrapped_recap_next))
        awaitText(app.getString(R.string.wrapped_recap_top_artist))
        click(app.getString(R.string.wrapped_recap_next))
        awaitText(app.getString(R.string.wrapped_recap_summary))
        assertFalse(nodes().mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(results)
            results.firstOrNull()
        }.any { it.multiParagraph.didExceedMaxLines })
        saveFrame("wrapped-summary.png")
        click(app.getString(R.string.wrapped_recap_select_year))
        awaitText((year - 1).toString())
        click((year - 1).toString())
        awaitText(app.getString(R.string.wrapped_recap_start))
        assertTrue(labels().any { it.contains((year - 1).toString()) })
        click(app.getString(R.string.wrapped_close))
        awaitText("Home")
        assertEquals(year - 1, nav.currentBackStackEntry?.savedStateHandle?.get<Int>("wrapped_seen_year"))
    }

    private fun mount() {
        activity.setContent {
            MaterialTheme {
                nav = rememberNavController()
                CompositionLocalProvider(LocalNavController provides nav) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") { Text("Home") }
                        composable("wrapped") { WrappedScreen(playMusic = false) }
                        composable("local_playlist/{id}") { Text("Playlist") }
                    }
                    LaunchedEffect(Unit) { nav.navigate("wrapped") }
                }
            }
        }
    }

    private fun frame() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 360, 800)
        windowRoots().forEach { it.measureAndLayoutForTest() }
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    private fun awaitText(text: String) {
        val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
        do {
            frame()
            if (labels().any { it.contains(text) }) return
            Thread.yield()
        } while (System.nanoTime() < deadline)
        error("Missing '$text'. Visible semantics: ${labels()}")
    }

    private fun click(text: String) {
        val node = nodes().firstOrNull { label(it).contains(text) && it.config.contains(SemanticsActions.OnClick) }
        val action = node?.config?.get(SemanticsActions.OnClick)?.action
            ?: nodes().flatMap { it.config.getOrElseNullable(SemanticsActions.CustomActions) { null }.orEmpty() }
                .firstOrNull { it.label == text }?.action
            ?: error("No clickable '$text'. Visible semantics: ${labels()}")
        assertTrue(action.invoke())
    }

    private fun nodes(): List<SemanticsNode> = windowRoots().flatMap { flatten(it.semanticsOwner.rootSemanticsNode) }

    // Popups such as DropdownMenu live in their own window, outside the activity decor view.
    @Suppress("UNCHECKED_CAST")
    private fun windowRoots(): List<ViewRootForTest> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>
        return views.toList().flatMap(::roots)
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun label(node: SemanticsNode): String =
        node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().joinToString { it.text } +
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().joinToString()
    private fun labels(): List<String> = nodes().map(::label).filter { it.isNotBlank() }
    private fun roots(view: View): List<ViewRootForTest> =
        listOfNotNull(view as? ViewRootForTest) + if (view is ViewGroup) (0 until view.childCount).flatMap { roots(view.getChildAt(it)) } else emptyList()

    private fun saveFrame(name: String) {
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        activity.window.decorView.draw(Canvas(bitmap))
        val directory = File("build/outputs/wrapped-ui").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
