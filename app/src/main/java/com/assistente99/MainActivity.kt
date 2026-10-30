package com.assistente99

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** Tela principal: status, atalhos e ajustes. */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var accDot: View
    private lateinit var accText: TextView
    private lateinit var accButton: Button
    private lateinit var ocrDot: View
    private lateinit var ocrText: TextView
    private lateinit var ocrButton: Button
    private lateinit var minField: EditText
    private lateinit var goodField: EditText
    private lateinit var pickupSwitch: SwitchCompat
    private lateinit var debugSwitch: SwitchCompat
    private lateinit var ocrWideSwitch: SwitchCompat
    private lateinit var autoSwitch: SwitchCompat
    private lateinit var notifySwitch: SwitchCompat
    private lateinit var floatSwitch: SwitchCompat

    // Evita pedir a captura em loop se o usuário recusar.
    private var autoAsked = false

    private val captureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            // O serviço leva um instante para marcar "ligado".
            ocrText.postDelayed({ refreshStatus() }, 600)
        } else {
            Toast.makeText(this, "Captura de tela não autorizada", Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    private val notificationLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { launchCapturePrompt() }

    private val notifyPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    private fun askNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startOcr() {
        // A notificação do serviço de captura precisa da permissão de notificações no Android 13+.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchCapturePrompt()
        }
    }

    private fun launchCapturePrompt() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    // ---------- Visual ----------

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun round(color: Int, radiusDp: Int, strokeColor: Int? = null) = Ui.round(this, color, radiusDp, strokeColor)
    private fun card() = Ui.card(this)
    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = Ui.text(this, t, size, color, bold)
    private fun sectionTitle(t: String) = Ui.sectionTitle(this, t)
    private fun filledButton(t: String, color: Int = ACCENT, onClick: () -> Unit) = Ui.button(this, t, color, onClick)
    private fun tile(emoji: String, title: String, sub: String, onClick: () -> Unit) = Ui.tile(this, emoji, title, sub, onClick)
    private fun fieldLabel(t: String) = Ui.fieldLabel(this, t)
    private fun numberField(value: Float) = Ui.numberField(this, value)
    private fun switchRow(t: String, sub: String, checked: Boolean) = Ui.switchRow(this, t, sub, checked)

    private fun statusRow(title: String): Triple<View, TextView, LinearLayout> {
        val box = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(12) }
            background = round(BAD, 6)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(text(title, 16f, INK, true))
        val state = text("", 13f, MUTED)
        col.addView(state)
        row.addView(dot)
        row.addView(col)
        box.addView(row)
        return Triple(dot, state, box)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        prefs = Prefs(this)
        window.statusBarColor = HEADER

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // Cabeçalho
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(HEADER, 0xFF1F3A5F.toInt())
            ).apply { cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat()) }
            setPadding(dp(22), dp(28), dp(22), dp(28))
            addView(text("Assistente 99", 28f, Color.WHITE, true))
            addView(text("Quanto vale cada km, na hora da corrida.\nFunciona na 99 e na Uber.", 14f, 0xFFB7C4D6.toInt()).apply {
                setPadding(0, dp(6), 0, 0)
            })
        })

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }

        // Status
        col.addView(sectionTitle("STATUS"))
        val (d1, t1, c1) = statusRow("Leitura da tela (acessibilidade)")
        accDot = d1; accText = t1
        accButton = filledButton("Ativar nas configurações") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c1.addView(accButton)
        col.addView(c1)

        val (d2, t2, c2) = statusRow("Captura de tela (Uber)")
        ocrDot = d2; ocrText = t2
        ocrButton = filledButton("Ligar captura de tela") { startOcr() }
        c2.addView(ocrButton)
        col.addView(c2)

        // Atalhos
        col.addView(sectionTitle("ATALHOS"))
        col.addView(tile("🕘", "Últimas 10 viagens", "Uber e 99, com endereços e mapa") {
            startActivity(Intent(this, RideLogActivity::class.java))
        })
        col.addView(tile("🎨", "Personalizar o card", "Tamanho, fonte, posição e campos") {
            startActivity(Intent(this, CardSettingsActivity::class.java))
        })
        col.addView(tile("🔎", "Ver o que o app leu", "Diagnóstico para enviar se algo falhar") { showDump() })

        // Ajustes
        col.addView(sectionTitle("AJUSTES"))
        val set = card()
        set.addView(text("Tema do app", 13f, MUTED))
        set.addView(Ui.segmented(this, listOf("Automático", "Claro", "Escuro"), prefs.themeMode) { i ->
            prefs.themeMode = i
            Pal.apply(this)
            recreate()
        })
        set.addView(fieldLabel("R$/km mínimo aceitável (abaixo disso o card fica vermelho)"))
        minField = numberField(prefs.minPerKm).also { set.addView(it) }
        set.addView(fieldLabel("R$/km considerado bom (a partir daí o card fica verde)"))
        goodField = numberField(prefs.goodPerKm).also { set.addView(it) }
        set.addView(View(this).apply {
            setBackgroundColor(LINE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(16); bottomMargin = dp(6)
            }
        })
        pickupSwitch = switchRow("Contar o km até o passageiro", "Entra no cálculo do R$/km", prefs.includePickup)
        set.addView(pickupSwitch)
        autoSwitch = switchRow("Ligar a captura ao abrir o app", "Pede a autorização sozinho se estiver desligada", prefs.autoCapture)
        set.addView(autoSwitch)
        ocrWideSwitch = switchRow("Ler por imagem também com texto na tela", "Ajuda na 99; gasta mais bateria", prefs.ocrWhenText)
        set.addView(ocrWideSwitch)
        notifySwitch = switchRow("Última corrida na barra de notificações", "Silenciosa, com botões de mapa", prefs.notifyLastRide)
        notifySwitch.setOnCheckedChangeListener { _, on -> if (on) askNotificationPermissionIfNeeded() }
        set.addView(notifySwitch)
        floatSwitch = switchRow("Botão flutuante", "Atalho sobre os apps: últimas viagens e ligar/desligar Uber e 99", prefs.floatingButton)
        set.addView(floatSwitch)
        debugSwitch = switchRow("Modo diagnóstico", "Mostra um card mesmo sem reconhecer a corrida", prefs.debugMode)
        set.addView(debugSwitch)
        col.addView(set)

        col.addView(filledButton("Salvar ajustes", HEADER) { save() })

        col.addView(text(
            "Tudo é processado no aparelho: nada é salvo nem enviado para a internet.", 12f, MUTED
        ).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(18), dp(8), 0) })

        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(col)
        })
        setContentView(root)

        if (prefs.notifyLastRide) askNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // Captura "sempre ligada": ao abrir o app, pede a autorização se ela estiver desligada.
        // (O Android exige a confirmação do usuário a cada vez; não dá para ligá-la escondido.)
        if (prefs.autoCapture && !OcrState.running && !autoAsked && isServiceEnabled()) {
            autoAsked = true
            startOcr()
        }
    }

    private fun refreshStatus() {
        val acc = isServiceEnabled()
        accDot.background = round(if (acc) GOOD else BAD, 6)
        accText.text = if (acc) "Ativo. Abra a 99 ou a Uber e fique online." else "Desligado. Toque abaixo e ligue \"Assistente 99\"."
        accButton.visibility = if (acc) View.GONE else View.VISIBLE

        val ocr = OcrState.running
        ocrDot.background = round(if (ocr) GOOD else BAD, 6)
        ocrText.text = if (ocr) "Ligada. Pode abrir a Uber." else "Desligada. Necessária para ler o card da Uber."
        ocrButton.text = if (ocr) "Desligar captura de tela" else "Ligar captura de tela"
        ocrButton.background = round(if (ocr) 0xFF6B7280.toInt() else ACCENT, 14)
        ocrButton.setOnClickListener {
            if (OcrState.running) {
                stopService(Intent(this, ScreenCaptureService::class.java))
                // Desligar na mão vale até fechar o app.
                autoAsked = true
                ocrText.postDelayed({ refreshStatus() }, 400)
            } else startOcr()
        }
    }

    private fun save() {
        fun parse(f: EditText) = f.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
        prefs.minPerKm = parse(minField)
        prefs.goodPerKm = parse(goodField)
        prefs.includePickup = pickupSwitch.isChecked
        prefs.debugMode = debugSwitch.isChecked
        prefs.ocrWhenText = ocrWideSwitch.isChecked
        prefs.autoCapture = autoSwitch.isChecked
        prefs.notifyLastRide = notifySwitch.isChecked
        if (!notifySwitch.isChecked) RideNotifier.cancel(this)
        prefs.floatingButton = floatSwitch.isChecked
        RideAccessibilityService.instance?.refreshFloating()
        Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
    }

    private val BG get() = Pal.bg(this)
    private val CARD get() = Pal.card(this)
    private val LINE get() = Pal.line(this)
    private val INK get() = Pal.ink(this)
    private val MUTED get() = Pal.muted(this)

    companion object {
        private val HEADER = Ui.HEADER
        private val ACCENT = Ui.ACCENT
        private val GOOD = Ui.GOOD
        private val BAD = Ui.BAD
    }

    /** Mostra o último texto lido da tela dos apps e permite compartilhar (ex.: WhatsApp). */
    private fun showDump() {
        val dump = prefs.lastDump
        val seen = prefs.seenPackages.ifBlank { "nenhum (o serviço não recebeu nenhum evento)" }
        val status = prefs.lastStatus.ifBlank { "nenhuma tentativa de leitura dos apps monitorados" }
        val texts = if (dump.isBlank()) {
            "Nenhum texto lido ainda."
        } else {
            val secs = (System.currentTimeMillis() - prefs.lastDumpAt) / 1000
            "Texto lido há $secs s:\n$dump"
        }
        val rideDump = if (prefs.lastRideDump.isBlank()) {
            "Nenhuma ainda (fique online e deixe uma solicitação ou o radar aparecer)."
        } else {
            val secs = (System.currentTimeMillis() - prefs.lastRideDumpAt) / 1000
            "Capturas (mais recente primeiro; a última foi há $secs s):\n\n${prefs.lastRideDump}"
        }
        val ocr = prefs.lastOcrDump.ifBlank { "Nada lido por OCR ainda (captura ${if (OcrState.running) "ligada" else "DESLIGADA"})." }
        val ocrRides = prefs.ocrRideHistory.ifBlank {
            "Nenhuma leitura de OCR com km ainda (o card da corrida precisa estar na tela com a captura ligada)."
        }
        val rejected = prefs.ocrRejected.ifBlank { "Nenhuma." }
        val message = "OCR - LEITURAS DESCARTADAS (não bateram com a estimativa da Uber):\n$rejected\n\n" +
            "OCR - LEITURAS COM KM (mais recente primeiro):\n$ocrRides\n\n" +
            "OCR - ÚLTIMA LEITURA (qualquer tela):\n$ocr\n\n" +
            "ÚLTIMA TELA COM PREÇO E KM (radar/corrida):\n$rideDump\n\n" +
            "Apps que enviaram eventos de tela (pacote: quantidade):\n$seen\n\n" +
            "Estado da última leitura:\n$status\n\n$texts"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Última leitura da tela")
            .setMessage(message)
            .setPositiveButton("Compartilhar") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                startActivity(Intent.createChooser(send, "Compartilhar"))
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${RideAccessibilityService::class.java.name}") ||
            enabled.contains("$packageName/.RideAccessibilityService")
    }
}
