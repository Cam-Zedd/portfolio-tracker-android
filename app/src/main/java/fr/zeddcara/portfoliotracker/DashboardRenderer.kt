package fr.zeddcara.portfoliotracker

import java.util.Locale
import kotlin.math.abs

object DashboardRenderer {
    fun render(p: PortfolioSnapshot): String {
        val allPositions = p.accounts.flatMap { it.positions }
        val accountLabels = p.accounts.map { it.label }
        val accountValues = p.accounts.map { it.totalEur }
        val heatLabels = allPositions.map { it.ticker }
        val heatValues = allPositions.map { it.valueEur.coerceAtLeast(0.0001) }
        val heatText = allPositions.map { "${it.account}<br>${pct(it.dayPct)} séance<br>${pct(it.unrealizedPct)} latent" }
        val heatColors = allPositions.map { it.dayPct * 100.0 }
        val typeGroups = allPositions.groupBy { it.assetType.ifBlank { "Autre" } }
        val typeLabels = typeGroups.keys.toList()
        val typeValues = typeGroups.values.map { list -> list.sumOf { it.valueEur } }

        val warnings = if (p.warnings.isEmpty()) "" else """
          <div class='warnbox'><strong>Mode secours / avertissements</strong><br>${p.warnings.joinToString("<br>") { esc(it) }}</div>
        """.trimIndent()

        val accountsHtml = p.accounts.joinToString("\n") { accountSection(it) }
        val transactionsNote = "Les fichiers d’entrée restent les CSV V5.7. Les PRU/FX historiques spéciaux sont lus dans _transaction_overrides.csv."

        return """
<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>Portfolio Tracker Android</title>
<script src="plotly.min.js"></script>
<style>
:root{--bg:#07101d;--panel:#0e1d31;--panel2:#10243d;--line:#244463;--text:#eaf2ff;--muted:#9bb0c9;--pos:#34d399;--neg:#fb7185;--warn:#fbbf24;--accent:#60a5fa}
*{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#07101d,#0a1626);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;padding:14px}.wrap{max-width:1450px;margin:auto}.top{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;flex-wrap:wrap}.top h1{font-size:25px;margin:4px 0}.sub{color:var(--muted);font-size:12px}.pill{border:1px solid var(--line);border-radius:999px;padding:6px 10px;background:#0b1b2d;color:#bcd4ef;font-size:11px}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:14px 0}.card{background:linear-gradient(180deg,var(--panel2),var(--panel));border:1px solid var(--line);border-radius:16px;padding:14px;box-shadow:0 8px 25px rgba(0,0,0,.16)}.label{font-size:11px;color:var(--muted);text-transform:uppercase;letter-spacing:.5px}.kpi{font-size:24px;font-weight:800;margin-top:5px}.pos{color:var(--pos)}.neg{color:var(--neg)}.section{margin-top:24px}.section h2{font-size:21px;margin:0 0 10px}.section h3{font-size:15px;color:#cfe2ff}.charts{display:grid;grid-template-columns:1fr 1fr;gap:10px}.plotcard{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:8px;min-height:330px}.plot{width:100%;height:320px}.tablewrap{overflow-x:auto;border:1px solid var(--line);border-radius:14px;background:var(--panel)}table{border-collapse:collapse;width:100%;min-width:920px;font-size:12px}th,td{padding:10px;border-bottom:1px solid #1c3854;text-align:right;white-space:nowrap}th{background:#0b192a;color:#9db6d3;font-size:10px;text-transform:uppercase}th:first-child,td:first-child{text-align:left}.name{color:var(--muted);font-size:10px}.warnbox{background:rgba(251,191,36,.08);border:1px solid rgba(251,191,36,.35);color:#fde68a;padding:10px 12px;border-radius:12px;font-size:11px;margin:10px 0}.alert{font-size:10px;padding:3px 7px;border-radius:999px;border:1px solid var(--line);margin-left:5px;color:#c7dbef}.footer{color:var(--muted);font-size:10px;margin:25px 0 10px;line-height:1.5}
@media(max-width:900px){body{padding:9px}.grid{grid-template-columns:1fr 1fr}.charts{grid-template-columns:1fr}.kpi{font-size:20px}.plotcard{min-height:290px}.plot{height:280px}}@media(max-width:520px){.grid{grid-template-columns:1fr}.top h1{font-size:21px}}
</style></head><body><div class="wrap">
<div class="top"><div><h1>Mon portefeuille — Android</h1><div class="sub">Calcul natif à partir des fichiers V5.7 · généré le ${esc(p.generatedAt)}</div></div><div class="pill">EUR/USD ${fmt(p.eurUsd,4)}</div></div>
$warnings
<div class="grid">
${kpiCard("Total global", eur(p.totalEur), "")}
${kpiCard("Variation séance", signedEur(p.dayPnlEur), pct(p.dayPct), state(p.dayPnlEur))}
${kpiCard("PV/MV latente", signedEur(p.unrealizedEur), "positions ouvertes", state(p.unrealizedEur))}
${kpiCard("Cash global", eur(p.cashEur), "converti en EUR")}
</div>
<div class="charts">
<div class="plotcard"><div class="label">Répartition par compte</div><div id="accountsPie" class="plot"></div></div>
<div class="plotcard"><div class="label">Répartition par classe d’actifs</div><div id="typesPie" class="plot"></div></div>
</div>
<div class="section"><h2>Heatmap positions</h2><div class="plotcard"><div id="heatmap" class="plot"></div></div></div>
$accountsHtml
<div class="footer">${esc(transactionsNote)}<br>Les cours sont récupérés via Yahoo Finance. Si un cours est indisponible, l’app conserve la position et utilise le dernier prix de transaction comme secours.</div>
</div>
<script>
const commonLayout={paper_bgcolor:'rgba(0,0,0,0)',plot_bgcolor:'rgba(0,0,0,0)',font:{color:'#dcecff',size:11},margin:{l:8,r:8,t:20,b:8},showlegend:true,legend:{font:{size:10}}};
Plotly.newPlot('accountsPie',[{labels:${jsStrings(accountLabels)},values:${jsNumbers(accountValues)},type:'pie',hole:.52,textinfo:'label+percent',hovertemplate:'%{label}<br>%{value:.2f} €<extra></extra>'}],commonLayout,{displayModeBar:false,responsive:true});
Plotly.newPlot('typesPie',[{labels:${jsStrings(typeLabels)},values:${jsNumbers(typeValues)},type:'pie',hole:.52,textinfo:'label+percent',hovertemplate:'%{label}<br>%{value:.2f} €<extra></extra>'}],commonLayout,{displayModeBar:false,responsive:true});
Plotly.newPlot('heatmap',[{labels:${jsStrings(heatLabels)},parents:${jsStrings(heatLabels.map { "" })},values:${jsNumbers(heatValues)},text:${jsStrings(heatText)},marker:{colors:${jsNumbers(heatColors)},colorscale:'RdYlGn',cmid:0},type:'treemap',textinfo:'label+text',hovertemplate:'%{label}<br>%{value:.2f} €<br>%{text}<extra></extra>'}],{...commonLayout,margin:{l:4,r:4,t:6,b:4}},{displayModeBar:false,responsive:true});
</script></body></html>
        """.trimIndent()
    }

