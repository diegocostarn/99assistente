package com.assistente99

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Personalizar o card: tamanho, fonte, transparência, quais informações aparecem e onde ele fica na tela.
 * Cada mudança é salva na hora e a prévia no topo atualiza junto.
 */
class CardSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var previewHolder: FrameLayout
    private lateinit var content: LinearLayout

    // Exemplo com os números de um card real da Uber (R$ 12,70, 2,9 km + 8,1 km, 8 + 12 min).
    private val sample = RideInfo(12.70, 2.9, 8.1, 8, 12, 1.15)

    private val positionButtons = ArrayList<Button>()
    private lateinit var positionLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Personalizar o card"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        prefs = Prefs(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFFF2F4F7.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(content)
        })
        build()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun build() {
        content.removeAllViews()
        positionButtons.clear()

        // ---- Prévia ----
        content.addView(label("Prévia (como o card aparece sobre a Uber/99)"))
        previewHolder = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#7A8794"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        content.addView(previewHolder)
        refreshPreview()

        // ---- Tamanho ----
        val sizeText = TextView(this).apply { textSize = 14f }
        content.addView(label("Tamanho"))
        content.addView(sizeText)
        content.addView(SeekBar(this).apply {
            max = 140 // 60% a 200%
            progress = prefs.cardScalePercent - 60
            sizeText.text = "${prefs.cardScalePercent}%"
            setOnSeekBarChangeListener(onChange { p ->
                prefs.cardScalePercent = p + 60
                sizeText.text = "${prefs.cardScalePercent}%"
                refreshPreview()
            })
        })

        // ---- Opacidade ----
        val alphaText = TextView(this).apply { textSize = 14f }
        content.addView(label("Opacidade (menor = mais transparente)"))
        content.addView(alphaText)
        content.addView(SeekBar(this).apply {
            max = 70 // 30% a 100%
            progress = prefs.cardAlpha - 30
            alphaText.text = "${prefs.cardAlpha}%"
            setOnSeekBarChangeListener(onChange { p ->
                prefs.cardAlpha = p + 30
                alphaText.text = "${prefs.cardAlpha}%"
                refreshPreview()
            })
        })

        // ---- Fonte ----
        content.addView(label("Fonte"))
        content.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CardSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                RideCardView.FONT_NAMES
            )
            setSelection(prefs.cardFont.coerceIn(0, RideCardView.FONT_NAMES.size - 1))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    if (prefs.cardFont != pos) {
                        prefs.cardFont = pos
                        refreshPreview()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        })

        // ---- O que mostrar ----
        content.addView(label("O que mostrar no card"))
        content.addView(check("Valor por km (R$/km)", prefs.showPerKm) { prefs.showPerKm = it })
        content.addView(check("Valor da corrida (R$)", prefs.showPrice) { prefs.showPrice = it })
        content.addView(check("Distância (km)", prefs.showKm) { prefs.showKm = it })
        content.addView(check("Valor por hora (R$/h)", prefs.showPerHour) { prefs.showPerHour = it })
        content.addView(check("Tempo total (min)", prefs.showTime) { prefs.showTime = it })

        // ---- Posição ----
        content.addView(label("Posição na tela"))
        positionLabel = TextView(this).apply { textSize = 14f }
        content.addView(positionLabel)
        // Ordem visual da grade 3x3 -> índice salvo (ver RideCardView.POSITION_NAMES).
        val grid = listOf(listOf(1, 0, 2), listOf(4, 3, 5), listOf(7, 6, 8))
        val arrows = mapOf(1 to "↖", 0 to "↑", 2 to "↗", 4 to "←", 3 to "●", 5 to "→", 7 to "↙", 6 to "↓", 8 to "↘")
        for (row in grid) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (index in row) {
                val b = Button(this).apply {
                    tag = index
                    text = arrows[index]
                    textSize = 20f
                    setOnClickListener {
                        prefs.cardPosition = index
                        refreshPositionButtons()
                    }
                }
                positionButtons += b
                line.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            content.addView(line)
        }
        refreshPositionButtons()
        content.addView(TextView(this).apply {
            text = "Dica: na Uber o app lê a imagem da tela, e o que o card cobrir não é lido. " +
                "Deixe o card onde ele não tape o valor e os km da solicitação (topo ou uma borda, " +
                "e use um tamanho menor se precisar). Tapar o card da 99 não atrapalha, porque ele é lido por texto."
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        })

        // ---- Ações ----
        content.addView(Button(this).apply {
            text = "Testar sobre outros apps (6 segundos)"
            setOnClickListener {
                val svc = RideAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(
                        this@CardSettingsActivity,
                        "Ative o serviço de acessibilidade primeiro (tela principal).",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    svc.previewOverlay()
                    Toast.makeText(
                        this@CardSettingsActivity,
                        "Volte para a tela inicial ou para a Uber: o card aparece por 6 s.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        content.addView(Button(this).apply {
            text = "Restaurar padrão"
            setOnClickListener {
                prefs.resetCard()
                build()
            }
        })
    }

    private fun refreshPreview() {
        previewHolder.removeAllViews()
        val card = RideCardView(this)
        card.bind(sample, prefs)
        previewHolder.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun refreshPositionButtons() {
        val selected = prefs.cardPosition
        for (b in positionButtons) {
            val index = b.tag as Int
            b.alpha = if (index == selected) 1f else 0.45f
        }
        positionLabel.text = "Posição: ${RideCardView.POSITION_NAMES[selected]}"
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun check(text: String, checked: Boolean, onToggle: (Boolean) -> Unit) = CheckBox(this).apply {
        this.text = text
        isChecked = checked
        setOnCheckedChangeListener { _, value ->
            onToggle(value)
            refreshPreview()
        }
    }

    private fun onChange(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) block(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
