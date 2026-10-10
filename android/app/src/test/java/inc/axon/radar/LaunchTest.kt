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

    /** A link to the app's deck on a given card. */
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

    /**
     * The carousel's stack holds one card per move, newest first; a tap on a card opens that move in the app,
     * and a new deck in the morning starts the stack again from the newest card.
     */
    @Test
    fun carouselStack() {
        val json = File("../../radar.json").readText()
        File(ctx.filesDir, "radar.json").writeText(json)
        val deck = Radar.parse(json).deck
        val stack = CarouselFactory(ctx, 7).apply { onCreate() }
        org.junit.Assert.assertEquals(deck.size, stack.count)
        listOf(0, 4, stack.count - 1).forEach { stack.getViewAt(it) }
        val tap = CarouselWidget.openTemplate(ctx).let { Intent(ctx, MainActivity::class.java) }
        tap.fillIn(CarouselWidget.fillIn("move", deck[3].id), 0)
        org.junit.Assert.assertEquals(Link("move", deck[3].id), Link.from(tap))
        org.junit.Assert.assertTrue(CarouselWidget.newDeck(ctx, 7, deck.first().id))
        org.junit.Assert.assertFalse(CarouselWidget.newDeck(ctx, 7, deck.first().id))
        org.junit.Assert.assertTrue(CarouselWidget.newDeck(ctx, 7, "new-move"))
        org.junit.Assert.assertTrue(CarouselWidget.newDeck(ctx, 8, deck.first().id))
        // The arrows' broadcast moves the stack; on a widget that is gone it does nothing.
        CarouselWidget().onReceive(ctx, Intent(ctx, CarouselWidget::class.java).setAction(CarouselWidget.ACTION_STEP)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 7).putExtra(CarouselWidget.EXTRA_DIR, 1))
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
