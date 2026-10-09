package inc.axon.radar

import android.content.Context
import android.text.Html
import android.text.Spanned
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime

/** The daily brief the widgets show, read from the radar's public brief file. */
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
        val bust = System.currentTimeMillis() / 300_000L
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
        b?.str("radar_url")?.takeIf { it.startsWith("https://") } ?: RADAR_URL

    /** The radar opened on one tab (#moves) or one item (#co=tether). */
    fun link(b: JSONObject?, hash: String): String = radarUrl(b).substringBefore('#') + "#" + hash
}

/** A string field, or null when it is missing, JSON null or blank. */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

fun JSONObject.objects(key: String): List<JSONObject> {
    val a: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

/** Date and text helpers shared by every widget. */
object Fmt {
    private val MON = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DOW = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun date(iso: String?): LocalDate? = runCatching { LocalDate.parse(iso!!.take(10)) }.getOrNull()

    fun day(iso: String?): String = date(iso)?.let { "${it.dayOfMonth} ${MON[it.monthValue - 1]}" } ?: ""

    fun dowDay(iso: String?): String = date(iso)?.let { "${DOW[it.dayOfWeek.value - 1]} ${it.dayOfMonth} ${MON[it.monthValue - 1]}" } ?: ""

    fun range(a: String?, b: String?, year: Boolean = false): String {
        val s = date(a) ?: return day(b)
        val e = date(b) ?: return day(a)
        val y = if (year) " ${e.year}" else ""
        return if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${MON[e.monthValue - 1]}$y"
        else "${s.dayOfMonth} ${MON[s.monthValue - 1]} – ${e.dayOfMonth} ${MON[e.monthValue - 1]}$y"
    }

    fun updated(iso: String?): String {
        val d = runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull() ?: date(iso) ?: return ""
        return "↻  Updated ${DOW[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MON[d.monthValue - 1]}"
    }

    /** "2025-10" to "Oct 2025". */
    fun month(ym: String?): String {
        val p = ym?.split("-") ?: return ""
        val m = p.getOrNull(1)?.toIntOrNull() ?: return ""
        return "${MON[(m - 1).coerceIn(0, 11)]} ${p[0]}"
    }

    fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun html(s: String): Spanned = Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT)

    /** Headline with the **key phrases** set in bold white, the rest left in the pale text colour. */
    fun headline(raw: String?): Spanned =
        html(escape(raw ?: "").replace(Regex("\\*\\*(.+?)\\*\\*"), "<b><font color=\"#FFFFFF\">$1</font></b>"))

    /** "LICENCE · VARA · 5 OCT" with the type picked out in colour. */
    fun meta(type: String, extra: String?, iso: String?, color: String = "#2852EA"): Spanned {
        val parts = listOfNotNull(extra?.takeIf { it.isNotBlank() }, day(iso).takeIf { it.isNotBlank() })
        val rest = if (parts.isEmpty()) "" else " · " + parts.joinToString(" · ") { escape(it) }
        return html("<font color=\"$color\">${escape(type)}</font>$rest")
    }

    fun glyph(type: String?): Int = when (type) {
        "Funding", "M&A" -> R.drawable.g_sq
        "Launch", "Partnership" -> R.drawable.g_dot
        else -> R.drawable.g_dia
    }

    fun initials(name: String?): String {
        val w = (name ?: "?").replace(Regex("[^A-Za-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            w.isEmpty() -> "?"
            w.size > 1 -> "${w[0][0]}${w[1][0]}".uppercase()
            else -> w[0].take(2).uppercase()
        }
    }
}
