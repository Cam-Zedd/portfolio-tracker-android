package fr.zeddcara.portfoliotracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: CsvStore
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private val executor = Executors.newSingleThreadExecutor()
    private var showingLive = true

    companion object {
        private const val REQ_EXPORT = 2001
        private const val REQ_IMPORT = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CsvStore(this).also { it.ensureSeeded() }
        buildUi()
        val saved = File(filesDir, "dashboard_android.html")
        if (saved.exists()) loadLiveHtml(saved.readText()) else loadBundledDashboard()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 29))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(11, 25, 42))
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(3,0,3,0) }
        }
        bar.addView(button("Dashboard") { showDashboardChoice() })
        bar.addView(button("Données") { showDataMenu() })
        bar.addView(button("Actualiser") { refreshDashboard() })
        bar.addView(button("Exporter") { exportZip() })
        bar.addView(button("PDF") { printPdf() })

        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            setBackgroundColor(Color.rgb(7,16,29))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12,6,12,6)
            setBackgroundColor(Color.rgb(11,25,42))
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        status = TextView(this).apply {
            text = "V5.7 Android · prêt"
            setTextColor(Color.rgb(180,205,232))
            textSize = 11f
            setPadding(10,0,0,0)
        }
        footer.addView(progress, LinearLayout.LayoutParams(34,34))
        footer.addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        root.addView(webView)
        root.addView(footer)
        setContentView(root)
    }

    private fun showDashboardChoice() {
        val options = arrayOf("Dashboard Android live", "Dashboard V5.7 original (11/09)")
        AlertDialog.Builder(this).setTitle("Dashboard").setItems(options) { _, which ->
            if (which == 0) {
                val saved = File(filesDir, "dashboard_android.html")
                if (saved.exists()) loadLiveHtml(saved.readText()) else refreshDashboard()
            } else loadBundledDashboard()
        }.show()
    }

    private fun loadBundledDashboard() {
        showingLive = false
        webView.loadUrl("file:///android_asset/dashboard/dashboard_v57.html")
        status.text = "Dashboard V5.7 original · données figées au paquet du 11/09/2026"
    }

    private fun loadLiveHtml(html: String) {
        showingLive = true
        webView.loadDataWithBaseURL("file:///android_asset/dashboard/", html, "text/html", "UTF-8", null)
        status.text = "Dashboard Android live · CSV V5.7 locaux"
    }

    private fun refreshDashboard() {
        progress.visibility = View.VISIBLE
        status.text = "Actualisation des cours et recalcul…"
        executor.execute {
            try {
                val snapshot = PortfolioEngine(store).buildSnapshot()
                val html = DashboardRenderer.render(snapshot)
                File(filesDir, "dashboard_android.html").writeText(html)
                runOnUiThread {
                    progress.visibility = View.GONE
                    loadLiveHtml(html)
                    status.text = "Mis à jour ${snapshot.generatedAt} · ${snapshot.warnings.size} avertissement(s)"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    status.text = "Erreur: ${e.message ?: e.javaClass.simpleName}"
                    AlertDialog.Builder(this).setTitle("Actualisation impossible").setMessage(e.stackTraceToString().take(5000)).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun showDataMenu() {
        val actions = mutableListOf<Pair<String, String?>>()
        actions += "＋ Transactions CTO" to "cto_transactions.csv"
        actions += "＋ Transactions PEA" to "pea_transactions.csv"
        actions += "＋ Transactions eToro" to "etoro_transactions.csv"
        actions += "Cash CTO" to "cto_cash_movements.csv"
        actions += "Cash PEA" to "pea_cash_movements.csv"
        actions += "Cash eToro" to "etoro_cash_movements.csv"
        actions += "Configuration CTO" to "cto_config.csv"
        actions += "Configuration PEA" to "pea_config.csv"
        actions += "Configuration eToro" to "etoro_config.csv"
        actions += "Instruments CTO" to "cto_instruments.csv"
        actions += "Instruments PEA" to "pea_instruments.csv"
        actions += "Instruments eToro" to "etoro_instruments.csv"
        actions += "Watchlist News" to "news_watchlist.csv"
        actions += "News enrichment" to "news_enrichment.csv"
        actions += "Overrides avancés PRU / FX" to "_transaction_overrides.csv"
        actions += "Benchmarks" to "benchmarks.csv"
        actions += "Historique annuel CTO" to "cto_annual_history.csv"
        actions += "Historique annuel PEA" to "pea_annual_history.csv"
        actions += "Historique annuel eToro" to "etoro_annual_history.csv"
        actions += "Importer un CSV…" to null
        actions += "Réinitialiser les CSV à la V5.7…" to "__RESET__"
        AlertDialog.Builder(this).setTitle("Fichiers d’entrée V5.7").setItems(actions.map { it.first }.toTypedArray()) { _, which ->
            when (val target = actions[which].second) {
                null -> importCsv()
                "__RESET__" -> confirmReset()
                else -> startActivity(Intent(this, CsvEditorActivity::class.java).putExtra("filename", target))
            }
        }.show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Réinitialiser ?")
            .setMessage("Tous les CSV locaux seront remplacés par ceux de la V5.7 MASTER du 11/09/2026.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Réinitialiser") { _, _ ->
                store.resetToBundled()
                File(filesDir, "dashboard_android.html").delete()
                loadBundledDashboard()
                Toast.makeText(this, "CSV restaurés", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun exportZip() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "PortfolioTracker_Android_inputs.zip")
        }
        startActivityForResult(intent, REQ_EXPORT)
    }

    private fun importCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, REQ_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        when (requestCode) {
            REQ_EXPORT -> try {
                contentResolver.openOutputStream(uri)?.use { store.exportZip(it) }
                Toast.makeText(this, "Export terminé", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Export: ${e.message}", Toast.LENGTH_LONG).show() }
            REQ_IMPORT -> importCsvFromUri(uri)
        }
    }

    private fun importCsvFromUri(uri: Uri) {
        val name = displayName(uri) ?: return Toast.makeText(this, "Nom de fichier introuvable", Toast.LENGTH_LONG).show()
        if (name !in CsvStore.INPUT_FILES) {
            Toast.makeText(this, "Nom non reconnu: $name", Toast.LENGTH_LONG).show(); return
        }
        try {
            contentResolver.openInputStream(uri)?.use { input -> store.file(name).outputStream().use { input.copyTo(it) } }
            Toast.makeText(this, "$name importé", Toast.LENGTH_SHORT).show()
            status.text = "$name importé · actualisez le dashboard"
        } catch (e: Exception) { Toast.makeText(this, "Import: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun displayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
        } finally { cursor?.close() }
    }

    private fun printPdf() {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val adapter = webView.createPrintDocumentAdapter(if (showingLive) "Portfolio_Android" else "Portfolio_V5_7")
        printManager.print("Portfolio Tracker", adapter, null)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }
}
