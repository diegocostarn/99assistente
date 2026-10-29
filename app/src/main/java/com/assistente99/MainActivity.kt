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
    private lateinit var debugCheck: CheckBox
    private lateinit var ocrStatus: TextView

    private val captureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
            ocrStatus.text = "Captura de tela: ligada. Pode abrir a Uber."
        } else {
            ocrStatus.text = "Captura de tela: permissão negada."
        }
    }

    private val notificationLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { launchCapturePrompt() }

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
            text = "Últimas 10 viagens (Uber e 99)"
            setOnClickListener { startActivity(Intent(this@MainActivity, RideLogActivity::class.java)) }
        })

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

        debugCheck = CheckBox(this).apply {
            text = "Modo diagnóstico (mostra um card mesmo sem reconhecer a corrida)"
            isChecked = prefs.debugMode
        }
        col.addView(debugCheck)

        col.addView(TextView(this).apply {
            text = "Leitura por captura de tela (OCR): necessária para o card de solicitação da Uber, " +
                "que o app não entrega como texto. Tudo é processado no aparelho, nada é salvo nem enviado."
            textSize = 13f
            setPadding(0, pad, 0, 0)
        })
        ocrStatus = TextView(this).apply { textSize = 14f }
        col.addView(ocrStatus)
        col.addView(Button(this).apply {
            text = "2) Ligar leitura por captura de tela"
            setOnClickListener { startOcr() }
        })
        col.addView(Button(this).apply {
            text = "Desligar leitura por captura de tela"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                ocrStatus.text = "Captura de tela: desligada."
            }
        })

        col.addView(Button(this).apply {
            text = "Ver o que o app leu da tela"
            setOnClickListener { showDump() }
        })

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
        if (::ocrStatus.isInitialized) {
            ocrStatus.text = if (OcrState.running) "Captura de tela: ligada." else "Captura de tela: desligada."
        }
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
        prefs.debugMode = debugCheck.isChecked
        prefs.packagesText = pkgField.text.toString().ifBlank { Prefs.DEFAULT_PACKAGES }
        Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
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
