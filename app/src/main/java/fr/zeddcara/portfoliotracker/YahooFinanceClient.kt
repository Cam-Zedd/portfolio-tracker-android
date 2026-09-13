package fr.zeddcara.portfoliotracker

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class YahooFinanceClient {
    private val quoteCache = ConcurrentHashMap<String, Quote>()
    private val fxCurrentCache = ConcurrentHashMap<String, Double>()
    private val fxHistoricalCache = ConcurrentHashMap<String, Double>()

    fun fetchQuote(ticker: String): Quote {
        quoteCache[ticker]?.let { return it }
        val enc = URLEncoder.encode(ticker, Charsets.UTF_8.name()).replace("+", "%20")
        val json = getJson("https://query1.finance.yahoo.com/v8/finance/chart/$enc?interval=1d&range=5d")
        val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val currency = normalizeCurrency(meta.optString("currency", ""))
        val rawPrice = meta.optDouble("regularMarketPrice", Double.NaN)
        val rawPrev = when {
            meta.has("chartPreviousClose") -> meta.optDouble("chartPreviousClose", Double.NaN)
            meta.has("previousClose") -> meta.optDouble("previousClose", Double.NaN)
            else -> Double.NaN
        }
        var price = rawPrice
        var prev = rawPrev
        if (!price.isFinite() || !prev.isFinite()) {
            val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
            val vals = (0 until closes.length()).mapNotNull { i -> if (closes.isNull(i)) null else closes.optDouble(i).takeIf { it.isFinite() } }
            if (!price.isFinite() && vals.isNotEmpty()) price = vals.last()
            if (!prev.isFinite() && vals.size >= 2) prev = vals[vals.size - 2]
        }
        if (!price.isFinite()) error("Cours Yahoo indisponible pour $ticker")
        if (!prev.isFinite()) prev = price
        val multiplier = if (meta.optString("currency") in setOf("GBp", "GBX", "GBx")) 0.01 else 1.0
        val q = Quote(ticker, price * multiplier, prev * multiplier, currency, meta.optLong("regularMarketTime", 0L))
        quoteCache[ticker] = q
        return q
    }

    fun rate(sourceCurrency: String, targetCurrency: String, date: LocalDate? = null): Double {
        val source = normalizeCurrency(sourceCurrency)
        val target = normalizeCurrency(targetCurrency)
        if (source == target) return 1.0
        val key = "$source->$target@${date ?: "NOW"}"
        val cache = if (date == null) fxCurrentCache else fxHistoricalCache
        cache[key]?.let { return it }

        val sourceToEur = if (source == "EUR") 1.0 else 1.0 / eurCross(source, date)
        val eurToTarget = if (target == "EUR") 1.0 else eurCross(target, date)
        return (sourceToEur * eurToTarget).also { cache[key] = it }
    }

    private fun eurCross(currency: String, date: LocalDate?): Double {
        val ticker = "EUR${currency}=X"
        return if (date == null) {
            fetchQuote(ticker).price
        } else {
            fetchHistoricalClose(ticker, date)
        }
    }

    private fun fetchHistoricalClose(ticker: String, targetDate: LocalDate): Double {
        val enc = URLEncoder.encode(ticker, Charsets.UTF_8.name()).replace("+", "%20")
        val p1 = targetDate.minusDays(5).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val p2 = targetDate.plusDays(5).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val json = getJson("https://query1.finance.yahoo.com/v8/finance/chart/$enc?interval=1d&period1=$p1&period2=$p2")
        val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val ts = result.getJSONArray("timestamp")
        val closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
        val targetEpoch = targetDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        var bestValue = Double.NaN
        var bestDistance = Long.MAX_VALUE
        for (i in 0 until minOf(ts.length(), closes.length())) {
            if (closes.isNull(i)) continue
            val v = closes.optDouble(i, Double.NaN)
            if (!v.isFinite()) continue
            val d = abs(ts.optLong(i) - targetEpoch)
            if (d < bestDistance) {
                bestDistance = d
                bestValue = v
            }
        }
        if (!bestValue.isFinite()) error("FX historique indisponible: $ticker $targetDate")
        return bestValue
    }

    private fun normalizeCurrency(raw: String): String = when (raw.trim()) {
        "GBp", "GBX", "GBx" -> "GBP"
        else -> raw.trim().uppercase().ifBlank { "USD" }
    }

    private fun getJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 PortfolioTrackerAndroid/1.0")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code !in 200..299) error("Yahoo HTTP $code: ${body.take(180)}")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }
}
