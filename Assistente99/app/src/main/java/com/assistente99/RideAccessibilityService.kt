package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Observa a tela dos apps de motorista (99 e Uber) em tempo real. A cada mudança de conteúdo:
 *  1. coleta todos os textos visíveis de todas as janelas dos apps monitorados
 *     (a oferta pode estar numa janela separada, e não só na janela ativa);
 *  2. tenta interpretar como uma solicitação de corrida (preço + km);
 *  3. mostra o card com o R$/km; se a solicitação sumir, esconde o card.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayView

    private var lastDump = ""
    private var lastSavedAt = 0L
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

    /** Raízes de todas as janelas que pertencem aos apps monitorados. */
    private fun monitoredRoots(): List<AccessibilityNodeInfo> {
        val packages = prefs.packages
        val result = ArrayList<AccessibilityNodeInfo>()
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() in packages) result += r
            }
        } catch (_: Exception) {
            // Alguns aparelhos lançam exceção ao listar janelas; cai no fallback abaixo.
        }
        if (result.isEmpty()) {
            val active = rootInActiveWindow
            if (active != null && active.packageName?.toString() in packages) result += active
        }
        return result
    }

    private fun scan() {
        val roots = monitoredRoots()
        if (roots.isEmpty()) {
            scheduleHide()
            return
        }

        val pkg = roots.first().packageName?.toString() ?: ""
        val texts = ArrayList<String>()
        var nodes = 0
        for (r in roots) {
            nodes += collectTexts(r, texts, 0)
            @Suppress("DEPRECATION")
            r.recycle()
        }

        saveDump(pkg, nodes, texts)

        val ride = RideParser.parse(texts)
        when {
            ride != null -> {
                handler.removeCallbacks(hideRunnable)
                overlay.show(ride, prefs)
            }
            prefs.debugMode -> {
                handler.removeCallbacks(hideRunnable)
                overlay.showDebug(
                    if (texts.isEmpty()) {
                        "$pkg: tela sem nenhum texto legível ($nodes elementos)."
                    } else {
                        "$pkg: leu ${texts.size} textos, mas não reconheceu preço e km."
                    }
                )
            }
            else -> scheduleHide()
        }
    }

    /** Guarda o último texto lido, para a tela principal mostrar/compartilhar. */
    private fun saveDump(pkg: String, nodes: Int, texts: List<String>) {
        val dump = texts.joinToString(" | ")
        val now = System.currentTimeMillis()
        if (dump == lastDump || now - lastSavedAt < 500) return
        lastDump = dump
        lastSavedAt = now
        prefs.lastDump = "Pacote: $pkg\nElementos na tela: $nodes\nTextos lidos: ${texts.size}\n\n" +
            dump.take(4000)
        prefs.lastDumpAt = now
        if (BuildConfigDebug.LOG_TEXTS) Log.d(TAG, "Textos da tela: $dump")
    }

    /** Esconde o card só se a solicitação continuar ausente por ~1,2 s (evita piscar). */
    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 1200)
    }

    /** Coleta textos recursivamente e devolve quantos elementos foram visitados. */
    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int): Int {
        if (node == null || depth > 40) return 0
        var count = 1
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            count += collectTexts(child, out, depth + 1)
            @Suppress("DEPRECATION")
            child?.recycle()
        }
        return count
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
