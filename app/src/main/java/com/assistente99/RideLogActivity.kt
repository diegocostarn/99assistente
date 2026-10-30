package com.assistente99

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lista das últimas 10 solicitações, com app de origem, R$/km, tempos e botões para abrir os endereços no mapa. */
class RideLogActivity : AppCompatActivity() {

    private lateinit var content: LinearLayout
    private val br = Locale("pt", "BR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        content = Ui.screen(this, "Últimas viagens", "As ${RideLog.MAX} solicitações mais recentes, Uber e 99")
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val prefs = Prefs(this)
        val entries = RideLog(this).all()

        if (entries.isEmpty()) {
            content.addView(Ui.card(this).apply {
                addView(Ui.text(this@RideLogActivity, "Nenhuma solicitação registrada ainda", 16f, Pal.ink(this@RideLogActivity), true))
                addView(Ui.text(
                    this@RideLogActivity,
                    "Fique online na Uber ou na 99: cada solicitação que chegar aparece aqui.",
                    14f, Pal.muted(this@RideLogActivity)
                ).apply { setPadding(0, dp(6), 0, 0) })
            })
            return
        }

        for (e in entries) content.addView(card(e, prefs))

        content.addView(Ui.button(this, "Limpar histórico", Ui.DANGER) {
            AlertDialog.Builder(this@RideLogActivity)
                .setMessage("Apagar as ${entries.size} solicitações do histórico?")
                .setPositiveButton("Apagar") { _, _ ->
                    RideLog(this@RideLogActivity).clear()
                    render()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        })
    }

    private fun card(e: RideLog.Entry, prefs: Prefs): LinearLayout {
        val ink = Pal.ink(this)
        val muted = Pal.muted(this)
        val dark = Pal.dark(this)
        val perKm = e.perKm
        val accent = when {
            perKm == null -> muted
            perKm >= prefs.goodPerKm -> Color.parseColor(if (dark) "#4CD07D" else "#15803D")
            perKm >= prefs.minPerKm -> Color.parseColor(if (dark) "#F2B84B" else "#B45309")
            else -> Color.parseColor(if (dark) "#FF6B63" else "#B91C1C")
        }

        val card = Ui.card(this)

        // Linha 1: app de origem + horário
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(Ui.sourcePill(this@RideLogActivity, e.source))
            addView(Ui.text(
                this@RideLogActivity,
                SimpleDateFormat("dd/MM  HH:mm", br).format(Date(e.time)), 13f, muted
            ).apply { setPadding(dp(10), 0, 0, 0) })
        })

        // Linha 2: R$/km em destaque + preço
        card.addView(Ui.text(
            this, if (perKm != null) "R$ ${fmt(perKm)}/km" else "R$ --/km", 28f, accent, true
        ).apply { setPadding(0, dp(6), 0, 0) })
        card.addView(Ui.text(this, "R$ ${fmt(e.price)}", 16f, ink, true))
        val passenger = listOfNotNull(
            e.rating?.let { "★ ${fmt(it)}" },
            e.rides?.let { "$it corridas" }
        )
        if (passenger.isNotEmpty()) {
            card.addView(Ui.text(this, "Passageiro: " + passenger.joinToString("  •  "), 14f, ink, true)
                .apply { setPadding(0, dp(4), 0, 0) })
        }

        // Tempo e distância
        val a = e.pickupMin
        val b = e.tripMin
        val tempo = when {
            a != null && b != null -> "$a min até o passageiro + $b min de viagem = ${a + b} min"
            b != null -> "$b min de viagem"
            else -> "não lido"
        }
        val ka = e.pickupKm
        val kb = e.tripKm
        val dist = when {
            ka != null && kb != null -> "${fmt(ka)} km até o passageiro + ${fmt(kb)} km de viagem = ${fmt(ka + kb)} km"
            kb != null -> "${fmt(kb)} km de viagem"
            else -> "não lida"
        }
        card.addView(info("Tempo", tempo))
        card.addView(info("Distância", dist))

        // Endereços e botões de mapa
        addAddress(card, "Embarque", e.pickupAddress, "🔵  Abrir embarque no mapa", PICKUP_COLOR)
        addAddress(card, "Destino", e.dropoffAddress, "🟠  Abrir destino no mapa", DROPOFF_COLOR)
        return card
    }

    private fun info(label: String, value: String): TextView = Ui.text(
        this, "$label: $value", 14f, Pal.muted(this)
    ).apply { setPadding(0, dp(4), 0, 0) }

    private fun addAddress(card: LinearLayout, label: String, address: String?, buttonText: String, color: Int) {
        card.addView(Ui.text(
            this, if (address != null) "$label: $address" else "$label: endereço não lido", 14f, Pal.ink(this)
        ).apply { setPadding(0, dp(10), 0, 0) })
        if (address != null) card.addView(Ui.button(this, buttonText, color) { openMap(address) })
    }

    /** Abre o endereço no app de mapas do celular (Google Maps, Waze etc., conforme o aparelho). */
    private fun openMap(address: String) {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum app de mapas encontrado.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /** Azul para o embarque, laranja para o destino: dá para distinguir os dois botões de relance. */
        private const val PICKUP_COLOR = 0xFF2563EB.toInt()
        private const val DROPOFF_COLOR = 0xFFEA580C.toInt()
    }

    private fun fmt(v: Double): String = String.format(br, "%.2f", v)

    private fun dp(v: Int): Int = Ui.dp(this, v)
}
