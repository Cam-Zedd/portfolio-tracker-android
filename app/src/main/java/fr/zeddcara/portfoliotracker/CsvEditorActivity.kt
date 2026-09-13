package fr.zeddcara.portfoliotracker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.time.LocalDate

class CsvEditorActivity : Activity() {
    private lateinit var store: CsvStore
    private lateinit var filename: String
    private lateinit var table: CsvTable
    private lateinit var list: ListView
    private lateinit var titleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CsvStore(this).also { it.ensureSeeded() }
        filename = intent.getStringExtra("filename") ?: run { finish(); return }
        table = CsvUtils.read(store.file(filename))
        buildUi()
        refreshList()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(7,16,29)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8,8,8,8); setBackgroundColor(Color.rgb(11,25,42)) }
        val back = Button(this).apply { text = "←"; setOnClickListener { finish() } }
        titleView = TextView(this).apply { text = filename; textSize = 15f; setTextColor(Color.WHITE); setPadding(10,0,10,0) }
        val add = Button(this).apply { text = "+"; setOnClickListener { editRow(null) } }
        top.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        list = ListView(this).apply {
            dividerHeight = 1
            setOnItemClickListener { _, _, position, _ -> editRow(position) }
            setOnItemLongClickListener { _, _, position, _ -> confirmDelete(position); true }
        }
        val hint = TextView(this).apply {
            text = "Touchez une ligne pour la modifier · appui long pour supprimer · + pour ajouter"
            setTextColor(Color.rgb(155,176,201)); textSize = 10f; setPadding(12,7,12,7)
        }
        root.addView(top)
        root.addView(hint)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun refreshList() {
        val labels = table.rows.mapIndexed { idx, row ->
            val pairs = table.header.take(5).mapIndexed { i, h -> "$h=${row.getOrElse(i) { "" }}" }
            "${idx + 1}. ${pairs.joinToString("  ·  ")}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        titleView.text = "$filename (${table.rows.size})"
    }

    private fun editRow(index: Int?) {
        if (table.header.isEmpty()) return
        val original = if (index == null) MutableList(table.header.size) { "" } else table.rows[index].toMutableList()
        if (index == null) {
            val dateIdx = table.header.indexOf("date")
            if (dateIdx >= 0) original[dateIdx] = LocalDate.now().toString()
            val typeIdx = table.header.indexOf("type")
            if (typeIdx >= 0 && filename.contains("transactions")) original[typeIdx] = "BUY"
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,6,22,6) }
        val fields = mutableListOf<EditText>()
        table.header.forEachIndexed { i, h ->
            val label = TextView(this).apply { text = h; setTextColor(Color.rgb(190,210,232)); textSize = 11f; setPadding(0,8,0,0) }
            val edit = EditText(this).apply {
                setText(original.getOrElse(i) { "" })
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                inputType = when (h.lowercase()) {
                    "quantity", "price", "value", "amount_eur", "amount_usd", "target_1", "target_2", "stop", "min_score" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    "comment", "source_note", "aliases", "note", "description" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
                if (h in listOf("comment","source_note","aliases","note","description")) minLines = 2
            }
            container.addView(label); container.addView(edit); fields += edit
        }
        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle(if (index == null) "Ajouter une ligne" else "Modifier la ligne ${index + 1}")
            .setView(scroll)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val row = fields.map { it.text.toString().trim() }.toMutableList()
                        val error = validate(row)
                        if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        else {
                            if (index == null) table.rows.add(row) else table.rows[index] = row
                            CsvUtils.write(store.file(filename), table)
                            refreshList(); dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun validate(row: List<String>): String? {
        fun value(col: String) = table.header.indexOf(col).takeIf { it >= 0 }?.let { row.getOrElse(it) { "" } }.orEmpty()
        if (filename.contains("transactions")) {
            val type = value("type").uppercase()
            if (type !in setOf("BUY","SELL")) return "type doit être BUY ou SELL"
            if ((value("quantity").toDoubleOrNull() ?: 0.0) <= 0) return "quantity doit être > 0"
            if ((value("price").toDoubleOrNull() ?: 0.0) <= 0) return "price doit être > 0"
            if (value("ticker").isBlank()) return "ticker obligatoire"
            if (value("date").isBlank()) return "date obligatoire (AAAA-MM-JJ)"
        }
        return null
    }

    private fun confirmDelete(index: Int) {
        AlertDialog.Builder(this).setTitle("Supprimer la ligne ${index + 1} ?")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                table.rows.removeAt(index); CsvUtils.write(store.file(filename), table); refreshList()
            }.show()
    }
}
