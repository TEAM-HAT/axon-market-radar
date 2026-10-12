package inc.axon.radar.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AXON's mark on one move: what it means for AXON, and the next step when there is one. */
data class Mark(val mark: String, val why: String, val next: String?, val owner: String?) {
    val label: String get() = Lens.markLabel(mark)
    val matters: Boolean get() = mark == "threat" || mark == "opportunity" || mark == "watch"
}

/** How AXON sees a company: its roles (the first is the main one), threat level, the markets where it matters and a note. */
data class Stance(val roles: List<String>, val threat: String, val markets: List<String>, val note: String) {
    val role: String get() = roles.firstOrNull() ?: "context"
    val rival: Boolean get() = role == "competitor" || role == "kit"
}

/** One of AXON's own relationships: a client, partner, provider or named rival, with its status. */
data class Relation(
    val id: String, val name: String, val publicId: String?, val relationship: String, val status: String,
    val markets: List<String>, val note: String,
)

/** AXON's position in one of its markets, and what to watch there. */
data class Position(val market: String, val axon: String, val watch: String)

data class Decision(val date: String, val text: String, val evidence: String)

/** One mark in the capability grid: full (documented), claimed (marketing only) or absent, what exists, and its sources. */
data class PillarCell(val mark: String, val note: String, val sources: List<String>)

/** A pillar of the grid and the test its marks answer. */
data class Pillar(val id: String, val name: String, val test: String)

/** A company assessed pillar by pillar: AXON's reading of what it is, and when its record was checked. */
data class Assessed(
    val id: String, val name: String, val checked: String, val basis: String?, val verdict: String, val cells: Map<String, PillarCell>,
) {
    fun count(mark: String): Int = cells.values.count { it.mark == mark }
}

/** AXON and its competitors by pillar, from AXON's July 2026 assessment onwards. */
data class Grid(val method: String, val pillars: List<Pillar>, val companies: Map<String, Assessed>, val next: List<String>) {
    val axon: Assessed? get() = companies["axon"]
    val rivals: List<Assessed> get() = companies.values.filter { it.id != "axon" }
}

/**
 * AXON's private view of the radar. It never travels with the public data: the app reads it from the private
 * repository with a read-only key the user enters once, and keeps it encrypted on the phone.
 */
