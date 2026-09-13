package fr.zeddcara.portfoliotracker

import java.time.LocalDate

data class Instrument(
    val ticker: String,
    val name: String,
    val yfTicker: String,
    val currency: String,
    val assetType: String,
    val target1: Double?,
    val target2: Double?,
    val stop: Double?
)

data class TxOverride(
    val costBasisAccount: Double? = null,
    val feeAccount: Double? = null,
    val fxAccountPerCcy: Double? = null,
    val pruAfter: Double? = null
)

data class Transaction(
    val date: LocalDate,
    val ticker: String,
    val type: String,
    val quantity: Double,
    val price: Double,
    val currency: String,
    val comment: String,
    val override: TxOverride = TxOverride()
)

data class Quote(
    val ticker: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val timestamp: Long = 0L,
    val fallback: Boolean = false
)

data class PositionState(
    var qty: Double = 0.0,
    var pruNative: Double = 0.0,
    var costBasisAccount: Double = 0.0,
    var realizedAccount: Double = 0.0
)

data class PositionView(
    val account: String,
    val ticker: String,
    val name: String,
    val assetType: String,
    val qty: Double,
    val pruNative: Double,
    val pruCurrency: String,
    val quote: Quote,
    val valueAccount: Double,
    val valueEur: Double,
    val costBasisAccount: Double,
    val unrealizedAccount: Double,
    val unrealizedPct: Double,
    val dayPnlAccount: Double,
    val dayPct: Double,
    val target1: Double?,
    val target2: Double?,
    val stop: Double?
)

data class AccountSummary(
    val key: String,
    val label: String,
    val currency: String,
    val positions: List<PositionView>,
    val cash: Double,
    val positionsValue: Double,
    val total: Double,
    val totalEur: Double,
    val unrealized: Double,
    val unrealizedPct: Double,
    val realized: Double,
    val dayPnl: Double,
    val dayPct: Double
)

data class PortfolioSnapshot(
    val generatedAt: String,
    val accounts: List<AccountSummary>,
    val totalEur: Double,
    val cashEur: Double,
    val unrealizedEur: Double,
    val dayPnlEur: Double,
    val dayPct: Double,
    val eurUsd: Double,
    val warnings: List<String>
)
