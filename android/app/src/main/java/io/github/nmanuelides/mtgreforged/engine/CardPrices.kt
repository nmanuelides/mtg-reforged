package io.github.nmanuelides.mtgreforged.engine

import android.content.Context
import android.net.ConnectivityManager
import android.util.JsonReader
import android.util.Log
import forge.StaticData
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A Card Kingdom price for one printing (or, when [exact] is false, the cheapest printing). */
data class CardPrice(val retail: Float, val buylist: Float, val inStock: Int, val url: String, val exact: Boolean) {
    val label: String get() = (if (exact) "" else "from ") + usd(retail)

    companion object {
        fun usd(value: Float): String = "$" + String.format(Locale.US, if (value >= 100) "%.0f" else "%.2f", value)
    }
}

/**
 * Card Kingdom's public price list, refreshed at most once a day on an unmetered network
 * and kept on disk as a small TSV of non-foil prices. Lookups match the exact printing by
 * set code + collector number, falling back to the cheapest printing of the same name.
 */
object CardPrices {
    private const val TAG = "CardPrices"
    private const val SOURCE = "https://api.cardkingdom.com/api/v2/pricelist"
    private const val BASE = "https://www.cardkingdom.com/"
    private const val PREFS = "prices"
    private const val KEY_FETCHED = "fetchedAt"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private class Index(
        val bySku: HashMap<String, Int>,
        val cheapestByName: HashMap<String, Int>,
        val retail: FloatArray,
        val buylist: FloatArray,
        val stock: IntArray,
        val urls: Array<String>,
    )

    @Volatile private var index: Index? = null

    private val _version = MutableStateFlow(0)
    /** Bumps whenever prices (re)load, so screens can recompute values. */
    val version: StateFlow<Int> = _version.asStateFlow()

    val available get() = index != null

    /** When the saved price list was downloaded (0 = never). */
    @Volatile var fetchedAt: Long = 0L
        private set

    /** Prices older than a day weren't refreshed (offline, mobile data…) and may have changed. */
    val stale get() = available && System.currentTimeMillis() - fetchedAt > 26 * 60 * 60 * 1000L

    /** What a deck is worth, leaving out basic lands (everyone has piles of them). */
    fun deckValue(cards: Iterable<Map.Entry<PaperCard, Int>>): Double =
        cards.filterNot { it.key.rules.type.isBasicLand }.sumOf { (c, n) -> (of(c)?.retail ?: 0f).toDouble() * n }

    private fun file(context: Context) = File(context.filesDir, "ck-prices.tsv")

    /** Loads the saved list, then downloads a fresh one if it's a day old and we're on Wi-Fi. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val saved = file(app)
        if (index == null && saved.exists()) runCatching { load(saved) }.onFailure { Log.w(TAG, "load failed", it) }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fetchedAt = prefs.getLong(KEY_FETCHED, 0L)
        val age = System.currentTimeMillis() - fetchedAt
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        if (age < MAX_AGE_MS && saved.exists()) return
        if (connectivity?.isActiveNetworkMetered != false) return
        runCatching {
            download(saved)
            fetchedAt = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FETCHED, fetchedAt).apply()
            load(saved)
        }.onFailure { Log.w(TAG, "download failed", it) }
    }

    fun of(card: PaperCard): CardPrice? {
        val idx = index ?: return null
        skuOf(card)?.let { sku -> idx.bySku[sku]?.let { return idx.price(it, exact = true) } }
        return idx.cheapestByName[nameKey(card.name)]?.let { idx.price(it, exact = false) }
    }

    /** Cheapest printing of a card name. */
    fun cheapest(name: String): CardPrice? {
        val idx = index ?: return null
        return idx.cheapestByName[nameKey(name)]?.let { idx.price(it, exact = false) }
    }

    private fun Index.price(i: Int, exact: Boolean) = CardPrice(retail[i], buylist[i], stock[i], BASE + urls[i], exact)

