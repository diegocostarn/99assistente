package com.assistente99

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Leitura por captura de tela. Serve para telas que o app (ex.: card de solicitação da Uber)
 * não expõe como texto ao serviço de acessibilidade.
 *
 * A cada ~1 s, se o serviço de acessibilidade viu uma tela sem texto num app monitorado:
 *  1. tira um "print" da tela (só na memória, nada é salvo nem enviado);
 *  2. descarta o topo da imagem (onde fica o próprio card do Assistente 99);
 *  3. lê o texto com o OCR do ML Kit, no aparelho;
 *  4. passa as linhas pelo RideParser e manda o card ser desenhado.
 */
class ScreenCaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var busy = false
    private var lastWideOcrAt = 0L

    /** Proporção entre a imagem do OCR e a tela real (a imagem é reduzida para ~720 px de largura). */
    private var captureScale = 1f
    private var lastOcrDump = ""
    private val ocrRideHistory = ArrayList<String>()
    private val ocrRejected = ArrayList<String>()
    private val kmRegex = Regex("""\d\s*km""", RegexOption.IGNORE_CASE)
    private var stopped = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post { stopSelf() }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            try {
                captureAndRead()
            } catch (e: Exception) {
                Log.w(TAG, "Falha na captura", e)
                busy = false
            }
            if (!stopped) handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = Prefs(this)

        val resultCode = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = readProjectionData(intent)
        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14+: o serviço precisa estar em primeiro plano ANTES de obter a projeção.
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(resultCode, data)
            projection = p
            p.registerCallback(projectionCallback, handler)
            setupVirtualDisplay(p)
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar a captura", e)
            stopSelf()
            return START_NOT_STICKY
        }

        OcrState.running = true
        handler.postDelayed(tick, INTERVAL_MS)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readProjectionData(intent: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_DATA)
        }

    private fun setupVirtualDisplay(p: MediaProjection) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds: Rect = if (Build.VERSION.SDK_INT >= 30) {
            wm.maximumWindowMetrics.bounds
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }

        // Reduz para ~720 px de largura: mais rápido e suficiente para ler os números do card.
        val scale = minOf(1f, MAX_WIDTH / bounds.width().toFloat())
        val w = (bounds.width() * scale).toInt()
        val h = (bounds.height() * scale).toInt()
        captureScale = scale

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        virtualDisplay = p.createVirtualDisplay(
            "assistente99-ocr",
            w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )
    }

    private fun captureAndRead() {
        val r = reader ?: return
        val now = System.currentTimeMillis()
        val svc = RideAccessibilityService.instance

        val sparse = now - OcrState.lastSparseAt < OcrState.GRACE_MS
        val unparsed = prefs.ocrWhenText && now - OcrState.lastUnparsedAt < OcrState.GRACE_MS
        val active = sparse || unparsed
        val accessibilityHasRide = now - OcrState.lastAccessibilityRideAt < OcrState.GRACE_MS

        // Saiu da tela da solicitação: tira o card que o OCR tinha colocado.
        if (OcrState.shownByOcr && !active) {
            OcrState.shownByOcr = false
            svc?.hideFromOcr()
        }

        // Sempre esvazia a fila de imagens, mesmo quando não vai usar o quadro.
        val image = r.acquireLatestImage() ?: return
        // Telas com texto (modo "ler também quando há texto") são lidas com menos frequência, para poupar bateria.
        if (!active || accessibilityHasRide || busy || (!sparse && now - lastWideOcrAt < WIDE_INTERVAL_MS)) {
            image.close()
            return
        }
        if (!sparse) lastWideOcrAt = now

        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val full = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        image.close()

        // Tapa o card do Assistente 99 (onde quer que o usuário o tenha colocado) para o OCR não lê-lo.
        svc?.overlayBounds()?.let { b ->
            val m = 6f
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
            android.graphics.Canvas(full).drawRect(
                b.left * captureScale - m, b.top * captureScale - m,
                b.right * captureScale + m, b.bottom * captureScale + m, paint
            )
        }

        // Ignora o topo, onde fica o card do Assistente 99 (evita ler a si mesmo).
        // Na 99 a lista de solicitações começa logo abaixo do título, então ignora só a barra de status.
        val fraction = if (OcrState.lastSparsePkg == "com.app99.driver") IGNORE_TOP_99 else IGNORE_TOP_FRACTION
        val top = (height * fraction).toInt()
        val cropped = Bitmap.createBitmap(full, 0, top, width, height - top)
        full.recycle()

        busy = true
        recognizer.process(InputImage.fromBitmap(cropped, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { b -> b.lines.map { it.text } }
                handleLines(lines)
            }
            .addOnCompleteListener {
                busy = false
                cropped.recycle()
            }
    }

    private fun handleLines(lines: List<String>) {
        val now = System.currentTimeMillis()
        val svc = RideAccessibilityService.instance

        // Guarda o que o OCR leu, para diagnóstico na tela principal.
        val dump = lines.joinToString(" | ")
        if (dump.isNotBlank() && dump != lastOcrDump) {
            lastOcrDump = dump
            prefs.lastOcrDump = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())}] ${lines.size} linhas\n${dump.take(2000)}"
        }

        // Leituras com "km" (card de corrida) vão para um histórico próprio: a tela inicial lida
        // um segundo depois não apaga o texto do card.
        if (kmRegex.containsMatchIn(dump)) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            ocrRideHistory.add(0, "[$time] ${lines.size} linhas\n${dump.take(1500)}")
            while (ocrRideHistory.size > 5) ocrRideHistory.removeAt(ocrRideHistory.size - 1)
            prefs.ocrRideHistory = ocrRideHistory.joinToString("\n\n---\n\n")
        }

        val all = RideParser.parseOcrAllRaw(lines)
        val good = all.filter { RideParser.isPlausible(it) }
        for (bad in all.filter { !RideParser.isPlausible(it) }) {
            // Leitura que não bate com a estimativa por km do card: não mostra, e guarda para análise.
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val calc = bad.perKm(true)?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "?"
            ocrRejected.add(
                0,
                "[$time] DESCARTADA: calculei R$ $calc/km, o app estima R$ ${bad.estPerKm}/km " +
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

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Leitura da tela", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Assistente 99")
            .setContentText("Lendo a tela da 99/Uber para calcular o R$/km")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        OcrState.running = false
        if (OcrState.shownByOcr) {
            OcrState.shownByOcr = false
            RideAccessibilityService.instance?.hideFromOcr()
        }
        try {
            virtualDisplay?.release()
            reader?.close()
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
        } catch (_: Exception) {
        }
        recognizer.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val TAG = "Assistente99"
        private const val CHANNEL_ID = "ocr"
        private const val NOTIFICATION_ID = 99
        private const val INTERVAL_MS = 900L
        private const val WIDE_INTERVAL_MS = 2000L
        private const val MAX_WIDTH = 720f

        /** Fração do topo da tela ignorada (o card do Assistente 99 aparece ali). */
        private const val IGNORE_TOP_FRACTION = 0.36f
        private const val IGNORE_TOP_99 = 0.08f
    }
}
