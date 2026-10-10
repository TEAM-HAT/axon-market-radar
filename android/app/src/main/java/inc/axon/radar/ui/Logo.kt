package inc.axon.radar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import inc.axon.radar.R

/*
 * The Radar logo: the mark (an R inside radar rings) and the lockup with the "Radar" wordmark.
 * Both are vector drawables made by brand/build_logo.py, drawn in white and tinted here.
 */

/** The mark's box is 2R wide and (1 + √3/2)R tall; the lockup runs on to the end of the wordmark. */
private const val MARK_ASPECT = 2f / 1.8660254f
private const val LOCKUP_ASPECT = 7.1995f / 1.8660254f

@Composable
fun RadarMark(color: Color, height: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Image(
        painterResource(R.drawable.logo_mark), description,
        modifier.height(height).width(height * MARK_ASPECT), colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun RadarLockup(color: Color, height: Dp, modifier: Modifier = Modifier, description: String? = "Radar") {
    Image(
        painterResource(R.drawable.logo_lockup), description,
        modifier.height(height).width(height * LOCKUP_ASPECT), colorFilter = ColorFilter.tint(color),
    )
}
