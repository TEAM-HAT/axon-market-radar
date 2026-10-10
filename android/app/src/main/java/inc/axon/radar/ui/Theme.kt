package inc.axon.radar.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import inc.axon.radar.R
import kotlin.math.ceil
import kotlin.math.floor

/** The six flat page colours, taken from the inspiration, and the neutrals around them. */
object Palette {
    val Slate = Color(0xFF7E888B)
    val Crimson = Color(0xFF8B1112)
    val Royal = Color(0xFF263B94)
    val Yellow = Color(0xFFFCED00)
    val Sage = Color(0xFF618C75)
    val Violet = Color(0xFF975ACA)

    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Brand = Color(0xFF061AD3)      // the Radar logo's blue: the loading screen
    val Paper = Color(0xFFD6D6D6)      // directory screens
    val Row = Color(0xFFDEDEDE)        // list rows on paper
    val Rule = Color(0xFFC9C9C9)
    val Muted = Color(0xFF9A9A9A)      // counts and secondary text on paper
    val Faint = Color(0xFFB9B9B9)      // inactive titles and filters
    val Ink2 = Color(0xFF3C3C3C)
    val Bar = Color(0xFF5E5E5E)        // tab bar on paper
    val Night = Color(0xFF121212)      // tab bar on the deck
}

/**
 * Black and white: one tone per family of move, the way the glyphs already group them. Rules and
 * licences are white paper, capital is black, commercial moves are silver.
 */
object Tones {
    val Paper = Color(0xFFF2F2F2)
    val Silver = Color(0xFFB4B4B4)
    val Black = Color(0xFF000000)

    /** One step from each tone, for the second tile, the faces and a swatch on its own tone. */
    val PaperStep = Color(0xFFDADADA)
    val SilverStep = Color(0xFF9A9A9A)
    val BlackStep = Color(0xFF1F1F1F)

    /** Hairline round black cards on the black deck. */
    val Edge = Color(0xFF3A3A3A)
}

enum class Look { Colour, Mono }

/** Kind of move → page colour, in colour. */
fun typeColor(type: String?): Color = when (type) {
    "License" -> Palette.Yellow
    "Regulation" -> Palette.Royal
    "Funding" -> Palette.Crimson
    "M&A" -> Palette.Violet
    "Launch" -> Palette.Sage
    else -> Palette.Slate // Partnership and anything unknown
}

/** The darker tile on each colour page: the page colour with 17% black over it. */
fun shade(c: Color, k: Float = 0.83f): Color = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

/** The second kind in each family, drawn with fine diagonal lines in black and white. */
private val LINED = setOf("Regulation", "M&A", "Partnership")

/**
 * How the app is painted. In colour, every kind of move has its page colour from the inspiration,
 * display type stays black and reading text turns white on the two dark pages. In black and white,
 * each family has a tone, the second kind of each family is marked with fine lines, and everything
 * on the black pages is inverted: white type, and white blocks where the colour pages have black.
 */
@Immutable
class Ink private constructor(val look: Look) {
    val mono: Boolean get() = look == Look.Mono

    /** The page colour of a kind of move. */
    fun page(type: String?): Color = if (!mono) typeColor(type) else when (glyphOf(type)) {
        Shape.Diamond -> Tones.Paper
        Shape.Square -> Tones.Black
        Shape.Dot -> Tones.Silver
    }

    /** True for the kinds that carry lines: regulation, M&A and partnerships, in black and white only. */
    fun lined(type: String?): Boolean = mono && type in LINED

    /** A black page in black and white. Colour pages are never treated as dark here. */
    fun dark(bg: Color): Boolean = mono && bg.luminance() < 0.2f

    /** Huge names and headings: black on every colour page, as in the inspiration; white on black. */
    fun display(bg: Color): Color = if (dark(bg)) Palette.White else Palette.Black

    /** Reading text: white on the two dark colour pages and on black; black elsewhere. */
    fun reading(bg: Color): Color = when {
        !mono -> if (bg == Palette.Crimson || bg == Palette.Royal) Palette.White else Palette.Black
        dark(bg) -> Palette.White
        else -> Palette.Black
    }

    fun secondary(bg: Color): Color = reading(bg).copy(alpha = if (!mono && bg == Palette.Yellow) 0.55f else 0.62f)

