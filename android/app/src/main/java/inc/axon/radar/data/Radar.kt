package inc.axon.radar.data

import android.content.Context
import inc.axon.radar.str
import inc.axon.radar.ui.Look
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

data class Move(
    val id: String, val date: String, val precision: String, val type: String, val typeLabel: String,
    val region: String?, val title: String, val summary: String, val amount: Double?, val regulator: String?,
    val jurisdiction: String?, val companyId: String?, val companyName: String?, val related: List<String>,
    val sourceName: String?, val sourceUrl: String?, val added: String?,
) {
    /** Who the move is about: the company, else the regulator, else the region. */
    val subject: String get() = companyName ?: regulator ?: jurisdiction ?: region ?: "Market"

    /** The one big figure on the move's black block: the amount, else the regulator, else the date. */
    val figure: String get() = Text.money(amount) ?: regulator?.takeIf { it != subject } ?: Text.day(date).uppercase()
}

data class Licence(val regulator: String?, val key: String?, val jurisdiction: String?, val type: String?, val year: Int?)
data class Funding(val total: Double?, val lastType: String?, val lastAmount: Double?, val lastDate: String?, val lead: String?)

data class Company(
    val id: String, val name: String, val hqCity: String?, val hqCountry: String?, val region: String?,
    val segment: String?, val founded: Int?, val website: String?, val blurb: String?, val licences: List<Licence>,
    val funding: Funding?, val status: String?, val sources: List<String>, val moves: Int, val lastDate: String?,
)

data class Regulator(val name: String, val where: String, val licences: Int, val actions: Int, val lastDate: String?) {
    val total get() = licences + actions
}

data class Month(val ym: String, val n: Int)

data class Trends(
    val since: String?, val total: Int, val licences: Int, val actions: Int, val commercial: Int, val capital: Int,
    val months: List<Month>, val partialLast: Boolean,
)

data class Radar(
    val updatedAt: String?, val nextRun: String?, val windowStart: String?, val windowEnd: String?, val count: Int,
    val newToday: Int, val headline: String, val mixText: String, val regions: Map<String, Int>, val highlights: List<String>,
    val moves: List<Move>, val companies: List<Company>, val regulators: List<Regulator>, val trends: Trends, val radarUrl: String,
) {
    val companyById: Map<String, Company> = companies.associateBy { it.id }
    val moveById: Map<String, Move> = moves.associateBy { it.id }

    /** Moves inside the 7-day window, topped up with the latest so the deck never runs thin. */
    val deck: List<Move> by lazy {
        val inWindow = moves.filter { windowStart != null && windowEnd != null && it.date >= windowStart && it.date <= windowEnd }
        if (inWindow.size >= 12) inWindow else moves.take(maxOf(12, inWindow.size))
    }

    fun movesFor(companyId: String): List<Move> = moves.filter { it.companyId == companyId || companyId in it.related }

    fun movesForRegulator(name: String): List<Move> = moves.filter { it.regulator == name }

    /** Moves that sit beside this one on the page's timeline: same company, else same regulator, else same kind. */
    fun timelineFor(m: Move): List<Move> = when {
        m.companyId != null -> movesFor(m.companyId)
        m.regulator != null -> movesForRegulator(m.regulator)
        else -> moves.filter { it.type == m.type }
    }

    fun latestMove(companyId: String): Move? = movesFor(companyId).firstOrNull()

    companion object {
        fun parse(text: String): Radar {
            val o = JSONObject(text)
            val moves = o.list("events").map { e ->
                Move(
                    id = e.getString("id"), date = e.str("date") ?: "", precision = e.str("date_precision") ?: "day",
                    type = e.str("type") ?: "Regulation", typeLabel = e.str("type_label") ?: e.str("type") ?: "",
                    region = e.str("region"), title = e.str("title") ?: "", summary = e.str("summary") ?: "",
                    amount = e.num("amount_usd_m"), regulator = e.str("regulator"), jurisdiction = e.str("jurisdiction"),
                    companyId = e.str("company_id"), companyName = e.str("company_name"),
                    related = e.strings("related"), sourceName = e.str("source_name"), sourceUrl = e.str("source_url"),
                    added = e.str("added"),
                )
            }
            val companies = o.list("companies").map { c ->
                val f = c.optJSONObject("funding")
                Company(
                    id = c.getString("id"), name = c.str("name") ?: c.getString("id"), hqCity = c.str("hq_city"),
                    hqCountry = c.str("hq_country"), region = c.str("region"), segment = c.str("segment"),
                    founded = c.int("founded"), website = c.str("website"), blurb = c.str("blurb"),
                    licences = c.list("licenses").map { l ->
                        Licence(l.str("regulator"), l.str("regulator_key"), l.str("jurisdiction"), l.str("type"), l.int("year"))
                    },
                    funding = f?.let {
                        Funding(it.num("total_usd_m"), it.str("last_round_type"), it.num("last_round_usd_m"), it.str("last_round_date"), it.str("lead"))
                    },
                    status = c.str("status"), sources = c.strings("sources"), moves = c.optInt("moves"), lastDate = c.str("last_date"),
                )
            }
            val regulators = o.list("regulators").map { r ->
                Regulator(r.str("name") ?: "Other", r.str("where") ?: "", r.optInt("licences"), r.optInt("actions"), r.str("last_date"))
            }
            val t = o.optJSONObject("trends") ?: JSONObject()
            val trends = Trends(
                since = t.str("since"), total = t.optInt("total", moves.size), licences = t.optInt("licences"),
                actions = t.optInt("regulatory_actions"), commercial = t.optInt("commercial"), capital = t.optInt("capital"),
                months = t.list("months").map { Month(it.str("m") ?: "", it.optInt("n")) }, partialLast = t.optBoolean("partial_last"),
            )
            val regions = o.optJSONObject("regions")?.let { r -> r.keys().asSequence().associateWith { r.optInt(it) } } ?: emptyMap()
            return Radar(
                updatedAt = o.str("updated_at"), nextRun = o.str("next_run"), windowStart = o.str("window_start"),
                windowEnd = o.str("window_end"), count = o.optInt("count"), newToday = o.optInt("new_today"),
                headline = o.str("headline") ?: "", mixText = o.str("mix_text") ?: "", regions = regions,
                highlights = o.strings("highlights"), moves = moves, companies = companies, regulators = regulators,
                trends = trends, radarUrl = o.str("radar_url") ?: "https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei",
            )
        }

        private fun JSONObject.list(key: String): List<JSONObject> {
            val a: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }

        private fun JSONObject.strings(key: String): List<String> {
            val a: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } }
        }

        private fun JSONObject.num(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
        private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else optInt(key).takeIf { it != 0 }
    }
}

