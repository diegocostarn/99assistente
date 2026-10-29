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
    private var lastOcrDump = ""
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
        val accessibilityHasRide = now - OcrState.lastAccessibilityRideAt < OcrState.GRACE_MS

        // Saiu da tela da solicitação: tira o card que o OCR tinha colocado.
        if (OcrState.shownByOcr && !sparse) {
            OcrState.shownByOcr = false
            svc?.hideFromOcr()
        }

        // Sempre esvazia a fila de imagens, mesmo quando não vai usar o quadro.
        val image = r.acquireLatestImage() ?: return
        if (!sparse || accessibilityHasRide || busy) {
            image.close()
            return
        }

        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val full = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        image.close()

        // Ignora o topo, onde fica o card do Assistente 99 (evita ler a si mesmo).
        val top = (height * IGNORE_TOP_FRACTION).toInt()
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

        val ride = RideParser.parseOcr(lines)
        if (ride != null) {
            OcrState.lastOcrRideAt = now
            OcrState.shownByOcr = true
            svc?.showRideFromOcr(ride)
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
        private const val MAX_WIDTH = 720f

        /** Fração do topo da tela ignorada (o card do Assistente 99 aparece ali). */
        private const val IGNORE_TOP_FRACTION = 0.36f
    }
}
