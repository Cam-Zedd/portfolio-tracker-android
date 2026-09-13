package fr.zeddcara.portfoliotracker

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvStore(private val context: Context) {
    val inputDir: File = File(context.filesDir, "portfolio_inputs")

    companion object {
        val INPUT_FILES = listOf(
            "cto_transactions.csv", "pea_transactions.csv", "etoro_transactions.csv",
            "cto_config.csv", "pea_config.csv", "etoro_config.csv",
            "cto_cash_movements.csv", "pea_cash_movements.csv", "etoro_cash_movements.csv",
            "cto_instruments.csv", "pea_instruments.csv", "etoro_instruments.csv",
            "cto_annual_history.csv", "pea_annual_history.csv", "etoro_annual_history.csv",
            "benchmarks.csv", "news_watchlist.csv", "news_enrichment.csv", "_transaction_overrides.csv"
        )
    }

    fun ensureSeeded() {
        inputDir.mkdirs()
        INPUT_FILES.forEach { name ->
            val dest = File(inputDir, name)
            if (!dest.exists()) {
                context.assets.open("input/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    fun resetToBundled() {
        inputDir.mkdirs()
        INPUT_FILES.forEach { name ->
            val dest = File(inputDir, name)
            context.assets.open("input/$name").use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
    }

    fun file(name: String): File {
        require(name in INPUT_FILES) { "Fichier non autorisé: $name" }
        return File(inputDir, name)
    }

    fun exportZip(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            INPUT_FILES.forEach { name ->
                val f = file(name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("inputs/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val dash = File(context.filesDir, "dashboard_android.html")
            if (dash.exists()) {
                zip.putNextEntry(ZipEntry("dashboard_android.html"))
                dash.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("plotly.min.js"))
            context.assets.open("dashboard/plotly.min.js").use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
