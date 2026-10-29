package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Card flutuante mostrado sobre o app da 99.
 * Usa TYPE_ACCESSIBILITY_OVERLAY, então não precisa da permissão "Sobrepor a outros apps".
 * O card não captura toques, para não atrapalhar o botão de aceitar da corrida.
 */
class OverlayView(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var line1: TextView? = null
    private var line2: TextView? = null
    private var bg: GradientDrawable? = null

    fun show(ride: RideInfo, prefs: Prefs) {
        ensureCreated()

        val includePickup = prefs.includePickup
        val perKm = ride.perKm(includePickup)
        val net = perKm?.let { it - prefs.costPerKm }
        val shown = if (prefs.costPerKm > 0f) net else perKm

        val color = when {
            shown == null -> COLOR_NEUTRAL
            shown >= prefs.goodPerKm -> COLOR_GOOD
            shown >= prefs.minPerKm -> COLOR_OK
            else -> COLOR_BAD
        }
        bg?.setColor(color)

        title?.text = if (perKm != null) "R$ ${fmt(perKm)} / km" else "R$ -- / km"

        val kmTotal = ride.totalKm(includePickup)
        line1?.text = "R$ ${fmt(ride.price)} • ${fmt(kmTotal)} km" +
            (if (includePickup && ride.pickupKm != null)
                " (${fmt(ride.pickupKm)} + ${fmt(ride.tripKm ?: 0.0)})" else "")

        val parts = mutableListOf<String>()
        ride.perHour(includePickup)?.let { parts += "R$ ${fmt(it)}/h" }
        if (prefs.costPerKm > 0f && net != null) {
            parts += "lucro R$ ${fmt(net * kmTotal)}"
        }
        line2?.text = parts.joinToString(" • ")
        line2?.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    fun hide() {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
    }

    private fun ensureCreated() {
        if (root != null) return

        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        bg = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(COLOR_NEUTRAL)
        }

        title = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        line1 = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        line2 = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(title)
            addView(line1)
            addView(line2)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(80)
        }
        wm.addView(root, params)
    }

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "%.2f", v)

    companion object {
        private val COLOR_GOOD = Color.parseColor("#E6137A3A")
        private val COLOR_OK = Color.parseColor("#E6B7791F")
        private val COLOR_BAD = Color.parseColor("#E6B3261E")
        private val COLOR_NEUTRAL = Color.parseColor("#E6333333")
    }
}
