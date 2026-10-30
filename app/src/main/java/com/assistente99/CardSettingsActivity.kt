package com.assistente99

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * Personalizar o card: tamanho, transparência, tema, quais informações aparecem, onde ele fica
 * e por quanto tempo. Cada mudança é salva na hora e a prévia no topo atualiza junto.
 */
class CardSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var previewHolder: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var positionLabel: TextView

    // Exemplo com os números de um card real da Uber (R$ 12,70, 2,9 km + 8,1 km, 8 + 12 min).
    private val sample = RideInfo(12.70, 2.9, 8.1, 8, 12, 1.15, rating = 4.87, rides = 395)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        prefs = Prefs(this)
        content = Ui.screen(this, "Personalizar o card", "Tamanho, tema, campos, posição e tempo na tela")
        build()
    }

    private fun build() {
        content.removeAllViews()
        val muted = Pal.muted(this)

        // ---- Prévia ----
        content.addView(Ui.sectionTitle(this, "PRÉVIA"))
        previewHolder = FrameLayout(this).apply {
            background = Ui.round(this@CardSettingsActivity, 0xFF7A8794.toInt(), 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        content.addView(previewHolder)
        refreshPreview()

        // ---- Aparência ----
        content.addView(Ui.sectionTitle(this, "APARÊNCIA"))
        val look = Ui.card(this)
        val sizeText = Ui.text(this, "Tamanho: ${prefs.cardScalePercent}%", 14f, muted)
        look.addView(sizeText)
        look.addView(Ui.seek(this, 140, prefs.cardScalePercent - 60) { p ->
            prefs.cardScalePercent = p + 60
            sizeText.text = "Tamanho: ${prefs.cardScalePercent}%"
            refreshPreview()
        })
        val alphaText = Ui.text(this, "Opacidade: ${prefs.cardAlpha}% (menor = mais transparente)", 14f, muted)
            .apply { setPadding(0, dp(10), 0, 0) }
        look.addView(alphaText)
        look.addView(Ui.seek(this, 90, prefs.cardAlpha - 10) { p ->
            prefs.cardAlpha = p + 10
            alphaText.text = "Opacidade: ${prefs.cardAlpha}% (menor = mais transparente)"
            refreshPreview()
        })
        look.addView(Ui.fieldLabel(this, "Tema do card"))
        look.addView(Ui.segmented(this, listOf("Do app", "Claro", "Escuro"), prefs.cardTheme) { i ->
            prefs.cardTheme = i
            build()
        })
        content.addView(look)

        // ---- O que mostrar ----
        content.addView(Ui.sectionTitle(this, "O QUE MOSTRAR NO CARD"))
        val fields = Ui.card(this)
        fields.addView(check("Valor por km", "R$/km", prefs.showPerKm) { prefs.showPerKm = it })
        fields.addView(check("Valor da corrida", "Preço em R$", prefs.showPrice) { prefs.showPrice = it })
        fields.addView(check("Distância", "Total em km", prefs.showKm) { prefs.showKm = it })
        fields.addView(check("Nota do passageiro", "Estrelas, quando o app mostra", prefs.showRating) { prefs.showRating = it })
        fields.addView(check("Corridas do passageiro", "Quantidade, quando o app mostra", prefs.showRides) { prefs.showRides = it })
        fields.addView(check("Valor por hora", "R$/h", prefs.showPerHour) { prefs.showPerHour = it })
        fields.addView(check("Tempo total", "Em minutos", prefs.showTime) { prefs.showTime = it })
        content.addView(fields)

        // ---- Tempo na tela ----
        content.addView(Ui.sectionTitle(this, "TEMPO NA TELA"))
        val timing = Ui.card(this)
        val secText = Ui.text(this, secondsLabel(), 14f, muted)
        timing.addView(secText)
        timing.addView(Ui.seek(this, MAX_SECONDS - MIN_SECONDS, prefs.cardSeconds - MIN_SECONDS) { p ->
            prefs.cardSeconds = p + MIN_SECONDS
            secText.text = secondsLabel()
        })
        content.addView(timing)

        // ---- Posição ----
        content.addView(Ui.sectionTitle(this, "POSIÇÃO NA TELA"))
        val pos = Ui.card(this)
        positionLabel = Ui.text(this, "", 14f, muted)
        pos.addView(positionLabel)
        pos.addView(Ui.button(this, "Posicionar livremente (arrastar o card)") {
            val svc = RideAccessibilityService.instance
            if (svc == null) {
                needService()
            } else {
                svc.startPositioning()
                Toast.makeText(
                    this,
                    "Arraste o card e toque em Salvar posição. Dá para abrir a Uber ou a 99 antes, para ver o lugar real.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        pos.addView(Ui.outlineButton(this, "Voltar à posição padrão (topo)") {
            prefs.cardPosition = 0
            prefs.cardX = -1
            prefs.cardY = -1
            refreshPositionLabel()
        })
        pos.addView(Ui.text(
            this,
            "Dica: na Uber o app lê a imagem da tela, e o que o card cobrir não é lido. " +
                "Deixe o card onde ele não tape o valor e os km da solicitação (topo ou uma borda, " +
                "e use um tamanho menor se precisar). Tapar o card da 99 não atrapalha, porque ele é lido por texto.",
            13f, muted
        ).apply { setPadding(0, dp(10), 0, 0) })
        content.addView(pos)
        refreshPositionLabel()

        // ---- Ações ----
        content.addView(Ui.sectionTitle(this, "TESTAR"))
        content.addView(Ui.button(this, "Testar sobre outros apps (${prefs.cardSeconds} segundos)") {
            val svc = RideAccessibilityService.instance
            if (svc == null) {
                needService()
            } else {
                svc.previewOverlay()
                Toast.makeText(
                    this,
                    "Volte para a tela inicial ou para a Uber: o card aparece por ${prefs.cardSeconds} s.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        content.addView(Ui.button(this, "Restaurar padrão", Ui.GRAY) {
            prefs.resetCard()
            build()
        })
    }

    private fun secondsLabel() =
        "O card fica ${prefs.cardSeconds} segundos na tela depois que a solicitação some"

    private fun needService() {
        Toast.makeText(this, "Ative o serviço de acessibilidade primeiro (tela principal).", Toast.LENGTH_LONG).show()
    }

    private fun refreshPreview() {
        previewHolder.removeAllViews()
        val card = RideCardView(this)
        card.bind(sample, prefs, "Uber")
        previewHolder.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    override fun onResume() {
        super.onResume()
        if (::positionLabel.isInitialized) refreshPositionLabel()
    }

    private fun refreshPositionLabel() {
        val custom = prefs.cardX >= 0 && prefs.cardY >= 0
        positionLabel.text = if (custom) "Posição: livre (a que você arrastou)" else "Posição: padrão (topo da tela)"
    }

    private fun check(title: String, sub: String, checked: Boolean, onToggle: (Boolean) -> Unit): SwitchCompat =
        Ui.switchRow(this, title, sub, checked).apply {
            setOnCheckedChangeListener { _, value ->
                onToggle(value)
                refreshPreview()
            }
        }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    companion object {
        const val MIN_SECONDS = 3
        const val MAX_SECONDS = 12
    }
}
