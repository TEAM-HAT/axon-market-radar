package inc.axon.radar

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import inc.axon.radar.data.Radar
import inc.axon.radar.data.Store
import inc.axon.radar.ui.Look
import inc.axon.radar.ui.Link
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Starts the real activity, with and without data, the way the launcher and the widgets do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
    }

    @Test
    fun opensBeforeAnyDataArrives() {
        File(ctx.filesDir, "radar.json").delete()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun opensInBlackAndWhite() {
        File(ctx.filesDir, "radar.json").writeText(File("../../radar.json").readText())
        Store.setLook(ctx, Look.Mono)
        val move = Radar.parse(File("../../radar.json").readText()).moves.first { it.type == "Funding" }
        val intent = Intent(ctx, MainActivity::class.java).putExtra(Link.EXTRA_DEST, "move").putExtra(Link.EXTRA_ID, move.id)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
        Store.setLook(ctx, Look.Colour)
    }

    /** The carousel's grid button opens the app's deck on the same card. */
    @Test
    fun opensTheDeckOnACard() {
        val json = File("../../radar.json").readText()
        File(ctx.filesDir, "radar.json").writeText(json)
        val card = Radar.parse(json).deck[3]
        val intent = Intent(ctx, MainActivity::class.java).putExtra(Link.EXTRA_DEST, "deck").putExtra(Link.EXTRA_ID, card.id)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)
    }

    /** The carousel's list holds one card per move in the deck, and each card opens its own move. */
    @Test
    fun carouselListHasEveryCard() {
        val json = File("../../radar.json").readText()
        File(ctx.filesDir, "radar.json").writeText(json)
        val deck = Radar.parse(json).deck
        val factory = CarouselFactory(ctx, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
        factory.onCreate()
        org.junit.Assert.assertEquals(deck.size, factory.count)
        org.junit.Assert.assertEquals(deck[3].id, CarouselWidget.fillIn(deck[3]).getStringExtra(Link.EXTRA_ID))
        // Building every card must not throw, at the top, middle and end.
        listOf(0, deck.size / 2, deck.lastIndex).forEach { factory.getViewAt(it) }
        // An arrow tap for a widget that is gone does nothing rather than crash.
        CarouselWidget().onReceive(ctx, Intent(ctx, CarouselWidget::class.java).setAction(CarouselWidget.ACTION_SCROLL)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 7).putExtra(CarouselWidget.EXTRA_DELTA, 1))
    }

    @Test
    fun opensAMoveFromAWidget() {
        val json = File("../../radar.json").readText()
        File(ctx.filesDir, "radar.json").writeText(json)
        val move = Radar.parse(json).deck.first()
        val intent = Intent(ctx, MainActivity::class.java).putExtra(Link.EXTRA_DEST, "move").putExtra(Link.EXTRA_ID, move.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        controller.newIntent(Intent(ctx, MainActivity::class.java).putExtra(Link.EXTRA_DEST, "companies"))
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
}
