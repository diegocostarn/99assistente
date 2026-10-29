package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Observa a tela dos apps de motorista (99 e Uber) em tempo real. A cada mudança de conteúdo:
 *  1. coleta todos os textos visíveis;
 *  2. tenta interpretar como uma solicitação de corrida (preço + km);
 *  3. mostra o card com o R$/km; se a solicitação sumir, esconde o card.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayView

    private var lastDump = ""
    private var scanScheduled = false

    private val scanRunnable = Runnable {
        scanScheduled = false
        scan()
    }

    private val hideRunnable = Runnable { overlay.hide() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        overlay = OverlayView(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in prefs.packages) return

        // Agrupa rajadas de eventos: no máximo uma leitura a cada ~120 ms.
        if (!scanScheduled) {
            scanScheduled = true
            handler.postDelayed(scanRunnable, 120)
        }
    }

    private fun scan() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() !in prefs.packages) {
            scheduleHide()
            return
        }

        val texts = ArrayList<String>()
        try {
            collectTexts(root, texts, 0)
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }

        if (BuildConfigDebug.LOG_TEXTS) {
            val dump = texts.joinToString(" | ")
            if (dump != lastDump) {
                lastDump = dump
                Log.d(TAG, "Textos da tela: $dump")
            }
        }

        val ride = RideParser.parse(texts)
        if (ride == null) {
            scheduleHide()
        } else {
            handler.removeCallbacks(hideRunnable)
            overlay.show(ride, prefs)
        }
    }

    /** Esconde o card só se a solicitação continuar ausente por ~1,2 s (evita piscar). */
    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 1200)
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectTexts(child, out, depth + 1)
            @Suppress("DEPRECATION")
            child?.recycle()
        }
    }

    override fun onInterrupt() {
        overlay.hide()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Assistente99"
    }
}

/** Mude para false antes de distribuir. Com true, os textos lidos vão para o Logcat (útil para ajustar o parser). */
object BuildConfigDebug {
    const val LOG_TEXTS = true
}