    private fun nameKey(name: String) = name.substringBefore(" // ").trim().lowercase()

    private fun skuKey(set: String, number: String) = set.uppercase() + "-" + number.uppercase().trimStart('0')

    private fun skuOf(card: PaperCard): String? {
        val number = card.collectorNumber?.takeIf { it.isNotBlank() && it != "0" } ?: return null
        val set = runCatching { StaticData.instance().getCardEdition(card.edition)?.scryfallCode }.getOrNull() ?: card.edition
        return skuKey(set, number)
    }

    /** Streams the (large) JSON list straight into a compact TSV, keeping only non-foil prices. */
    private fun download(target: File) {
        val connection = URL(SOURCE).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME} (Android)")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        val tmp = File(target.path + ".tmp")
        try {
            require(connection.responseCode == 200) { "Card Kingdom answered ${connection.responseCode}" }
            var kept = 0
            tmp.bufferedWriter().use { out ->
                JsonReader(connection.inputStream.bufferedReader()).use { json ->
                    json.beginObject()
                    while (json.hasNext()) {
                        if (json.nextName() != "data") { json.skipValue(); continue }
                        json.beginArray()
                        while (json.hasNext()) {
                            var sku = ""; var name = ""; var url = ""; var foil = false
                            var retail = 0f; var buy = 0f; var qty = 0
                            json.beginObject()
                            while (json.hasNext()) {
                                when (json.nextName()) {
                                    "sku" -> sku = json.nextString()
                                    "name" -> name = json.nextString()
                                    "url" -> url = json.nextString()
                                    "is_foil" -> foil = json.nextString() == "true"
                                    "price_retail" -> retail = json.nextString().toFloatOrNull() ?: 0f
                                    "price_buy" -> buy = json.nextString().toFloatOrNull() ?: 0f
                                    "qty_retail" -> qty = json.nextInt()
                                    else -> json.skipValue()
                                }
                            }
                            json.endObject()
                            if (foil || retail <= 0f || name.isBlank()) continue
                            out.append(sku).append('\t').append(name).append('\t').append(retail.toString()).append('\t')
                                .append(buy.toString()).append('\t').append(qty.toString()).append('\t').append(url).append('\n')
                            kept++
                        }
                        json.endArray()
                    }
                    json.endObject()
                }
            }
            require(kept > 1000) { "Price list looks incomplete ($kept cards)" }
            tmp.renameTo(target.also { it.delete() })
            Log.i(TAG, "saved $kept prices")
        } finally {
            connection.disconnect()
            tmp.delete()
        }
    }

    private fun load(file: File) {
        val lines = file.readLines()
        val n = lines.size
        val retail = FloatArray(n); val buy = FloatArray(n); val stock = IntArray(n)
        val urls = arrayOfNulls<String>(n)
        val bySku = HashMap<String, Int>(n * 2)
        val cheapest = HashMap<String, Int>(n)
        var i = 0
        for (line in lines) {
            val f = line.split('\t')
            if (f.size < 6) continue
            retail[i] = f[2].toFloatOrNull() ?: continue
            buy[i] = f[3].toFloatOrNull() ?: 0f
            stock[i] = f[4].toIntOrNull() ?: 0
            urls[i] = f[5]
            val dash = f[0].indexOf('-')
            if (dash > 0) bySku.putIfAbsent(skuKey(f[0].substring(0, dash), f[0].substring(dash + 1)), i)
            val key = nameKey(f[1])
            val best = cheapest[key]
            if (best == null || retail[i] < retail[best]) cheapest[key] = i
            i++
        }
        @Suppress("UNCHECKED_CAST")
        index = Index(bySku, cheapest, retail, buy, stock, (urls as Array<String?>).map { it.orEmpty() }.toTypedArray())
        _version.value++
        Log.i(TAG, "loaded $i prices")
    }
}
