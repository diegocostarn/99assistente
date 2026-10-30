package com.assistente99

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** "Essa é boa": o que é uma corrida boa, aviso sonoro e aceite automático. */
class GoodSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var minField: EditText
    private lateinit var pickupField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        prefs = Prefs(this)
        val content = Ui.screen(this, "Essa é boa", "Avisa quando a corrida vale a pena, e aceita sozinho se você quiser")
        val muted = Pal.muted(this)

        // ---- O que é uma corrida boa ----
        content.addView(Ui.sectionTitle(this, "CORRIDA BOA É A QUE TEM"))
        val rule = Ui.card(this)
        val enabled = Ui.switchRow(this, "Avisar quando a corrida for boa", "O card mostra \"ESSA É BOA!\"", prefs.goodEnabled)
        rule.addView(enabled)
        rule.addView(Ui.fieldLabel(this, "R$ por km, no mínimo"))
        minField = Ui.numberField(this, prefs.goodMinPerKm).also { rule.addView(it) }
        rule.addView(Ui.fieldLabel(this, "Distância até o passageiro, no máximo (km)"))
        pickupField = Ui.numberField(this, prefs.goodMaxPickupKm).also { rule.addView(it) }
        rule.addView(Ui.text(
            this,
            "O R$/km é calculado como no card (conta o km até o passageiro, se essa opção estiver ligada). " +
                "Se o app não conseguir ler o km até o passageiro, a corrida não é marcada como boa.",
            13f, muted
        ).apply { setPadding(0, dp(10), 0, 0) })
        content.addView(rule)

        // ---- Aviso sonoro ----
        content.addView(Ui.sectionTitle(this, "AVISO SONORO"))
        val sound = Ui.card(this)
        val soundSwitch = Ui.switchRow(this, "Tocar um aviso", "Dois bipes no volume de alarme", prefs.goodSound)
        sound.addView(soundSwitch)
        sound.addView(Ui.outlineButton(this, "Ouvir o aviso") { GoodAlert.beep() })
        content.addView(sound)

        // ---- Aceite automático ----
        content.addView(Ui.sectionTitle(this, "ACEITE AUTOMÁTICO"))
        val auto = Ui.card(this)
        val autoSwitch = Ui.switchRow(
            this, "Aceitar a corrida boa sozinho", "Aperta o botão \"Aceitar\" por você", prefs.autoAccept
        )
        val secText = Ui.text(this, secondsLabel(), 14f, muted).apply { setPadding(0, dp(10), 0, 0) }
        autoSwitch.setOnCheckedChangeListener { button, on ->
            if (on && !prefs.autoAccept) {
                // Pede confirmação antes de ligar: o app vai tocar na tela sozinho.
                button.isChecked = false
                AlertDialog.Builder(this)
                    .setTitle("Ligar o aceite automático?")
                    .setMessage(
                        "O app vai apertar \"Aceitar\" sozinho nas corridas marcadas como boas, só em solicitação única " +
                            "(não na lista da 99), depois do tempo escolhido.\n\n" +
                            "A Uber e a 99 podem proibir isso nos termos de uso, e a conta pode ser penalizada. " +
                            "Você é responsável por usar. Ele só toca se o botão ainda estiver na tela."
                    )
                    .setPositiveButton("Ligar") { _, _ ->
                        prefs.autoAccept = true
                        button.isChecked = true
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else if (!on) {
                prefs.autoAccept = false
            }
        }
        auto.addView(autoSwitch)
        auto.addView(secText)
        auto.addView(Ui.seek(this, 9, prefs.autoAcceptSeconds - 1) { p ->
            prefs.autoAcceptSeconds = p + 1
            secText.text = secondsLabel()
        })
        auto.addView(Ui.text(
            this,
            "O aceite usa o serviço de acessibilidade: aperta o botão se o app o mostra, ou toca no lugar onde a " +
                "leitura por imagem viu o botão. Se não conseguir, o motivo aparece em \"Ver o que o app leu\".",
            13f, muted
        ).apply { setPadding(0, dp(10), 0, 0) })
        content.addView(auto)

        content.addView(Ui.button(this, "Salvar") {
            fun parse(f: EditText) = f.text.toString().replace(',', '.').toFloatOrNull()
            val min = parse(minField)
            val pickup = parse(pickupField)
            if (min == null || min <= 0f || pickup == null || pickup <= 0f) {
                Toast.makeText(this, "Preencha o R$/km e a distância com números maiores que zero.", Toast.LENGTH_LONG).show()
            } else {
                prefs.goodMinPerKm = min
                prefs.goodMaxPickupKm = pickup
                prefs.goodEnabled = enabled.isChecked
                prefs.goodSound = soundSwitch.isChecked
                Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun secondsLabel() = "Esperar ${prefs.autoAcceptSeconds} s antes de aceitar"

    private fun dp(v: Int) = Ui.dp(this, v)
}
