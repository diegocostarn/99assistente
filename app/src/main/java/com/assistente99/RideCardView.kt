package com.assistente99

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * O card com o R$/km. É usado tanto na janela flutuante sobre os apps (OverlayView)
 * quanto na prévia da tela "Personalizar o card", então os dois ficam sempre iguais.
 *
 * Corpo branco, com borda na cor do app de origem (preta na Uber, laranja na 99), um selo com o nome
 * do app e o R$/km grande, na cor da faixa (verde, amarelo ou vermelho).
 * O tamanho, a transparência e os campos vêm de [Prefs] a cada [bind].
 */
class RideCardView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    /** Desenha uma solicitação. [source] é "Uber" ou "99" (define o selo e a cor da borda). */
    fun bind(ride: RideInfo, prefs: Prefs, source: String? = null) {
        removeAllViews()

        val includePickup = prefs.includePickup
        val perKm = ride.perKm(includePickup)
        val kmTotal = ride.totalKm(includePickup)
        val band = bandOf(perKm, prefs)

        applyFrame(prefs, source)
        addHeader(source, band, prefs)

        var rows = 0
        if (prefs.showPerKm) {
            addLine(if (perKm != null) "R$ ${fmt(perKm)}/km" else "R$ --/km", 30f, true, bandColor(band), prefs)
            rows++
        }

        // Valor da corrida e distância na mesma linha.
        val priceKm = mutableListOf<String>()
        if (prefs.showPrice) priceKm += "R$ ${fmt(ride.price)}"
        if (prefs.showKm) {
            var km = "${fmt(kmTotal)} km"
            if (includePickup && ride.pickupKm != null) {
                km += " (${fmt(ride.pickupKm)} + ${fmt(ride.tripKm ?: 0.0)})"
            }
            priceKm += km
        }
        if (priceKm.isNotEmpty()) {
            addLine(priceKm.joinToString("  •  "), 16f, true, INK, prefs)
            rows++
        }

        val details = mutableListOf<String>()
        if (prefs.showTime) {
            val total = ride.totalMin(includePickup)
            if (total > 0) {
                var t = "$total min"
                if (includePickup && ride.pickupMin != null && ride.tripMin != null) {
                    t += " (${ride.pickupMin} + ${ride.tripMin})"
                }
                details += t
            }
        }
        if (prefs.showPerHour) ride.perHour(includePickup)?.let { details += "R$ ${fmt(it)}/h" }
        if (details.isNotEmpty()) {
            addLine(details.joinToString("  •  "), 14f, false, MUTED, prefs)
            rows++
        }

        // Se o usuário desmarcou tudo, ainda assim mostra o R$/km (um card vazio não serve para nada).
        if (rows == 0) {
            addLine(if (perKm != null) "R$ ${fmt(perKm)}/km" else "R$ --/km", 30f, true, bandColor(band), prefs)
        }
    }

    /**
     * Várias corridas ao mesmo tempo (lista "Solicitações" da 99): uma linha por corrida, com o R$/km
     * colorido pela sua faixa e o valor e os km ao lado.
     */
    fun bindMany(rides: List<RideInfo>, prefs: Prefs, source: String? = null) {
        removeAllViews()
        applyFrame(prefs, source)
        addHeader(source, null, prefs)
        val includePickup = prefs.includePickup
        for (ride in rides.take(MAX_ROWS)) {
            val perKm = ride.perKm(includePickup)
            val color = bandColor(bandOf(perKm, prefs))
            val label = if (perKm != null) "R$ ${fmt(perKm)}/km" else "R$ --/km"
            val rest = "   R$ ${fmt(ride.price)}  •  ${fmt(ride.totalKm(includePickup))} km"
            val span = SpannableString(label + rest)
            span.setSpan(ForegroundColorSpan(color), 0, label.length, 0)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, 0)
            addView(TextView(context).apply {
                text = span
                setTextColor(INK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * prefs.cardScale)
                setPadding(0, dp(3f * prefs.cardScale), 0, dp(3f * prefs.cardScale))
            })
        }
    }

    /** Card de diagnóstico: leitura de tela sem corrida reconhecida. */
    fun bindDebug(message: String, prefs: Prefs) {
        removeAllViews()
        applyFrame(prefs, null)
        addLine("Diagnóstico", 20f, true, INK, prefs)
        addLine(message, 14f, false, MUTED, prefs)
    }

    // ---------- Peças ----------

    /** Faixa do R$/km: 2 boa, 1 média, 0 baixa, -1 sem valor. */
    private fun bandOf(perKm: Double?, prefs: Prefs): Int = when {
        perKm == null -> -1
        perKm >= prefs.goodPerKm -> 2
        perKm >= prefs.minPerKm -> 1
        else -> 0
    }

    private fun bandColor(band: Int): Int = when (band) {
        2 -> GOOD
        1 -> OK
        0 -> BAD
        else -> MUTED
    }

    /** Selo do app (Uber preto, 99 laranja) à esquerda e a faixa ("Boa", "Média", "Baixa") à direita. */
    private fun addHeader(source: String?, band: Int?, prefs: Prefs) {
        if (source == null && band == null) return
        val s = prefs.cardScale
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (source != null) {
            row.addView(pill(source.uppercase(Locale.ROOT), brandColor(source), Color.WHITE, 13f, prefs))
        }
        row.addView(View(context), LayoutParams(0, 1, 1f))
        if (band != null && band >= 0) {
            val label = when (band) { 2 -> "BOA"; 1 -> "MÉDIA"; else -> "BAIXA" }
            row.addView(pill(label, bandColor(band), Color.WHITE, 12f, prefs))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4f * s)
        })
    }

    private fun pill(text: String, bg: Int, fg: Int, sizeSp: Float, prefs: Prefs): TextView {
        val s = prefs.cardScale
        return TextView(context).apply {
            this.text = text
            setTextColor(fg)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.06f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * s)
            setPadding(dp(10f * s), dp(3f * s), dp(10f * s), dp(3f * s))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f * s).toFloat()
                setColor(bg)
            }
        }
    }

    private fun applyFrame(prefs: Prefs, source: String?) {
        val s = prefs.cardScale
        val alpha = prefs.cardAlpha * 255 / 100
        background = GradientDrawable().apply {
            cornerRadius = dp(16f * s).toFloat()
            setColor(Color.argb(alpha, 255, 255, 255))
            setStroke(dp(3.5f * s), if (source != null) brandColor(source) else 0xFF9CA3AF.toInt())
        }
        setPadding(dp(14f * s), dp(10f * s), dp(14f * s), dp(10f * s))
    }

    private fun addLine(text: String, sizeSp: Float, bold: Boolean, color: Int, prefs: Prefs) {
        addView(TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * prefs.cardScale)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        })
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    companion object {
        private const val MAX_ROWS = 5
        private val INK = 0xFF111827.toInt()
        private val MUTED = 0xFF4B5563.toInt()
        private val GOOD = 0xFF15803D.toInt()
        private val OK = 0xFFB45309.toInt()
        private val BAD = 0xFFB91C1C.toInt()

        /** Preto para a Uber, laranja para a 99. */
        fun brandColor(source: String): Int =
            if (source.equals("Uber", ignoreCase = true)) 0xFF000000.toInt() else 0xFFFF7A00.toInt()

        /** Nomes das 9 posições, na mesma ordem do índice salvo em [Prefs.cardPosition]. */
        val POSITION_NAMES = listOf(
            "Topo, centro", "Topo, esquerda", "Topo, direita",
            "Meio da tela", "Meio, esquerda", "Meio, direita",
            "Base, centro", "Base, esquerda", "Base, direita"
        )
    }
}
