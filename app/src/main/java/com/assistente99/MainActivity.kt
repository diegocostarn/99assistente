package com.assistente99

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Tela de configuração: ativar o serviço e ajustar faixas de R$/km. */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var minField: EditText
    private lateinit var goodField: EditText
    private lateinit var costField: EditText
    private lateinit var pkgField: EditText
    private lateinit var pickupCheck: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }

        fun numberField(value: Float) = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (value == 0f) "" else value.toString())
        }

        col.addView(TextView(this).apply {
            text = "Assistente 99"
            textSize = 24f
        })

        status = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, pad) }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "1) Ativar o serviço de acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(TextView(this).apply {
            text = "Na lista, abra \"Assistente 99\" e ligue. O Android vai pedir confirmação: " +
                "o serviço só lê a tela dos apps de motorista (99 e Uber) e nada sai do aparelho."
            textSize = 13f
        })

        col.addView(label("R$/km mínimo aceitável (abaixo disso o card fica vermelho)"))
        minField = numberField(prefs.minPerKm).also { col.addView(it) }

        col.addView(label("R$/km considerado bom (a partir daí o card fica verde)"))
        goodField = numberField(prefs.goodPerKm).also { col.addView(it) }

        col.addView(label("Custo do carro por km (combustível + desgaste). Opcional: se preencher, o card mostra o lucro"))
        costField = numberField(prefs.costPerKm).also { col.addView(it) }

        pickupCheck = CheckBox(this).apply {
            text = "Incluir o km até o passageiro no cálculo"
            isChecked = prefs.includePickup
        }
        col.addView(pickupCheck)

        col.addView(label("Pacotes dos apps monitorados (99 e Uber), separados por vírgula"))
        pkgField = EditText(this).apply { setText(prefs.packagesText) }
        col.addView(pkgField)

        col.addView(Button(this).apply {
            text = "Salvar"
            setOnClickListener { save() }
        })

        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (isServiceEnabled()) {
            "Serviço ATIVO. Abra o app da 99 ou da Uber e fique online."
        } else {
            "Serviço DESLIGADO."
        }
    }

    private fun save() {
        fun parse(f: EditText) = f.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
        prefs.minPerKm = parse(minField)
        prefs.goodPerKm = parse(goodField)
        prefs.costPerKm = parse(costField)
        prefs.includePickup = pickupCheck.isChecked
        prefs.packagesText = pkgField.text.toString().ifBlank { Prefs.DEFAULT_PACKAGES }
        Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${RideAccessibilityService::class.java.name}") ||
            enabled.contains("$packageName/.RideAccessibilityService")
    }
}