data class Lens(
    val version: String?, val updated: String?, val stances: Map<String, Stance>, val marks: Map<String, Mark>,
    val relations: List<Relation>, val positions: List<Position>, val watchIds: List<String>, val watchNames: List<String>,
    val decisions: List<Decision>, val grid: Grid? = null,
) {
    fun stance(companyId: String?): Stance? = companyId?.let { stances[it] }
    fun mark(moveId: String): Mark? = marks[moveId]

    /** Every open next step, with the move that prompted it. */
    val steps: List<Pair<String, Mark>> get() = marks.filter { (_, m) -> !m.next.isNullOrBlank() && !m.owner.isNullOrBlank() }.toList()

    companion object {
        val ROLE_ORDER = listOf("competitor", "kit", "buyer-investor", "prospect", "partner", "context")
        val THREAT_ORDER = listOf("high", "medium", "low", "none")
        val MARKETS = listOf("UAE", "Saudi Arabia", "Bahrain", "EU", "UK", "US", "Canada")
        val OWNERS = listOf("Leadership", "Strategy", "BD", "Compliance", "Product")

        fun roleLabel(r: String?): String = when (r) {
            "competitor" -> "Direct competitor"
            "kit" -> "Build-it-yourself kit"
            "partner" -> "Partner or supplier"
            "prospect" -> "Prospect"
            "buyer-investor" -> "Buyer or investor"
            else -> "No direct role"
        }

        fun rolePlural(r: String?): String = when (r) {
            "competitor" -> "Competitors"
            "kit" -> "Kits"
            "partner" -> "Partners"
            "prospect" -> "Prospects"
            "buyer-investor" -> "Buyers & investors"
            else -> "No direct role"
        }

        fun threatLabel(t: String?): String = when (t) {
            "high" -> "High threat"
            "medium" -> "Medium threat"
            "low" -> "Low threat"
            else -> "No threat"
        }

        fun markLabel(m: String?): String = when (m) {
            "threat" -> "Threat"
            "opportunity" -> "Opportunity"
            "watch" -> "Watch"
            else -> "No effect"
        }

        /** How much a mark matters, for ordering: threats first, then opportunities, then moves to watch. */
        fun weight(m: String?): Int = when (m) {
            "threat" -> 0
            "opportunity" -> 1
            "watch" -> 2
            else -> 3
        }

        /** A pillar mark in words, as the July 2026 assessment's legend puts it. */
        fun pillarLabel(m: String?): String = when (m) {
            "full" -> "Documented & verifiable"
            "claimed" -> "Claimed in marketing, not evidenced"
            else -> "Absent from the documented record"
        }

        /** The same, short enough for a tag. */
        fun pillarShort(m: String?): String = when (m) {
            "full" -> "Documented"
            "claimed" -> "Claimed only"
            else -> "Absent"
        }

        private fun parseGrid(p: JSONObject): Grid? {
            val pillars = p.list("pillars").mapNotNull { x -> x.str("id")?.let { Pillar(it, x.str("name") ?: it, x.str("test") ?: "") } }
            val companies = p.optJSONObject("companies")?.let { c ->
                c.keys().asSequence().mapNotNull { id ->
                    val x = c.optJSONObject(id) ?: return@mapNotNull null
                    val cells = x.optJSONObject("cells")?.let { cs ->
                        cs.keys().asSequence().mapNotNull { pid ->
                            val y = cs.optJSONObject(pid) ?: return@mapNotNull null
                            pid to PillarCell(y.str("mark") ?: "absent", y.str("note") ?: "", y.strings("sources"))
                        }.toMap()
                    } ?: emptyMap()
                    id to Assessed(id, x.str("name") ?: id, x.str("checked") ?: "", x.str("basis"), x.str("verdict") ?: "", cells)
                }.toMap()
            } ?: emptyMap()
            if (pillars.isEmpty() || companies.isEmpty()) return null
            return Grid(p.str("method") ?: "", pillars, companies, p.strings("next"))
        }

        fun parse(text: String): Lens {
            val o = JSONObject(text)
            val stances = o.optJSONObject("companies")?.let { c ->
                c.keys().asSequence().mapNotNull { id ->
                    val s = c.optJSONObject(id) ?: return@mapNotNull null
                    id to Stance(s.strings("roles"), s.str("threat") ?: "none", s.strings("markets"), s.str("note") ?: "")
                }.toMap()
            } ?: emptyMap()
            val marks = o.optJSONObject("marks")?.let { m ->
                m.keys().asSequence().mapNotNull { id ->
                    val x = m.optJSONObject(id) ?: return@mapNotNull null
                    val mark = x.str("mark") ?: return@mapNotNull null
                    id to Mark(mark, x.str("why") ?: "", x.str("next"), x.str("owner"))
                }.toMap()
            } ?: emptyMap()
            val relations = o.list("ecosystem").map { r ->
                Relation(
                    r.str("id") ?: "", r.str("name") ?: "", r.str("public_id"), r.str("relationship") ?: "",
                    r.str("status") ?: "", r.strings("markets"), r.str("note") ?: "",
                )
            }
            val positions = o.list("markets").map { Position(it.str("market") ?: "", it.str("axon_position") ?: "", it.str("watch") ?: "") }
            val w = o.optJSONObject("watchlist")
            val decisions = o.list("decisions").map { Decision(it.str("date") ?: "", it.str("decision") ?: "", it.str("evidence") ?: "") }
            val grid = o.optJSONObject("pillars")?.let { runCatching { parseGrid(it) }.getOrNull() }
            return Lens(
                o.str("version"), o.str("updated"), stances, marks, relations, positions,
                w?.strings("companies") ?: emptyList(), w?.strings("private") ?: emptyList(), decisions, grid,
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

        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}

/** Seals small secrets with a key held in the phone's hardware keystore. Returns null where the keystore is missing. */
object Vault {
    private const val ALIAS = "radar-private-view"

    private fun key(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build(),
            )
            generateKey()
        }
    }.getOrNull()

    fun seal(plain: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key() ?: return null)
        val iv = c.iv
        byteArrayOf(iv.size.toByte()) + iv + c.doFinal(plain)
    }.getOrNull()

    fun open(sealed: ByteArray): ByteArray? = runCatching {
        val n = sealed[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key() ?: return null, GCMParameterSpec(128, sealed.copyOfRange(1, 1 + n)))
        c.doFinal(sealed.copyOfRange(1 + n, sealed.size))
    }.getOrNull()
}

/** What happened when the app asked for the private view. */
sealed interface LensResult {
    data object NoKey : LensResult
    data object Unchanged : LensResult
    data class Updated(val lens: Lens) : LensResult
    data class Refused(val code: Int) : LensResult
    data class Failed(val reason: String) : LensResult
}

