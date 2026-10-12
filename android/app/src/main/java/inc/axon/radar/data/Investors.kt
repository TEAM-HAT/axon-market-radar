package inc.axon.radar.data

/**
 * Someone named as a lead or other investor in a move, with every round they joined on the radar, newest first.
 * total is the sum of those rounds' disclosed sizes, not what this investor put in.
 */
data class Investor(
    val id: String, val name: String, val rounds: List<Move>, val led: Set<String>, val total: Double?,
    val kind: String?, val country: String?, val website: String?, val company: Company?,
    val portfolio: List<Holding>, val with: List<CoInvestor>,
)

/** A company an investor backed, with the rounds it joined there, newest first. companyId is set when the radar tracks it. */
data class Holding(val companyId: String?, val name: String, val rounds: List<Move>, val led: Boolean)

/** Another investor that joined the same rounds, and how many. */
data class CoInvestor(val id: String, val name: String, val rounds: Int)

/** The investor index, built the same way as the web portal's so both show the same names, counts and order. */
object Investors {
    private fun investing(role: String) = role == "lead investor" || role == "investor"

    fun build(r: Radar): List<Investor> {
        class Acc(val id: String) {
            val names = LinkedHashMap<String, Int>()
            val rounds = mutableListOf<Move>()
            val led = LinkedHashSet<String>()
        }
        val by = LinkedHashMap<String, Acc>()
        r.moves.forEach { m ->
            val seen = HashSet<String>()
            m.parties.forEach { p ->
                if (!investing(p.role)) return@forEach
                val a = by.getOrPut(p.id) { Acc(p.id) }
                if (seen.add(p.id)) { a.rounds += m; a.names[p.name] = (a.names[p.name] ?: 0) + 1 }
                if (p.role == "lead investor") a.led += m.id
            }
        }
        val built = by.values.map { a ->
            val co = r.companyById[a.id]
            val org = r.orgs[a.id]
            val base = co?.name ?: org?.name
            // The name the sources use most often (Coinbase Ventures for Coinbase); ties go to the radar's own name.
            val name = a.names.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { if (it.key == base) 0 else 1 }).first().key
            val amounts = a.rounds.mapNotNull { it.amount }
            val port = LinkedHashMap<String, MutableList<Move>>()
            a.rounds.forEach { m -> port.getOrPut(m.companyId ?: ("n:" + (m.companyName ?: m.id))) { mutableListOf() } += m }
            val holdings = port.values.map { ms ->
                val m = ms.first()
                Holding(
                    m.companyId?.takeIf { r.companyById.containsKey(it) }, m.companyId?.let { r.companyById[it]?.name } ?: m.companyName ?: m.title,
                    ms.toList(), ms.any { it.id in a.led },
                )
            }
            val along = LinkedHashMap<String, Int>()
            a.rounds.forEach { m -> m.parties.filter { investing(it.role) && it.id != a.id }.map { it.id }.distinct().forEach { along[it] = (along[it] ?: 0) + 1 } }
            Investor(
                a.id, name, a.rounds.toList(), a.led.toSet(), amounts.takeIf { it.isNotEmpty() }?.sum(),
                kind = if (co != null) null else org?.kind, country = co?.hqCountry ?: org?.country, website = co?.website ?: org?.website,
                company = co, portfolio = holdings, with = emptyList(),
            ) to along
        }.sortedWith(compareByDescending<Pair<Investor, Map<String, Int>>> { it.first.rounds.size }.thenByDescending { it.first.led.size }.thenBy { it.first.name })
        val names = built.associate { it.first.id to it.first.name }
        return built.map { (v, along) ->
            v.copy(with = along.map { (id, n) -> CoInvestor(id, names[id] ?: id, n) }.sortedWith(compareByDescending<CoInvestor> { it.rounds }.thenBy { it.name }))
        }
    }

    /** What kind of investor it is: an organisation's kind, or a company on the radar. */
    fun kindLabel(v: Investor): String = when {
        v.company != null -> "Investor and company on the radar"
        v.kind != null && v.kind != "other" -> v.kind.replaceFirstChar { it.uppercase() }
        else -> "Investor"
    }

    /** One plain sentence: how many companies and rounds, since when, how many led, and who it invests with most. */
    fun summary(v: Investor): String {
        val first = v.rounds.last()
        val top2 = v.with.take(2).map { it.name }
        return "Backed ${Text.plural(v.portfolio.size, "company", "companies")} on the radar in ${Text.plural(v.rounds.size, "round", "rounds")} since " +
            Text.monthYear(first.date.take(7)) + (if (v.led.isNotEmpty()) ", leading ${v.led.size}" else "") + "." +
            (if (top2.isNotEmpty()) " Most often alongside ${top2.joinToString(" and ")}." else "")
    }

    /** The companies it backed that AXON counts as rivals, the biggest threats first. */
    fun rivalsBacked(v: Investor, lens: Lens?): List<Holding> {
        if (lens == null) return emptyList()
        val tier = mapOf("high" to 0, "medium" to 1, "low" to 2, "none" to 3)
        return v.portfolio.filter { h -> h.companyId != null && lens.stance(h.companyId)?.rival == true }
            .sortedWith(compareBy<Holding> { tier[lens.stance(it.companyId)?.threat] ?: 3 }.thenBy { it.name })
    }
}