    /** The block that carries a big figure: black, or paper on a black page. */
    fun panel(bg: Color): Color = if (dark(bg)) Tones.Paper else Palette.Black
    fun onPanel(bg: Color): Color = if (dark(bg)) Palette.Black else Palette.White

    /** The first tile and quieter buttons: the page colour one step darker, or lighter on black. */
    fun tile(bg: Color): Color = when {
        !mono -> shade(bg)
        dark(bg) -> Tones.BlackStep
        bg == Tones.Silver -> Tones.SilverStep
        else -> Tones.PaperStep
    }
    fun onTile(bg: Color): Color = if (!mono) reading(bg) else reading(tile(bg))

    /** The initials beside a page's name. */
    fun chip(bg: Color): Color = when {
        !mono -> shade(bg, 0.7f)
        dark(bg) -> Color(0xFF2E2E2E)
        bg == Tones.Silver -> Color(0xFF8C8C8C)
        else -> Color(0xFFCCCCCC)
    }

    /** A move's square on a page: its own colour, or a step away when it matches the page. */
    fun swatch(type: String?, bg: Color): Color {
        val c = page(type)
        return when {
            c != bg -> c
            !mono -> shade(bg, 0.7f)
            else -> tile(bg)
        }
    }

    /** Hairline edges and frames: black in colour, the reading colour in black and white. */
    fun edge(bg: Color, alpha: Float): Color = (if (mono) reading(bg) else Palette.Black).copy(alpha = alpha)

    /** Lines over a swatch: dark on light tones, light on black. */
    fun lines(fill: Color): Color = if (fill.luminance() < 0.2f) Color(0x99FFFFFF) else Color(0x6B000000)

    /** The faint lines over a whole figure block, so the second kind reads even at a glance. */
    fun panelLines(bg: Color): Color = if (dark(bg)) Color(0x1F000000) else Color(0x2EFFFFFF)

    // The directory screens keep the inspiration's light grey in both looks; the tab bar turns black.
    val bar: Color get() = if (mono) Palette.Black else Palette.Bar
    val night: Color get() = if (mono) Palette.Black else Palette.Night

    companion object {
        val Colour = Ink(Look.Colour)
        val Mono = Ink(Look.Mono)
        fun of(look: Look): Ink = if (look == Look.Mono) Mono else Colour
    }
}

/** The look in use, provided once at the top of the app. */
val LocalInk = staticCompositionLocalOf { Ink.Colour }

/**
 * Fine diagonal lines over [left, top, right, bottom], on a grid fixed to the canvas so that
 * neighbouring blocks continue the same lines.
 */
fun DrawScope.hatch(
    color: Color, gap: Float, width: Float,
    left: Float = 0f, top: Float = 0f, right: Float = size.width, bottom: Float = size.height,
) {
    if (right <= left || bottom <= top) return
    clipRect(left, top, right, bottom) {
        // Lines x + y = k * gap, drawn from the bottom edge up to the top edge.
        val first = floor((left + top) / gap).toInt()
        val last = ceil((right + bottom) / gap).toInt()
        for (k in first..last) {
            val c = k * gap
            drawLine(color, Offset(c - bottom, bottom), Offset(c - top, top), strokeWidth = width)
        }
    }
}

enum class Shape { Diamond, Square, Dot }

fun glyphOf(type: String?): Shape = when (type) {
    "Funding", "M&A" -> Shape.Square
    "Launch", "Partnership" -> Shape.Dot
    else -> Shape.Diamond
}

val Grotesk = FontFamily(
    Font(R.font.intertight_regular, FontWeight.Normal),
    Font(R.font.intertight_medium, FontWeight.Medium),
    Font(R.font.intertight_bold, FontWeight.Bold),
    Font(R.font.intertight_extrabold, FontWeight.ExtraBold),
)

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object Type {
    fun display(size: Float) = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.ExtraBold, fontSize = size.sp,
        lineHeight = (size * 0.94f).sp, letterSpacing = (-0.035).em, lineHeightStyle = tight,
    )
    val Lead = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em)
    val Body = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp)
    val Tile = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 17.sp, letterSpacing = (-0.01).em)
    val Name = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em)
    val Row = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp)
    val Small = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp)
    val Caps = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
    val Tab = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.01).em)
    val Sup = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 10.sp)
}
