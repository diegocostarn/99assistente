package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager

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
    private var builtPosition = -1

    fun show(ride: RideInfo, prefs: Prefs) {
        ensureCreated().bind(ride, prefs)
    }

    /** Várias corridas na mesma tela (lista de solicitações): uma linha por corrida. */
    fun showMany(rides: List<RideInfo>, prefs: Prefs) {
        ensureCreated().bindMany(rides, prefs)
    }

    /** Card de diagnóstico: aparece quando há leitura de tela mas nenhuma corrida reconhecida. */
    fun showDebug(message: String) {
        ensureCreated().bindDebug(message, prefs)
    }

    fun hide() {
        card?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        card = null
        builtPosition = -1
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

        // Mudou a posição nas configurações: refaz a janela no lugar novo.
        if (card != null && builtPosition != position) hide()
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
        }
        wm.addView(view, params)
        card = view
        builtPosition = position
        return view
    }
}
