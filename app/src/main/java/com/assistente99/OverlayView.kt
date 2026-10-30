package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Janela flutuante com o card, mostrada sobre o app da 99/Uber.
 * Usa TYPE_ACCESSIBILITY_OVERLAY, então não precisa da permissão "Sobrepor a outros apps".
 * O card não captura toques, para não atrapalhar o botão de aceitar da corrida.
 *
 * O visual (tamanho, fonte, campos, transparência) vem de [RideCardView]; aqui só se cuida da janela
 * e da posição na tela, escolhida em [Prefs.cardPosition].
 */
class OverlayView(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(service)
    private var card: RideCardView? = null
    private var builtPosition = ""
    private var cardParams: WindowManager.LayoutParams? = null
    private var controls: View? = null

    fun show(ride: RideInfo, prefs: Prefs, source: String? = null, good: Boolean = false) {
        ensureCreated().bind(ride, prefs, source, good)
        relayout()
    }

    /** Várias corridas na mesma tela (lista de solicitações): uma linha por corrida. */
    fun showMany(rides: List<RideInfo>, prefs: Prefs, source: String? = null) {
        ensureCreated().bindMany(rides, prefs, source)
        relayout()
    }

    /** Card de diagnóstico: aparece quando há leitura de tela mas nenhuma corrida reconhecida. */
    fun showDebug(message: String) {
        ensureCreated().bindDebug(message, prefs)
        relayout()
    }

    /**
     * A janela do card é WRAP_CONTENT, mas o Android nem sempre a redimensiona quando o conteúdo
     * muda numa janela já aberta: o card ficava com a largura da leitura anterior e cortava o texto
     * (sumia o R$/km e os km). Aqui força medir de novo e reaplicar o tamanho da janela.
     */
    private fun relayout() {
        val v = card ?: return
        val lp = cardParams ?: return
        val dm = service.resources.displayMetrics
        val margin = (8 * dm.density).toInt()
        // Mede o card com o conteúdo novo e dá à janela o tamanho exato, sem depender do Android
        // recalcular o WRAP_CONTENT (que deixava o card com a largura antiga e cortava o texto).
        v.measure(
            View.MeasureSpec.makeMeasureSpec((dm.widthPixels - 2 * margin).coerceAtLeast(1), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        lp.width = v.measuredWidth
        lp.height = v.measuredHeight
        if (lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.START && lp.x >= 0) {
            lp.x = lp.x.coerceIn(0, (dm.widthPixels - lp.width).coerceAtLeast(0))
        }
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    fun hide() {
        card?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        card = null
        builtPosition = ""
        cardParams = null
        controls?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        controls = null
    }

    /**
     * Modo de posicionamento livre: mostra o card de exemplo, que pode ser arrastado para qualquer lugar,
     * com os botões "Salvar posição" e "Cancelar". A posição é guardada em pixels.
     */
    fun startEdit(ride: RideInfo, source: String? = null) {
        hide()
        val v = ensureCreated()
        v.bind(ride, prefs, source)
        val lp = cardParams ?: return
        val dm = service.resources.displayMetrics
        val density = dm.density
        fun dp(x: Int) = (x * density).toInt()

        v.post {
            // Passa para coordenadas absolutas a partir de onde o card está agora.
            bounds()?.let {
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = it.left
                lp.y = it.top
            }
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + e.rawX - downX).toInt()
                        .coerceIn(0, (dm.widthPixels - v.width).coerceAtLeast(0))
                    lp.y = (startY + e.rawY - downY).toInt()
                        .coerceIn(0, (dm.heightPixels - v.height).coerceAtLeast(0))
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                }
            }
            true
        }

        fun pill(label: String, color: Int, onClick: () -> Unit) = TextView(service).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(22).toFloat()
            }
            setPadding(dp(18), dp(12), dp(18), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6); marginEnd = dp(6) }
            setOnClickListener { onClick() }
        }

        val bar = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(service).apply {
                text = "Arraste o card até onde quiser"
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = GradientDrawable().apply {
                    setColor(0xCC000000.toInt()); cornerRadius = dp(14).toFloat()
                }
            })
            addView(LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
                addView(pill("Cancelar", 0xFF6B7280.toInt()) { hide() })
                addView(pill("✓ Salvar posição", 0xFF16A34A.toInt()) {
                    prefs.cardX = lp.x
                    prefs.cardY = lp.y
                    hide()
                })
            })
        }
        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(72)
        }
        wm.addView(bar, barParams)
        controls = bar
    }

    /**
     * Onde o card está na tela agora (em pixels), ou null se não está visível.
     * O serviço de captura usa isso para tapar o card na imagem antes do OCR, para o OCR
     * nunca ler o próprio card, esteja ele no topo, no meio ou na base.
     */
    fun bounds(): Rect? {
        val v = card ?: return null
        if (v.width == 0 || v.height == 0) return null
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    private fun ensureCreated(): RideCardView {
        val position = prefs.cardPosition
        val custom = prefs.cardX >= 0 && prefs.cardY >= 0
        val key = if (custom) "c${prefs.cardX},${prefs.cardY}" else "p$position"

        // Mudou a posição nas configurações: refaz a janela no lugar novo.
        if (card != null && builtPosition != key) hide()
        card?.let { return it }

        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val view = RideCardView(service)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            val horizontal = when (position) {
                1, 4, 7 -> Gravity.START
                2, 5, 8 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            val vertical = when (position) {
                0, 1, 2 -> Gravity.TOP
                6, 7, 8 -> Gravity.BOTTOM
                else -> Gravity.CENTER_VERTICAL
            }
            gravity = horizontal or vertical
            x = if (horizontal == Gravity.CENTER_HORIZONTAL) 0 else dp(8)
            y = when (vertical) {
                Gravity.TOP -> dp(56)
                Gravity.BOTTOM -> dp(40)
                else -> 0
            }
            if (custom) {
                gravity = Gravity.TOP or Gravity.START
                x = prefs.cardX
                y = prefs.cardY
            }
        }
        wm.addView(view, params)
        if (custom) {
            // Se o card ficou maior (tamanho/fonte) e saiu da tela, puxa de volta.
            view.post {
                val dm = service.resources.displayMetrics
                val nx = params.x.coerceIn(0, (dm.widthPixels - view.width).coerceAtLeast(0))
                val ny = params.y.coerceIn(0, (dm.heightPixels - view.height).coerceAtLeast(0))
                if (nx != params.x || ny != params.y) {
                    params.x = nx; params.y = ny
                    try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                }
            }
        }
        card = view
        cardParams = params
        builtPosition = key
        return view
    }
}
