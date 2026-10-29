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
 *
 * Também grava informações de diagnóstico (quais apps enviam eventos, se as janelas são legíveis)
 * para a tela principal mostrar.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var overlay: OverlayView
    private val rideLog by lazy { RideLog(this) }

    private var lastDump = ""
    private var lastSavedAt = 0L
    private var lastStatusAt = 0L
    private var lastSeenFlush = 0L
    private var lastEventPkg = ""
    private var scanScheduled = false
    private val seenPackages = LinkedHashMap<String, Int>()

    private val scanRunnable = Runnable {
        scanScheduled = false
        scan()
    }

    private val hideRunnable = Runnable { overlay.hide() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        overlay = OverlayView(this)
        instance = this
    }

    /** Chamado pelo serviço de captura (OCR) quando ele reconhece uma corrida na imagem. */
    fun showRideFromOcr(ride: RideInfo) {
        handler.post {
            handler.removeCallbacks(hideRunnable)
            overlay.show(ride, prefs)
            rideLog.record(RideLog.sourceLabel(OcrState.lastSparsePkg), ride, prefs.includePickup)
        }
    }

    /** Chamado pelo serviço de captura (OCR) quando a corrida saiu da tela. */
    fun hideFromOcr() {
        handler.post { overlay.hide() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        countPackage(pkg)
        if (pkg !in prefs.packages) return
        lastEventPkg = pkg

        // Agrupa rajadas de eventos: no máximo uma leitura a cada ~120 ms.
        if (!scanScheduled) {
            scanScheduled = true
            handler.postDelayed(scanRunnable, 120)
        }
    }

    /** Conta os eventos por app (de todos os apps), para mostrar no diagnóstico. */
    private fun countPackage(pkg: String) {
        if (seenPackages.size > 40) seenPackages.clear()
        seenPackages[pkg] = (seenPackages[pkg] ?: 0) + 1
        val now = System.currentTimeMillis()
        if (now - lastSeenFlush > 1000) {
            lastSeenFlush = now
            prefs.seenPackages = seenPackages.entries
                .sortedByDescending { it.value }
                .take(12)
                .joinToString("\n") { "${it.key}: ${it.value}" }
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

    private fun describeWindows(): String {
        return try {
            val ws = windows
            val readable = ws.mapNotNull { it.root?.packageName?.toString() }
                .joinToString(", ").ifEmpty { "nenhuma" }
            "Janelas abertas: ${ws.size}. Janelas legíveis de: $readable"
        } catch (e: Exception) {
            "Erro ao listar janelas: ${e.javaClass.simpleName}"
        }
    }

    private fun saveStatus(rootsCount: Int, nodes: Int, textCount: Int) {
        val now = System.currentTimeMillis()
        if (now - lastStatusAt < 700) return
        lastStatusAt = now
        val active = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            null
        } ?: "nenhuma"
        prefs.lastStatus = "App monitorado que enviou evento: $lastEventPkg\n" +
            "Janelas desse app que consegui abrir: $rootsCount\n" +
            "Elementos na tela: $nodes\n" +
            "Textos encontrados: $textCount\n" +
            "${describeWindows()}\n" +
            "Janela ativa: $active"
    }

    private fun scan() {
        val roots = monitoredRoots()
        if (roots.isEmpty()) {
            saveStatus(0, 0, 0)
            if (prefs.debugMode) {
                handler.removeCallbacks(hideRunnable)
                overlay.showDebug("$lastEventPkg enviou eventos, mas não consegui abrir a janela do app.")
            } else {
                scheduleHide()
            }
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

        saveStatus(roots.size, nodes, texts.size)
        saveDump(pkg, nodes, texts)
        saveRideDump(pkg, texts)

        val ride = RideParser.parse(texts)

        // Tela quase sem texto num app monitorado = card que o app não expõe (caso da Uber).
        // Avisa o serviço de OCR, que lê a imagem da tela no lugar.
        val sparse = texts.size <= SPARSE_MAX_TEXTS
        if (sparse) {
            OcrState.lastSparseAt = System.currentTimeMillis()
            OcrState.lastSparsePkg = pkg
        }
        if (ride != null) OcrState.lastAccessibilityRideAt = System.currentTimeMillis()

        when {
            ride != null -> {
                handler.removeCallbacks(hideRunnable)
                overlay.show(ride, prefs)
                rideLog.record(RideLog.sourceLabel(pkg), ride, prefs.includePickup)
            }
            sparse && OcrState.running -> {
                // O OCR cuida do card; só evita que um "esconder" pendente apague o que ele mostrou.
                handler.removeCallbacks(hideRunnable)
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

    private var lastRideDump = ""
    private val rideHistory = ArrayList<String>()
    private val kmRegex = Regex("""\d\s*km""", RegexOption.IGNORE_CASE)

    /**
     * Guarda a última tela que tinha preço e km (ex.: o card do radar), separada do resto,
     * para dar tempo de abrir o app e copiar depois que o card sumir.
     */
    private fun saveRideDump(pkg: String, texts: List<String>) {
        val dump = texts.joinToString(" | ").replace(' ', ' ').replace(' ', ' ')
        if (dump == lastRideDump) return
        // Precisa de um valor em R$ que não comece com "+" (ignora bônus e preço dinâmico) e de km.
        val hasPrice = texts.any {
            val t = it.replace(' ', ' ').trimStart()
            !t.startsWith("+") && t.contains("R\$")
        }
        if (!hasPrice || !kmRegex.containsMatchIn(dump)) return
        lastRideDump = dump

        // Guarda as 5 capturas mais recentes (mais nova primeiro), para nada se perder.
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        rideHistory.add(0, "[$time] ${texts.size} textos ($pkg)\n${dump.take(2000)}")
        while (rideHistory.size > 5) rideHistory.removeAt(rideHistory.size - 1)
        prefs.lastRideDump = rideHistory.joinToString("\n\n---\n\n")
        prefs.lastRideDumpAt = System.currentTimeMillis()
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
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Assistente99"

        /** Tela com até esta quantidade de textos é considerada "sem texto legível". */
        private const val SPARSE_MAX_TEXTS = 3

        @Volatile
        var instance: RideAccessibilityService? = null
    }
}

/** Mude para false antes de distribuir. Com true, os textos lidos vão para o Logcat (útil para ajustar o parser). */
object BuildConfigDebug {
    const val LOG_TEXTS = true
}
