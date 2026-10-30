package com.assistente99

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
        title = "Últimas viagens"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Pal.bg(this@RideLogActivity))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render() {
        content.removeAllViews()
        val prefs = Prefs(this)
        val entries = RideLog(this).all()

        if (entries.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Nenhuma solicitação registrada ainda.\n\nFique online na Uber ou na 99: " +
                    "cada solicitação que chegar aparece aqui, as últimas ${RideLog.MAX}."
                textSize = 16f
                setPadding(dp(4), dp(24), dp(4), dp(24))
            })
            return
        }

        for (e in entries) content.addView(card(e, prefs))

        content.addView(Button(this).apply {
            text = "Limpar histórico"
            setOnClickListener {
                AlertDialog.Builder(this@RideLogActivity)
                    .setMessage("Apagar as ${entries.size} solicitações do histórico?")
                    .setPositiveButton("Apagar") { _, _ ->
                        RideLog(this@RideLogActivity).clear()
                        render()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        })
    }

    private fun card(e: RideLog.Entry, prefs: Prefs): LinearLayout {
        val good = prefs.goodPerKm
        val min = prefs.minPerKm
        val perKm = e.perKm
        val dark = Pal.dark(this)
        val accent = when {
            perKm == null -> Pal.muted(this)
            perKm >= good -> Color.parseColor(if (dark) "#4CD07D" else "#137A3A")
            perKm >= min -> Color.parseColor(if (dark) "#F2B84B" else "#B7791F")
            else -> Color.parseColor(if (dark) "#FF6B63" else "#B3261E")
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#22808080"))
                setStroke(dp(2), accent)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        // Linha 1: app de origem + horário
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(sourceBadge(e.source))
        header.addView(TextView(this).apply {
            text = "  " + SimpleDateFormat("dd/MM HH:mm", br).format(Date(e.time))
            textSize = 13f
        })
        card.addView(header)

        // Linha 2: R$/km em destaque + preço
        card.addView(TextView(this).apply {
            text = if (perKm != null) "R$ ${fmt(perKm)} / km" else "R$ -- / km"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            setPadding(0, dp(6), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = "Valor da corrida: R$ ${fmt(e.price)}"
            textSize = 15f
        })

        // Tempo e distância
        val tempo = buildString {
            append("Tempo: ")
            val a = e.pickupMin
            val b = e.tripMin
            when {
                a != null && b != null -> append("$a min até o passageiro + $b min de viagem = ${a + b} min")
                b != null -> append("$b min de viagem")
                else -> append("não lido")
            }
        }
        card.addView(TextView(this).apply { text = tempo; textSize = 14f })

        val dist = buildString {
            append("Distância: ")
            val a = e.pickupKm
            val b = e.tripKm
            when {
                a != null && b != null -> append("${fmt(a)} km até o passageiro + ${fmt(b)} km de viagem = ${fmt(a + b)} km")
                b != null -> append("${fmt(b)} km de viagem")
                else -> append("não lida")
            }
        }
        card.addView(TextView(this).apply { text = dist; textSize = 14f })

        // Endereços e botões de mapa
        addAddress(card, "Embarque", e.pickupAddress, "Abrir embarque no mapa")
        addAddress(card, "Destino", e.dropoffAddress, "Abrir destino no mapa")
        return card
    }

    private fun addAddress(card: LinearLayout, label: String, address: String?, buttonText: String) {
        card.addView(TextView(this).apply {
            text = if (address != null) "$label: $address" else "$label: endereço não lido"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })
        if (address != null) {
            card.addView(Button(this).apply {
                text = buttonText
                transformationMethod = null
                setOnClickListener { openMap(address) }
            })
        }
    }

    private fun sourceBadge(source: String): TextView {
        val isUber = source == "Uber"
        return TextView(this).apply {
            text = source.uppercase(br)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isUber) Color.WHITE else Color.BLACK)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (isUber) Color.BLACK else Color.parseColor("#FFC800"))
            }
        }
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

    private fun fmt(v: Double): String = String.format(br, "%.2f", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