/**
 * The private view on this phone: the read-only key and the last copy of the lens, both sealed with the keystore.
 * The lens is read from the private repository through GitHub's API, only with the key.
 */
object LensStore {
    const val REPO = "TEAM-HAT/axon-radar-private"
    private const val PREFS = "private-view"

    /** Where the lens is read from: GitHub's API for the private repository. Tests point it at a local server. */
    @Volatile internal var api = "https://api.github.com/repos/$REPO/contents/lens/axon-lens.json"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "private-view.bin")

    /** Kept only in memory when the phone has no keystore, so the key is never written in the clear. */
    @Volatile private var memoryKey: String? = null
    @Volatile private var memoryLens: Lens? = null

    fun hasKey(ctx: Context): Boolean = key(ctx) != null

    private fun key(ctx: Context): String? {
        memoryKey?.let { return it }
        val s = prefs(ctx).getString("key", null) ?: return null
        return Vault.open(Base64.decode(s, Base64.NO_WRAP))?.toString(Charsets.UTF_8)
    }

    private fun saveKey(ctx: Context, key: String) {
        val sealed = Vault.seal(key.toByteArray(Charsets.UTF_8))
        if (sealed == null) memoryKey = key
        else prefs(ctx).edit().putString("key", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    /** Removes the key and every copy of the private view from this phone. */
    fun forget(ctx: Context) {
        memoryKey = null
        memoryLens = null
        prefs(ctx).edit().clear().commit()
        file(ctx).delete()
    }

    fun cached(ctx: Context): Lens? {
        memoryLens?.let { return it }
        if (!hasKey(ctx)) return null
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { Lens.parse(Vault.open(f.readBytes())!!.toString(Charsets.UTF_8)) }.getOrNull()
    }

    /** When the private view last arrived, as an ISO timestamp. */
    fun fetchedAt(ctx: Context): String? = prefs(ctx).getString("fetched", null)

    /** Tries a new key: it is kept only if GitHub accepts it and the lens reads. */
    fun connect(ctx: Context, key: String): LensResult {
        val clean = key.trim()
        if (clean.isEmpty()) return LensResult.Failed("Paste the key first.")
        val r = fetch(ctx, clean, null)
        if (r is LensResult.Updated) saveKey(ctx, clean)
        return r
    }

    /** Fetches the lens if it changed since the last copy. */
    fun refresh(ctx: Context): LensResult {
        val key = key(ctx) ?: return LensResult.NoKey
        return fetch(ctx, key, prefs(ctx).getString("etag", null)?.takeIf { file(ctx).exists() || memoryLens != null })
    }

    private fun fetch(ctx: Context, key: String, etag: String?): LensResult {
        val conn = try {
            (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/vnd.github.raw+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                etag?.let { setRequestProperty("If-None-Match", it) }
            }
        } catch (e: Exception) {
            return LensResult.Failed("The phone couldn't reach GitHub.")
        }
        return try {
            when (val code = conn.responseCode) {
                304 -> LensResult.Unchanged
                200 -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val lens = Lens.parse(body)
                    val sealed = Vault.seal(body.toByteArray(Charsets.UTF_8))
                    if (sealed != null) {
                        val tmp = File(ctx.filesDir, "private-view.tmp")
                        tmp.writeBytes(sealed)
                        tmp.renameTo(file(ctx))
                    }
                    memoryLens = if (sealed == null) lens else null
                    prefs(ctx).edit().putString("etag", conn.getHeaderField("ETag"))
                        .putString("fetched", java.time.OffsetDateTime.now().toString()).commit()
                    LensResult.Updated(lens)
                }
                else -> LensResult.Refused(code)
            }
        } catch (e: Exception) {
            LensResult.Failed("The phone couldn't reach GitHub.")
        } finally {
            conn.disconnect()
        }
    }

    /** A plain sentence for a result the user should see. */
    fun explain(r: LensResult): String = when (r) {
        LensResult.NoKey -> "No key on this phone yet."
        LensResult.Unchanged -> "Up to date."
        is LensResult.Updated -> "Connected: ${r.lens.marks.size} marks and ${r.lens.stances.size} companies."
        is LensResult.Refused -> when (r.code) {
            401 -> "GitHub didn't accept this key. Check it was copied whole and hasn't expired."
            403 -> "GitHub refused the key. If TEAM-HAT asks owners to approve new keys, approve it in the organisation's settings and try again."
            404 -> "This key can't see the private repository. Give it access to $REPO with Contents set to Read-only."
            else -> "GitHub answered with an error (${r.code}). Try again later."
        }
        is LensResult.Failed -> r.reason
    }
}
