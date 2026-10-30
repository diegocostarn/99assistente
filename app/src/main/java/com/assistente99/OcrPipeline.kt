package com.assistente99

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Leitura de uma imagem da tela por OCR (ML Kit, no aparelho), usada tanto pela captura via
 * serviço de acessibilidade (Android 11+) quanto pela captura por projeção de tela (Android 10 ou menos).
 *
 * Tapa o card do Assistente 99, corta o topo, lê o texto, interpreta as corridas e manda desenhar o card.
 */
class OcrPipeline(context: Context) {

    private val prefs = Prefs(context.applicationContext)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile var busy = false
        private set

    private var lastOcrDump = ""
    private val ocrRideHistory = ArrayList<String>()
    private val ocrRejected = ArrayList<String>()
    private val kmRegex = Regex("""\d\s*km""", RegexOption.IGNORE_CASE)

    /**
     * [full] é a imagem da tela já reduzida e [scale] a proporção em relação à tela real.
     * A imagem é reciclada aqui.
     */
    fun process(full: Bitmap, scale: Float) {
        val svc = RideAccessibilityService.instance

        // Tapa o card do Assistente 99 (onde quer que o usuário o tenha colocado) para o OCR não lê-lo.
        svc?.overlayBounds()?.let { b ->
            val m = 6f
            val paint = Paint().apply { color = Color.WHITE }
            Canvas(full).drawRect(
                b.left * scale - m, b.top * scale - m, b.right * scale + m, b.bottom * scale + m, paint
            )
        }

        // Ignora o topo, onde fica o card do Assistente 99 (evita ler a si mesmo).
        // Na 99 a lista de solicitações começa logo abaixo do título, então ignora só a barra de status.
        val fraction = if (OcrState.lastSparsePkg == "com.app99.driver") IGNORE_TOP_99 else IGNORE_TOP_FRACTION
        val top = (full.height * fraction).toInt()
        val cropped = Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
        full.recycle()

        busy = true
        recognizer.process(InputImage.fromBitmap(cropped, 0))
            .addOnSuccessListener { result ->
                handleLines(result.textBlocks.flatMap { b -> b.lines.map { it.text } })
            }
            .addOnCompleteListener {
                busy = false
                cropped.recycle()
            }
    }

    fun close() {
        recognizer.close()
    }

    private fun now() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun handleLines(lines: List<String>) {
        val now = System.currentTimeMillis()
        val svc = RideAccessibilityService.instance

        // Guarda o que o OCR leu, para diagnóstico na tela principal.
        val dump = lines.joinToString(" | ")
        if (dump.isNotBlank() && dump != lastOcrDump) {
            lastOcrDump = dump
            prefs.lastOcrDump = "[${now()}] ${lines.size} linhas\n${dump.take(2000)}"
        }

        // Leituras com "km" (card de corrida) vão para um histórico próprio: a tela inicial lida
        // um segundo depois não apaga o texto do card.
        if (kmRegex.containsMatchIn(dump)) {
            ocrRideHistory.add(0, "[${now()}] ${lines.size} linhas\n${dump.take(1500)}")
            while (ocrRideHistory.size > 5) ocrRideHistory.removeAt(ocrRideHistory.size - 1)
            prefs.ocrRideHistory = ocrRideHistory.joinToString("\n\n---\n\n")
        }

        val all = RideParser.parseOcrAllRaw(lines)
        val good = all.filter { RideParser.isPlausible(it) }
        for (bad in all.filter { !RideParser.isPlausible(it) }) {
            // Leitura que não bate com a estimativa por km do card: não mostra, e guarda para análise.
            val calc = bad.perKm(true)?.let { String.format(Locale.US, "%.2f", it) } ?: "?"
            ocrRejected.add(
                0,
                "[${now()}] DESCARTADA: calculei R$ $calc/km, o app estima R$ ${bad.estPerKm}/km " +
                    "(preço ${bad.price}, até o passageiro ${bad.pickupKm} km, viagem ${bad.tripKm} km)\n${dump.take(1200)}"
            )
            while (ocrRejected.size > 5) ocrRejected.removeAt(ocrRejected.size - 1)
            prefs.ocrRejected = ocrRejected.joinToString("\n\n---\n\n")
        }
        if (good.isNotEmpty()) {
            OcrState.lastOcrRideAt = now
            OcrState.shownByOcr = true
            svc?.showRidesFromOcr(good)
        } else if (OcrState.shownByOcr && now - OcrState.lastOcrRideAt > OcrState.GRACE_MS) {
            OcrState.shownByOcr = false
            svc?.hideFromOcr()
        }
    }

    companion object {

        /** Fração do topo da tela ignorada (o card do Assistente 99 aparece ali). */
        private const val IGNORE_TOP_FRACTION = 0.36f
        private const val IGNORE_TOP_99 = 0.08f
    }
}