/** The app's copy of radar.json: read from disk at once, refreshed from the web in the background. */
object Store {
    const val URL = "https://team-hat.github.io/axon-market-radar/radar.json"
    private const val PREFS = "radar"

    private fun file(ctx: Context) = File(ctx.filesDir, "radar.json")

    fun cached(ctx: Context): Radar? = runCatching { Radar.parse(file(ctx).readText()) }.getOrNull()

    /** Fetches radar.json if it changed since the last copy. Returns true when a new copy was saved. */
    fun refresh(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val conn = URL(URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.useCaches = false
        prefs.getString("radar_etag", null)?.takeIf { file(ctx).exists() }?.let { conn.setRequestProperty("If-None-Match", it) }
        try {
            when (conn.responseCode) {
                304 -> return false
                200 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    Radar.parse(body) // validate before saving
                    val tmp = File(ctx.filesDir, "radar.json.tmp")
                    tmp.writeText(body)
                    tmp.renameTo(file(ctx))
                    prefs.edit().putString("radar_etag", conn.getHeaderField("ETag")).apply()
                    return true
                }
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun watched(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("watch", emptySet())?.toSet() ?: emptySet()

    fun setWatched(ctx: Context, ids: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("watch", ids).apply()
    }

    /** Colour or black and white, for the app and its widgets. Kept on this phone. */
    fun look(ctx: Context): Look =
        if (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("look", null) == "mono") Look.Mono else Look.Colour

    fun setLook(ctx: Context, look: Look) {
        // Written at once, so widgets redrawn right after the switch read the new look.
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("look", if (look == Look.Mono) "mono" else "colour").commit()
    }
}

/** Dates, money and names, written the way the radar writes them. */
object Text {
    private fun date(iso: String?): LocalDate? = runCatching { LocalDate.parse(iso!!.take(10)) }.getOrNull()
    private fun mon(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.UK).take(3)

    fun day(iso: String?): String = date(iso)?.let { "${it.dayOfMonth} ${mon(it)}" } ?: ""
    fun dayYear(iso: String?): String = date(iso)?.let { "${it.dayOfMonth} ${mon(it)} ${it.year}" } ?: ""
    fun long(iso: String?, precision: String = "day"): String = date(iso)?.let {
        val month = it.month.getDisplayName(TextStyle.FULL, Locale.UK)
        if (precision == "month") "$month ${it.year}"
        else "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK)} ${it.dayOfMonth} $month ${it.year}"
    } ?: ""
    fun dowDay(iso: String?): String = date(iso)?.let { "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${it.dayOfMonth} ${mon(it)}" } ?: ""
    fun monthYear(ym: String?): String = runCatching { date("$ym-01")!!.let { "${mon(it)} ${it.year}" } }.getOrDefault("")
    fun monthFull(iso: String?): String = date(iso)?.let { "${it.month.getDisplayName(TextStyle.FULL, Locale.UK)} ${it.year}" } ?: ""
    fun range(a: String?, b: String?): String {
        val s = date(a) ?: return day(b)
        val e = date(b) ?: return day(a)
        return if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${mon(e)}" else "${s.dayOfMonth} ${mon(s)} – ${e.dayOfMonth} ${mon(e)}"
    }
    fun updated(iso: String?): String {
        val d = runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull() ?: date(iso) ?: return ""
        return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK)} ${d.dayOfMonth} ${mon(d)}"
    }

    fun money(m: Double?): String? {
        if (m == null) return null
        if (m >= 1000) return "$" + trim(m / 1000) + "B"
        return "$" + (if (m >= 100) Math.round(m).toString() else trim(m)) + "M"
    }
    private fun trim(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else String.format(Locale.UK, "%.1f", x)

    fun initials(name: String?): String {
        val w = (name ?: "?").replace(Regex("[^A-Za-z0-9 ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            w.isEmpty() -> "?"
            w.size > 1 -> "${w[0][0]}${w[1][0]}".uppercase()
            else -> w[0].take(2).uppercase()
        }
    }

    fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"

    fun host(u: String?): String = runCatching { java.net.URI(u!!).host.removePrefix("www.") }.getOrDefault("")

    /** "**bold**" markers from the sweep's headline, split into plain and bold runs. */
    fun runs(s: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        var bold = false
        s.split("**").forEach { part -> if (part.isNotEmpty()) out += part to bold; bold = !bold }
        return out
    }
}
