package com.assistente99

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * O card com o R$/km. É usado tanto na janela flutuante sobre os apps (OverlayView)
 * quanto na prévia da tela "Personalizar o card", então os dois ficam sempre iguais.
 *
 * Tudo que o usuário personaliza (tamanho, fonte, transparência, campos) é lido de [Prefs]
 * a cada [bind], por isso as mudanças aparecem na próxima solicitação sem reiniciar nada.
 */
class RideCardView(context: Context) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    /** Desenha uma solicitação. */
    fun bind(ride: RideInfo, prefs: Prefs) {
        removeAllViews()

        val includePickup = prefs.includePickup
        val perKm = ride.perKm(includePickup)
        val net = perKm?.let { it - prefs.costPerKm }
        val shown = if (prefs.costPerKm > 0f) net else perKm
        val kmTotal = ride.totalKm(includePickup)

        applyFrame(
            prefs,
            when {
                shown == null -> COLOR_NEUTRAL
                shown >= prefs.goodPerKm -> COLOR_GOOD
                shown >= prefs.minPerKm -> COLOR_OK
                else -> COLOR_BAD
            }
        )

        var rows = 0

        if (prefs.showPerKm) {
            addLine(if (perKm != null) "R$ ${fmt(perKm)} / km" else "R$ -- / km", 26f, true, prefs)
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
            addLine(priceKm.joinToString(" • "), 15f, false, prefs)
            rows++
        }

        if (prefs.showTime) {
            val total = ride.totalMin(includePickup)
            if (total > 0) {
                var t = "Tempo: $total min"
                if (includePickup && ride.pickupMin != null && ride.tripMin != null) {
                    t += " (${ride.pickupMin} + ${ride.tripMin})"
                }
                addLine(t, 15f, false, prefs)
                rows++
            }
        }

        val money = mutableListOf<String>()
        if (prefs.showPerHour) ride.perHour(includePickup)?.let { money += "R$ ${fmt(it)} / hora" }
        if (prefs.showProfit && prefs.costPerKm > 0f && net != null) {
            money += "lucro R$ ${fmt(net * kmTotal)}"
        }
        if (money.isNotEmpty()) {
            addLine(money.joinToString(" • "), 14f, false, prefs)
            rows++
        }

        // Se o usuário desmarcou tudo, ainda assim mostra o R$/km (um card vazio não serve para nada).
        if (rows == 0) {
            addLine(if (perKm != null) "R$ ${fmt(perKm)} / km" else "R$ -- / km", 26f, true, prefs)
        }
    }

    /** Card de diagnóstico: leitura de tela sem corrida reconhecida. */
    fun bindDebug(message: String, prefs: Prefs) {
        removeAllViews()
        applyFrame(prefs, COLOR_NEUTRAL)
        addLine("Diagnóstico", 22f, true, prefs)
        addLine(message, 14f, false, prefs)
    }

    private fun applyFrame(prefs: Prefs, base: Int) {
        val s = prefs.cardScale
        val alpha = prefs.cardAlpha * 255 / 100
        background = GradientDrawable().apply {
            cornerRadius = dp(14f * s).toFloat()
            setColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)))
        }
        setPadding(dp(16f * s), dp(10f * s), dp(16f * s), dp(10f * s))
    }

    private fun addLine(text: String, sizeSp: Float, bold: Boolean, prefs: Prefs) {
        addView(TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * prefs.cardScale)
            typeface = fontFor(prefs.cardFont, bold)
        })
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    companion object {
        private const val COLOR_GOOD = 0xFF137A3A.toInt()
        private const val COLOR_OK = 0xFFB7791F.toInt()
        private const val COLOR_BAD = 0xFFB3261E.toInt()
        private const val COLOR_NEUTRAL = 0xFF333333.toInt()

        val FONT_NAMES = listOf("Padrão", "Condensada", "Serifada", "Monoespaçada", "Casual")

        /** Nomes das 9 posições, na mesma ordem do índice salvo em [Prefs.cardPosition]. */
        val POSITION_NAMES = listOf(
            "Topo, centro", "Topo, esquerda", "Topo, direita",
            "Meio da tela", "Meio, esquerda", "Meio, direita",
            "Base, centro", "Base, esquerda", "Base, direita"
        )

        fun fontFor(code: Int, bold: Boolean): Typeface {
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            return when (code) {
                1 -> Typeface.create("sans-serif-condensed", style)
                2 -> Typeface.create(Typeface.SERIF, style)
                3 -> Typeface.create(Typeface.MONOSPACE, style)
                4 -> Typeface.create("casual", style)
                else -> Typeface.create(Typeface.DEFAULT, style)
            }
        }
    }
}
