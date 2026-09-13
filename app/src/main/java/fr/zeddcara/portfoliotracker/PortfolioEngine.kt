package fr.zeddcara.portfoliotracker

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class PortfolioEngine(private val store: CsvStore, private val yahoo: YahooFinanceClient = YahooFinanceClient()) {
    private data class AccountDef(
        val key: String,
        val label: String,
        val currency: String,
        val txFile: String,
        val instrumentFile: String,
        val configFile: String,
        val cashFile: String
    )

    private val accounts = listOf(
        AccountDef("cto", "CTO XTB", "EUR", "cto_transactions.csv", "cto_instruments.csv", "cto_config.csv", "cto_cash_movements.csv"),
        AccountDef("pea", "PEA XTB", "EUR", "pea_transactions.csv", "pea_instruments.csv", "pea_config.csv", "pea_cash_movements.csv"),
        AccountDef("etoro", "eToro", "USD", "etoro_transactions.csv", "etoro_instruments.csv", "etoro_config.csv", "etoro_cash_movements.csv")
    )

    fun buildSnapshot(): PortfolioSnapshot {
        val warnings = mutableListOf<String>()
        val overrideMap = loadOverrides()
        val summaries = accounts.map { computeAccount(it, overrideMap, warnings) }
        val eurUsd = try { yahoo.rate("EUR", "USD") } catch (_: Exception) { readConfig("etoro_config.csv")["snapshot_eurusd"]?.toDoubleOrNull() ?: 1.16 }
        val totalEur = summaries.sumOf { it.totalEur }
        val cashEur = summaries.sumOf { s -> if (s.currency == "EUR") s.cash else s.cash / eurUsd }
        val unrealizedEur = summaries.sumOf { s -> if (s.currency == "EUR") s.unrealized else s.unrealized / eurUsd }
        val dayPnlEur = summaries.sumOf { s -> if (s.currency == "EUR") s.dayPnl else s.dayPnl / eurUsd }
        val previous = totalEur - dayPnlEur
        val dayPct = if (abs(previous) > 1e-12) dayPnlEur / previous else 0.0
        return PortfolioSnapshot(
            generatedAt = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
            accounts = summaries,
            totalEur = totalEur,
            cashEur = cashEur,
            unrealizedEur = unrealizedEur,
            dayPnlEur = dayPnlEur,
            dayPct = dayPct,
            eurUsd = eurUsd,
            warnings = warnings.distinct()
        )
    }

    private fun computeAccount(def: AccountDef, overrides: Map<String, TxOverride>, warnings: MutableList<String>): AccountSummary {
        val cfg = readConfig(def.configFile)
        val snapshotDate = LocalDate.parse(cfg["snapshot_date"] ?: error("snapshot_date manquant pour ${def.key}"))
        val instruments = loadInstruments(def.instrumentFile)
        val instrumentByTicker = instruments.associateBy { it.ticker }
        val txs = loadTransactions(def, overrides)
        val states = instruments.associate { it.ticker to PositionState() }.toMutableMap()

        txs.sortedBy { it.date }.forEach { tx ->
            val inst = instrumentByTicker[tx.ticker] ?: error("${tx.ticker} absent de ${def.instrumentFile}")
            val st = states.getOrPut(tx.ticker) { PositionState() }
            val fee = tx.override.feeAccount ?: 0.0
            val fx = tx.override.fxAccountPerCcy ?: safeRate(tx.currency, def.currency, tx.date, warnings)
            when (tx.type) {
                "BUY" -> {
                    val explicitCost = tx.override.costBasisAccount
                    val cost = explicitCost ?: (tx.quantity * tx.price * fx + fee)
                    val newQty = st.qty + tx.quantity
                    st.pruNative = if (newQty > 0) ((st.qty * st.pruNative) + (tx.quantity * tx.price)) / newQty else 0.0
                    st.qty = newQty
                    st.costBasisAccount += cost
                }
                "SELL" -> {
                    if (tx.quantity > st.qty + 1e-8) error("Vente ${tx.ticker}: ${tx.quantity} > ${st.qty}")
                    val oldQty = st.qty
                    val allocated = tx.override.costBasisAccount ?: if (oldQty > 0) st.costBasisAccount * (tx.quantity / oldQty) else 0.0
                    val proceeds = tx.quantity * tx.price * fx - fee
                    st.realizedAccount += proceeds - allocated
                    st.qty -= tx.quantity
                    st.costBasisAccount -= allocated
                    if (st.qty <= 1e-10) {
                        st.qty = 0.0
                        st.pruNative = 0.0
                        st.costBasisAccount = 0.0
                    }
                }
                else -> error("Type ${tx.type} invalide pour ${tx.ticker}")
            }
            if (st.qty > 1e-10 && tx.override.pruAfter != null) {
                st.pruNative = tx.override.pruAfter
                st.costBasisAccount = st.qty * st.pruNative * fx
            }
            @Suppress("UNUSED_VARIABLE") val ignored = inst
        }

        val lastTxPrice = txs.groupBy { it.ticker }.mapValues { (_, list) -> list.maxBy { it.date }.price }
        val positionViews = mutableListOf<PositionView>()
        for (inst in instruments) {
            val st = states[inst.ticker] ?: continue
            if (st.qty <= 1e-10) continue
            val quote = try {
                yahoo.fetchQuote(inst.yfTicker)
            } catch (e: Exception) {
                warnings.add("${inst.ticker}: cours live indisponible, dernier prix de transaction utilisé")
                val p = lastTxPrice[inst.ticker] ?: st.pruNative
                Quote(inst.yfTicker, p, p, inst.currency, fallback = true)
            }
            val quoteToAccount = safeRate(quote.currency, def.currency, null, warnings)
            val valueAccount = st.qty * quote.price * quoteToAccount
            val dayPnl = st.qty * (quote.price - quote.previousClose) * quoteToAccount
            val previousValue = valueAccount - dayPnl
            val dayPct = if (abs(previousValue) > 1e-12) dayPnl / previousValue else 0.0
            val unreal = valueAccount - st.costBasisAccount
            val unrealPct = if (abs(st.costBasisAccount) > 1e-12) unreal / st.costBasisAccount else 0.0
            val valueEur = if (def.currency == "EUR") valueAccount else valueAccount * safeRate(def.currency, "EUR", null, warnings)
            positionViews += PositionView(
                account = def.label, ticker = inst.ticker, name = inst.name, assetType = inst.assetType,
                qty = st.qty, pruNative = st.pruNative, pruCurrency = inst.currency,
                quote = quote, valueAccount = valueAccount, valueEur = valueEur,
                costBasisAccount = st.costBasisAccount, unrealizedAccount = unreal, unrealizedPct = unrealPct,
                dayPnlAccount = dayPnl, dayPct = dayPct,
                target1 = inst.target1, target2 = inst.target2, stop = inst.stop
            )
        }

        val cash = cashBalance(def, cfg, snapshotDate, txs, warnings)
        val positionsValue = positionViews.sumOf { it.valueAccount }
        val total = cash + positionsValue
        val totalEur = if (def.currency == "EUR") total else total * safeRate(def.currency, "EUR", null, warnings)
        val unrealized = positionViews.sumOf { it.unrealizedAccount }
        val costBasis = positionViews.sumOf { it.costBasisAccount }
        val realized = states.values.sumOf { it.realizedAccount }
        val dayPnl = positionViews.sumOf { it.dayPnlAccount }
        val previous = positionsValue - dayPnl + cash
        return AccountSummary(
            key = def.key, label = def.label, currency = def.currency,
            positions = positionViews.sortedByDescending { it.valueAccount }, cash = cash,
            positionsValue = positionsValue, total = total, totalEur = totalEur,
            unrealized = unrealized, unrealizedPct = if (abs(costBasis) > 1e-12) unrealized / costBasis else 0.0,
            realized = realized, dayPnl = dayPnl, dayPct = if (abs(previous) > 1e-12) dayPnl / previous else 0.0
        )
    }

    private fun cashBalance(def: AccountDef, cfg: Map<String, String>, snapshotDate: LocalDate, txs: List<Transaction>, warnings: MutableList<String>): Double {
        val anchorKey = if (def.currency == "USD") "cash_anchor_usd" else "cash_anchor_eur"
        var cash = cfg[anchorKey]?.toDoubleOrNull() ?: 0.0
        txs.forEach { tx ->
            if (!tx.date.isAfter(snapshotDate)) return@forEach
            val fee = tx.override.feeAccount ?: 0.0
            val fx = tx.override.fxAccountPerCcy ?: safeRate(tx.currency, def.currency, tx.date, warnings)
            val amount = tx.quantity * tx.price * fx
            cash += when (tx.type) {
                "BUY" -> -(amount + fee)
                "SELL" -> amount - fee
                else -> 0.0
            }
        }
        val table = CsvUtils.read(store.file(def.cashFile))
        val amountCol = if (def.currency == "USD") "amount_usd" else "amount_eur"
        table.rows.forEach { row ->
            val map = table.header.mapIndexed { i, h -> h to row.getOrElse(i) { "" } }.toMap()
            val d = map["date"]?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: return@forEach
            if (d.isBefore(snapshotDate)) return@forEach
            val amount = map[amountCol]?.toDoubleOrNull() ?: 0.0
            cash += when (map["type"]?.trim()?.uppercase()) {
                "DEPOSIT", "DIVIDEND", "INTEREST", "CREDIT" -> amount
                "WITHDRAWAL", "FEE", "DEBIT" -> -amount
                else -> 0.0
            }
        }
        return cash
    }

    private fun readConfig(filename: String): Map<String, String> {
        val table = CsvUtils.read(store.file(filename))
        val keyIndex = table.header.indexOf("key")
        val valueIndex = table.header.indexOf("value")
        if (keyIndex < 0 || valueIndex < 0) return emptyMap()
        return table.rows.associate { it.getOrElse(keyIndex) { "" } to it.getOrElse(valueIndex) { "" } }
    }

    private fun loadInstruments(filename: String): List<Instrument> {
        val t = CsvUtils.read(store.file(filename))
        return t.rows.mapNotNull { row ->
            val m = t.header.mapIndexed { i, h -> h to row.getOrElse(i) { "" } }.toMap()
            val ticker = m["ticker"].orEmpty().trim()
            if (ticker.isBlank()) null else Instrument(
                ticker = ticker,
                name = m["name"].orEmpty().ifBlank { ticker },
                yfTicker = m["yf_ticker"].orEmpty().ifBlank { ticker },
                currency = m["currency"].orEmpty().uppercase().ifBlank { "EUR" },
                assetType = m["asset_type"].orEmpty().ifBlank { "Actif" },
                target1 = m["target_1"]?.toDoubleOrNull(),
                target2 = m["target_2"]?.toDoubleOrNull(),
                stop = m["stop"]?.toDoubleOrNull()
            )
        }
    }

    private fun loadTransactions(def: AccountDef, overrides: Map<String, TxOverride>): List<Transaction> {
        val t = CsvUtils.read(store.file(def.txFile))
        val counts = mutableMapOf<String, Int>()
        return t.rows.mapNotNull { row ->
            val m = t.header.mapIndexed { i, h -> h to row.getOrElse(i) { "" } }.toMap()
            val ticker = m["ticker"].orEmpty().trim()
            if (ticker.isBlank()) return@mapNotNull null
            val date = LocalDate.parse(m["date"].orEmpty().trim())
            val type = m["type"].orEmpty().trim().uppercase()
            val qty = m["quantity"]?.toDoubleOrNull() ?: error("Quantité invalide: $ticker")
            val price = m["price"]?.toDoubleOrNull() ?: error("Prix invalide: $ticker")
            val currency = m["currency"].orEmpty().trim().uppercase()
            val base = listOf(def.key, date.toString(), ticker.uppercase(), type, numKey(qty), numKey(price), currency).joinToString("|")
            val n = (counts[base] ?: 0) + 1
            counts[base] = n
            val key = "$base|#$n"
            val fromFile = overrides[key] ?: TxOverride()
            val comment = m["comment"].orEmpty()
            val merged = TxOverride(
                costBasisAccount = directive(comment, "COST") ?: fromFile.costBasisAccount,
                feeAccount = directive(comment, "FEE") ?: fromFile.feeAccount,
                fxAccountPerCcy = directive(comment, "FX") ?: fromFile.fxAccountPerCcy,
                pruAfter = directive(comment, "PRU") ?: fromFile.pruAfter
            )
            Transaction(date, ticker, type, qty, price, currency, comment, merged)
        }
    }

    private fun loadOverrides(): Map<String, TxOverride> {
        val t = CsvUtils.read(store.file("_transaction_overrides.csv"))
        return t.rows.mapNotNull { row ->
            val m = t.header.mapIndexed { i, h -> h to row.getOrElse(i) { "" } }.toMap()
            val key = m["tx_key"].orEmpty()
            if (key.isBlank()) null else key to TxOverride(
                costBasisAccount = m["cost_basis_account"]?.toDoubleOrNull(),
                feeAccount = m["fee_account"]?.toDoubleOrNull(),
                fxAccountPerCcy = m["fx_account_per_ccy"]?.toDoubleOrNull(),
                pruAfter = m["pru_after"]?.toDoubleOrNull()
            )
        }.toMap()
    }

    private fun directive(comment: String, name: String): Double? {
        val regex = Regex("(?:^|[;|\\s])${Regex.escape(name)}\\s*=\\s*([-+]?\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE)
        return regex.find(comment)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun safeRate(source: String, target: String, date: LocalDate?, warnings: MutableList<String>): Double {
        if (source.equals(target, true)) return 1.0
        return try { yahoo.rate(source, target, date) } catch (e: Exception) {
            warnings.add("FX $source→$target indisponible${date?.let { " au $it" } ?: ""}: taux 1 utilisé en secours")
            1.0
        }
    }

    private fun numKey(v: Double): String = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}
