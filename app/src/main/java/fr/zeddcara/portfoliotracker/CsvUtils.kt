package fr.zeddcara.portfoliotracker

import java.io.File
import java.nio.charset.StandardCharsets

data class CsvTable(val header: List<String>, val rows: MutableList<MutableList<String>>) {
    fun rowAsMap(index: Int): MutableMap<String, String> {
        val row = rows[index]
        return header.mapIndexed { i, h -> h to row.getOrElse(i) { "" } }.toMap().toMutableMap()
    }
}

object CsvUtils {
    fun parse(textRaw: String): CsvTable {
        val text = textRaw.removePrefix("\uFEFF")
        val records = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(field.toString()); field.setLength(0) }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    if (row.any { it.isNotEmpty() }) records.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) records.add(row)
        }
        if (records.isEmpty()) return CsvTable(emptyList(), mutableListOf())
        val header = records.first().map { it.trim() }
        val rows = records.drop(1).map { r ->
            MutableList(header.size) { idx -> r.getOrElse(idx) { "" } }
        }.toMutableList()
        return CsvTable(header, rows)
    }

    fun read(file: File): CsvTable = parse(file.readText(StandardCharsets.UTF_8))

    fun write(file: File, table: CsvTable) {
        file.parentFile?.mkdirs()
        val sb = StringBuilder("\uFEFF")
        sb.append(table.header.joinToString(",") { escape(it) }).append('\n')
        for (row in table.rows) {
            sb.append(table.header.indices.joinToString(",") { idx -> escape(row.getOrElse(idx) { "" }) }).append('\n')
        }
        file.writeText(sb.toString(), StandardCharsets.UTF_8)
    }

    private fun escape(value: String): String {
        val needs = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val v = value.replace("\"", "\"\"")
        return if (needs) "\"$v\"" else v
    }

    fun Double.pretty(maxDecimals: Int = 6): String {
        if (!this.isFinite()) return ""
        val s = "% .${maxDecimals}f".format(java.util.Locale.US, this).trim()
        return s.trimEnd('0').trimEnd('.')
    }
}
