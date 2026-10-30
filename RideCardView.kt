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

    // Cores do tema do card (claro ou escuro), definidas a cada desenho.
    private var dark = false
    private val INK get() = if (dark) 0xFFF3F4F6.toInt() else 0xFF111827.toInt()
    private val MUTED get() = if (dark) 0xFFE5E7EB.toInt() else 0xFF374151.toInt()
    private val GOOD get() = if (dark) 0xFF4CD07D.toInt() else 0xFF15803D.toInt()
    private val OK get() = if (dark) 0xFFF2B84B.toInt() else 0xFFB45309.toInt()
    private val BAD get() = if (dark) 0xFFFF6B63.toInt() else 0xFFB91C1C.toInt()

    init {
        orientation = VERTICAL
    }

    private fun pickTheme(prefs: Prefs) {
        dark = when (prefs.cardTheme) {
            1 -> false
            2 -> true
            else -> Pal.dark(context)
        }
    }

    /** Desenha uma solicitação. [source] é "Uber" ou "99" (define o selo e a cor da borda). */
    fun bind(ride: RideInfo, prefs: Prefs, source: String? = null, good: Boolean = false) {
        removeAllViews()
        pickTheme(prefs)

        val includePickup = prefs.includePickup
        val perKm = ride.perKm(includePickup)
        val kmTotal = ride.totalKm(includePickup)
        val band = bandOf(perKm, prefs)

        applyFrame(prefs, source, good)
        addHeader(source, band, prefs, good)

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

        val passenger = mutableListOf<String>()
        if (prefs.showRating && ride.rating != null) passenger += "★ ${fmt(ride.rating)}"
        if (prefs.showRides && ride.rides != null) passenger += "${ride.rides} corridas"
        if (passenger.isNotEmpty()) {
            addLine(passenger.joinToString("  •  "), 14f, true, MUTED, prefs)
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
        pickTheme(prefs)
        applyFrame(prefs, source)
        addHeader(source, null, prefs)
        val includePickup = prefs.includePickup
        for (ride in rides.take(MAX_ROWS)) {
            val perKm = ride.perKm(includePickup)
            val color = bandColor(bandOf(perKm, prefs))
            val label = if (perKm != null) "R$ ${fmt(perKm)}/km" else "R$ --/km"
            var rest = "   R$ ${fmt(ride.price)}  •  ${fmt(ride.totalKm(includePickup))} km"
            if (prefs.showRating && ride.rating != null) rest += "  •  ★ ${fmt(ride.rating)}"
            if (prefs.showRides && ride.rides != null) rest += "  •  ${ride.rides}"
            val span = SpannableString(label + rest)
            span.setSpan(ForegroundColorSpan(color), 0, label.length, 0)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, label.length, 0)
            addView(TextView(context).apply {
                text = span
                legible(this)
                setTextColor(INK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * prefs.cardScale)
                setPadding(0, dp(3f * prefs.cardScale), 0, dp(3f * prefs.cardScale))
            })
        }
    }

    /** Card de diagnóstico: leitura de tela sem corrida reconhecida. */
    fun bindDebug(message: String, prefs: Prefs) {
        removeAllViews()
        pickTheme(prefs)
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
    private fun addHeader(source: String?, band: Int?, prefs: Prefs, good: Boolean = false) {
        if (source == null && band == null) return
        val s = prefs.cardScale
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (source != null) {
            val uberDark = dark && source.equals("Uber", ignoreCase = true)
            row.addView(
                pill(source.uppercase(Locale.ROOT), if (uberDark) Color.WHITE else brandColor(source),
                    if (uberDark) Color.BLACK else Color.WHITE, 13f, prefs)
            )
        }
        if (band != null && band >= 0) {
            val label = if (good) "ESSA É BOA!" else when (band) { 2 -> "BOA"; 1 -> "MÉDIA"; else -> "BAIXA" }
            val labelColor = if (good) 0xFF16A34A.toInt() else bandColor(band)
            row.addView(pill(label, labelColor, Color.WHITE, if (good) 14f else 12f, prefs), LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { if (source != null) marginStart = dp(8f * s) })
        }
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
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

    private fun applyFrame(prefs: Prefs, source: String?, good: Boolean = false) {
        val s = prefs.cardScale
        val alpha = prefs.cardAlpha * 255 / 100
        background = GradientDrawable().apply {
            cornerRadius = dp(16f * s).toFloat()
            // Corrida boa: fundo com um tom de verde, para se destacar.
            val fill = when {
                good && dark -> intArrayOf(0x10, 0x3B, 0x22)
                good -> intArrayOf(0xE3, 0xF8, 0xEA)
                dark -> intArrayOf(0x1A, 0x1D, 0x24)
                else -> intArrayOf(255, 255, 255)
            }
            setColor(Color.argb(alpha, fill[0], fill[1], fill[2]))
            // Borda preta da Uber some num card escuro: lá ela vira branca.
            val border = when {
                source == null -> 0xFF9CA3AF.toInt()
                dark && source.equals("Uber", ignoreCase = true) -> 0xFFFFFFFF.toInt()
                else -> brandColor(source)
            }
            // A borda também segue a opacidade escolhida.
            setStroke(dp(3.5f * s), Color.argb(alpha, Color.red(border), Color.green(border), Color.blue(border)))
        }
        setPadding(dp(14f * s), dp(10f * s), dp(14f * s), dp(10f * s))
    }

    /** Contorno suave no texto: continua legível mesmo com o fundo do card bem transparente. */
    private fun legible(tv: TextView) {
        val glow = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        tv.setShadowLayer(dp(4f).toFloat(), 0f, dp(1f).toFloat(), glow)
    }

    private fun addLine(text: String, sizeSp: Float, bold: Boolean, color: Int, prefs: Prefs) {
        addView(TextView(context).apply {
            this.text = text
            legible(this)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * prefs.cardScale)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        })
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    companion object {
        private const val MAX_ROWS = 5

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