    private fun accountSection(a: AccountSummary): String {
        val curr = a.currency
        val rows = a.positions.joinToString("\n") { posRow(it, curr) }
        return """
<div class="section" id="${a.key}"><h2>${esc(a.label)}</h2>
<div class="grid">
${kpiCard("Valeur actuelle", money(a.total, curr), "Cash ${money(a.cash,curr)}")}
${kpiCard("Variation séance", signedMoney(a.dayPnl,curr), pct(a.dayPct), state(a.dayPnl))}
${kpiCard("PV/MV latente", signedMoney(a.unrealized,curr), pct(a.unrealizedPct), state(a.unrealized))}
${kpiCard("PV/MV réalisée", signedMoney(a.realized,curr), "depuis le journal V5.7", state(a.realized))}
</div>
<div class="tablewrap"><table><thead><tr><th>Titre</th><th>Qté</th><th>PRU</th><th>Cours</th><th>Valeur</th><th>Jour</th><th>PV/MV</th><th>Poids</th><th>Objectifs</th></tr></thead><tbody>$rows</tbody></table></div>
</div>
        """.trimIndent()
    }

    private fun posRow(p: PositionView, accountCurrency: String): String {
        val targets = buildList {
            p.target1?.let { add("T1 ${fmt(it,3)}") }
            p.target2?.let { add("T2 ${fmt(it,3)}") }
            p.stop?.let { add("STOP ${fmt(it,3)}") }
        }.joinToString(" · ").ifBlank { "—" }
        return """
<tr><td><strong>${esc(p.ticker)}</strong><div class="name">${esc(p.name)} · ${esc(p.assetType)}</div></td><td>${fmt(p.qty,5)}</td><td>${fmt(p.pruNative,4)} ${esc(p.pruCurrency)}</td><td>${fmt(p.quote.price,4)} ${esc(p.quote.currency)}${if (p.quote.fallback) " <span class='alert'>secours</span>" else ""}</td><td>${money(p.valueAccount,accountCurrency)}</td><td class="${state(p.dayPnlAccount)}">${signedMoney(p.dayPnlAccount,accountCurrency)}<div class="name">${pct(p.dayPct)}</div></td><td class="${state(p.unrealizedAccount)}">${signedMoney(p.unrealizedAccount,accountCurrency)}<div class="name">${pct(p.unrealizedPct)}</div></td><td>${eur(p.valueEur)}</td><td>${esc(targets)}</td></tr>
        """.trimIndent()
    }

    private fun kpiCard(label: String, value: String, sub: String, cls: String = "") =
        "<div class='card'><div class='label'>${esc(label)}</div><div class='kpi $cls'>$value</div>${if(sub.isNotBlank()) "<div class='sub'>${esc(sub)}</div>" else ""}</div>"

    private fun state(v: Double) = if (v > 1e-9) "pos" else if (v < -1e-9) "neg" else ""
    private fun money(v: Double, c: String) = if (c == "USD") usd(v) else eur(v)
    private fun signedMoney(v: Double, c: String) = if (c == "USD") signedUsd(v) else signedEur(v)
    private fun eur(v: Double) = String.format(Locale.FRANCE, "%,.2f €", v)
    private fun usd(v: Double) = String.format(Locale.US, "\$%,.2f", v)
    private fun signedEur(v: Double) = (if (v >= 0) "+" else "") + eur(v)
    private fun signedUsd(v: Double) = (if (v >= 0) "+" else "") + usd(v)
    private fun pct(v: Double) = String.format(Locale.FRANCE, "%+.2f %%", v * 100.0)
    private fun fmt(v: Double, d: Int) = String.format(Locale.US, "%.${d}f", v).trimEnd('0').trimEnd('.')
    private fun esc(s: String): String = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun jsStrings(values: List<String>) = values.joinToString(prefix = "[", postfix = "]") { jsQuote(it) }
    private fun jsNumbers(values: List<Double>) = values.joinToString(prefix = "[", postfix = "]") { if (it.isFinite()) it.toString() else "0" }
    private fun jsQuote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
