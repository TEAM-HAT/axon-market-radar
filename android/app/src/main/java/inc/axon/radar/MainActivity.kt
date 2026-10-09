package inc.axon.radar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Tapping the app icon opens the live radar and refreshes the widgets on the way. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Refresh.schedule(this)
        Refresh.now(this)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Brief.radarUrl(Brief.cached(this)))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
