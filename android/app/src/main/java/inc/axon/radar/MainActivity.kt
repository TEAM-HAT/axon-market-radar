package inc.axon.radar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import inc.axon.radar.ui.Link
import inc.axon.radar.ui.RadarApp

/** The Market Radar app. Widgets open it on a tab, a move or a company. */
class MainActivity : ComponentActivity() {
    private val link = mutableStateOf<Link?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Refresh.schedule(this)
        Refresh.now(this)
        if (savedInstanceState == null) link.value = Link.from(intent)
        setContent { RadarApp(link) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        link.value = Link.from(intent)
    }
}
