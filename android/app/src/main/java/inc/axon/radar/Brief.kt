package inc.axon.radar

import android.content.Context
import android.text.Html
import android.text.Spanned
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime

/** The weekly brief the widgets show, read from the radar's public brief file. */
object Brief {
    const val FEED_URL = "https://team-hat.github.io/axon-market-radar/brief.json"
    const val RADAR_URL = "https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei"
    private const val PREFS = "radar"
    private const val KEY = "brief_json"

    fun cached(ctx: Context): JSONObject? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    /** Downloads the brief. Throws on network errors or a malformed file. */
    fun fetch(): String {
        val bust = System.currentTimeMillis() / 600_000L
        val conn = URL("$FEED_URL?t=$bust").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body) // validate before caching
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun radarUrl(b: JSONObject?): String =
        b?.optString("radar_url").takeUnless { it.isNullOrBlank() } ?: RADAR_URL
}

/** Small date and text helpers shared by both widgets. */
object Fmt {
    private val MON = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DOW = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun date(iso: String?): LocalDate? = runCatching { LocalDate.parse(iso!!.take(10)) }.getOrNull()

    fun day(iso: String?): String = date(iso)?.let { "${it.dayOfMonth} ${MON[it.monthValue - 1]}" } ?: ""

    fun range(a: String?, b: String?): String {
        val s = date(a) ?: return day(b)
        val e = date(b) ?: return day(a)
        return if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${MON[e.monthValue - 1]}"
        else "${s.dayOfMonth} ${MON[s.monthValue - 1]} – ${e.dayOfMonth} ${MON[e.monthValue - 1]}"
    }

    fun updated(iso: String?): String {
        val d = runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull() ?: date(iso) ?: return ""
        return "Updated ${DOW[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MON[d.monthValue - 1]}"
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Headline with the **key phrases** set in bold white, the rest left in the pale text colour. */
    fun headline(raw: String?): Spanned {
        val html = escape(raw ?: "").replace(Regex("\\*\\*(.+?)\\*\\*"), "<b><font color=\"#FFFFFF\">$1</font></b>")
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    }

    /** "REGULATION · ESMA · 8 OCT" with the type in AXON blue. */
    fun meta(type: String, regulator: String?, iso: String?): Spanned {
        val parts = listOfNotNull(regulator?.takeIf { it.isNotBlank() }, day(iso).takeIf { it.isNotBlank() })
        val rest = if (parts.isEmpty()) "" else " · " + parts.joinToString(" · ") { escape(it) }
        return Html.fromHtml("<font color=\"#2852EA\">${escape(type)}</font>$rest", Html.FROM_HTML_MODE_COMPACT)
    }

    fun glyph(type: String?): Int = when (type) {
        "Funding", "M&A" -> R.drawable.g_sq
        "Launch", "Partnership" -> R.drawable.g_dot
        else -> R.drawable.g_dia
    }
}
