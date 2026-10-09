package inc.axon.radar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import inc.axon.radar.R

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
    val Paper = Color(0xFFD6D6D6)      // directory screens
    val Row = Color(0xFFDEDEDE)        // list rows on paper
    val Rule = Color(0xFFC9C9C9)
    val Muted = Color(0xFF9A9A9A)      // counts and secondary text on paper
    val Faint = Color(0xFFB9B9B9)      // inactive titles and filters
    val Ink2 = Color(0xFF3C3C3C)
    val Bar = Color(0xFF5E5E5E)        // tab bar on paper
    val Night = Color(0xFF121212)      // tab bar on the deck
}

/** Kind of move → page colour. */
fun typeColor(type: String?): Color = when (type) {
    "License" -> Palette.Yellow
    "Regulation" -> Palette.Royal
    "Funding" -> Palette.Crimson
    "M&A" -> Palette.Violet
    "Launch" -> Palette.Sage
    else -> Palette.Slate // Partnership and anything unknown
}

/** Display type stays black on every page, as in the inspiration; reading text turns white on the two dark pages. */
fun readingColor(bg: Color): Color = if (bg == Palette.Crimson || bg == Palette.Royal) Palette.White else Palette.Black

fun secondaryColor(bg: Color): Color = readingColor(bg).copy(alpha = if (bg == Palette.Yellow) 0.55f else 0.62f)

/** The darker tile on each page: the page colour with 17% black over it. */
fun shade(c: Color, k: Float = 0.83f): Color = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

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
