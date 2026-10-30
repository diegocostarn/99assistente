package com.assistente99

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import kotlin.math.abs

/**
 * Botão flutuante sobre qualquer app. Arrasta para mudar de lugar; toca para abrir uma janelinha com
 * atalho para as últimas viagens e chaves para ligar/desligar a leitura da Uber e da 99.
 */
class FloatingMenu(
    private val service: AccessibilityService,
    private val prefs: Prefs,
    private val onToggleChanged: () -> Unit
) {
    private val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var restoreX = -1
    private var restoreY = -1

    val isShown: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val screen = service.resources.displayMetrics

        val container = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }

        val button = TextView(service).apply {
            text = "R$"
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE60F172A.toInt())
                setStroke(dp(2), 0xFF22C55E.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
        }
        container.addView(button)

        val p = buildPanel()
        p.visibility = View.GONE
        (p.layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
        container.addView(p)
        panel = p

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.floatX.let { if (it < 0) screen.widthPixels - dp(62) else it }
            y = prefs.floatY.let { if (it < 0) screen.heightPixels / 3 else it }
        }
        params = lp

        // Arrastar = mover; toque curto = abrir/fechar a janelinha.
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        button.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (moved || abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        restoreX = -1
                        lp.x = (startX + dx).toInt().coerceIn(0, screen.widthPixels - dp(50))
                        lp.y = (startY + dy).toInt().coerceIn(0, screen.heightPixels - dp(50))
                        update()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.floatX = lp.x
                        prefs.floatY = lp.y
                    } else togglePanel()
                }
            }
            true
        }
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }

        wm.addView(container, lp)
        root = container
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        panel = null
        params = null
    }

    private fun update() {
        val r = root ?: return
        try { wm.updateViewLayout(r, params) } catch (_: Exception) {}
    }

    private fun togglePanel() {
        val p = panel ?: return
        if (p.visibility == View.VISIBLE) closePanel() else {
            p.visibility = View.VISIBLE
            // Se a janelinha não couber na tela (botão perto da borda), move o conjunto para caber
            // e, ao fechar, o botão volta para onde estava.
            root?.post {
                val lp = params ?: return@post
                val dm = service.resources.displayMetrics
                val w = root?.width ?: return@post
                val h = root?.height ?: return@post
                restoreX = lp.x
                restoreY = lp.y
                if (lp.x + w > dm.widthPixels) lp.x = (dm.widthPixels - w - dp(8)).coerceAtLeast(0)
                if (lp.y + h > dm.heightPixels) lp.y = (dm.heightPixels - h - dp(8)).coerceAtLeast(0)
                if (lp.x != restoreX || lp.y != restoreY) update()
            }
        }
    }

    private fun closePanel() {
        if (panel?.visibility == View.VISIBLE) {
            panel?.visibility = View.GONE
            val lp = params
            if (lp != null && restoreX >= 0) {
                lp.x = restoreX
                lp.y = restoreY
                restoreX = -1
                update()
            }
        }
    }

    private fun buildPanel(): LinearLayout {
        val ink = Pal.ink(service)
        val muted = Pal.muted(service)
        val box = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Pal.card(service))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Pal.line(service))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        box.addView(TextView(service).apply {
            text = "Assistente 99"
            textSize = 15f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })

        box.addView(TextView(service).apply {
            text = "🕘  Últimas viagens"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF16A34A.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                closePanel()
                service.startActivity(
                    Intent(service, RideLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        })

        box.addView(TextView(service).apply {
            text = "LER PEDIDOS DE"
            textSize = 11f
            setTextColor(muted)
            letterSpacing = 0.08f
            setPadding(0, dp(14), 0, dp(2))
        })
        box.addView(switchRow("Uber", prefs.enableUber) { prefs.enableUber = it; onToggleChanged() })
        box.addView(switchRow("99", prefs.enable99) { prefs.enable99 = it; onToggleChanged() })
        return box
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(service).apply {
        text = label
        textSize = 15f
        setTextColor(Pal.ink(service))
        isChecked = checked
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }
}
