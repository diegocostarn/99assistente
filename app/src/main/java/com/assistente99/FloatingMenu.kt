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
 * Botão flutuante sobre qualquer app. Pode ser arrastado, mas ao soltar ele gruda na lateral mais próxima
 * (esquerda ou direita). Ao tocar, a janelinha de opções abre para o lado de dentro da tela:
 * para a esquerda se o botão está na direita, e para a direita se está na esquerda.
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
    private var button: TextView? = null
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var side = 1
    private var restoreY = -1

    val isShown: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val screen = service.resources.displayMetrics
        side = prefs.floatSide

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }

        val btn = TextView(service).apply {
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
        val p = buildPanel()
        p.visibility = View.GONE
        button = btn
        panel = p
        root = container
        arrange()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or edgeGravity()
            x = dp(2)
            y = prefs.floatY.let { if (it < 0) screen.heightPixels / 3 else it }
        }
        params = lp

        // Arrastar = mover (ao soltar, gruda na lateral); toque curto = abrir/fechar a janelinha.
        var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0; var moved = false
        btn.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; moved = false
                    val loc = IntArray(2)
                    btn.getLocationOnScreen(loc)
                    startLeft = loc[0]
                    startTop = lp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > dp(6) || abs(dy) > dp(6))) {
                        moved = true
                        // Fecha a janelinha e passa a mover por coordenadas absolutas.
                        panel?.visibility = View.GONE
                        restoreY = -1
                        lp.gravity = Gravity.TOP or Gravity.START
                    }
                    if (moved) {
                        lp.x = (startLeft + dx).toInt().coerceIn(0, screen.widthPixels - dp(50))
                        lp.y = (startTop + dy).toInt().coerceIn(0, screen.heightPixels - dp(50))
                        update()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snapToEdge() else togglePanel()
                }
            }
            true
        }
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }

        wm.addView(container, lp)
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        button = null
        panel = null
        params = null
    }

    private fun edgeGravity() = if (side == 1) Gravity.END else Gravity.START

    /** Ordem dos itens: o botão fica na ponta junto à lateral e a janelinha do lado de dentro. */
    private fun arrange() {
        val c = root ?: return
        val b = button ?: return
        val p = panel ?: return
        c.removeAllViews()
        val gap = dp(8)
        if (side == 1) {
            c.addView(p, LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = gap })
            c.addView(b)
        } else {
            c.addView(b)
            c.addView(p, LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        }
    }

    /** Soltou o botão: escolhe a lateral mais próxima e grudo nela. */
    private fun snapToEdge() {
        val lp = params ?: return
        val screen = service.resources.displayMetrics
        side = if (lp.x + dp(25) < screen.widthPixels / 2) 0 else 1
        lp.gravity = Gravity.TOP or edgeGravity()
        lp.x = dp(2)
        arrange()
        update()
        prefs.floatSide = side
        prefs.floatY = lp.y
    }

    private fun update() {
        val r = root ?: return
        try { wm.updateViewLayout(r, params) } catch (_: Exception) {}
    }

    private fun togglePanel() {
        val p = panel ?: return
        if (p.visibility == View.VISIBLE) {
            closePanel()
            return
        }
        p.visibility = View.VISIBLE
        // Se a janelinha for mais alta que o espaço abaixo do botão, sobe o conjunto; ao fechar volta.
        root?.post {
            val lp = params ?: return@post
            val screenH = service.resources.displayMetrics.heightPixels
            val h = root?.height ?: return@post
            if (lp.y + h > screenH) {
                restoreY = lp.y
                lp.y = (screenH - h - dp(8)).coerceAtLeast(0)
                update()
            }
        }
    }

    private fun closePanel() {
        val p = panel ?: return
        if (p.visibility != View.VISIBLE) return
        p.visibility = View.GONE
        val lp = params
        if (lp != null && restoreY >= 0) {
            lp.y = restoreY
            restoreY = -1
            update()
        }
    }

    private fun buildPanel(): LinearLayout {
        val ink = Pal.ink(service)
        val muted = Pal.muted(service)
        val box = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Pal.card(service))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Pal.line(service))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(TextView(service).apply {
            text = "Assistente 99"
            textSize = 15f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })

        box.addView(actionButton("🕘  Últimas viagens", true) { open(RideLogActivity::class.java) })
        box.addView(actionButton("Abrir o aplicativo", false) { open(MainActivity::class.java) })

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

    private fun open(target: Class<*>) {
        closePanel()
        service.startActivity(Intent(service, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Mesmo estilo dos botões do app: cheio e verde, ou só o contorno verde. */
    private fun actionButton(label: String, filled: Boolean, onClick: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 15f
        setTextColor(if (filled) Color.WHITE else Pal.ink(service))
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            if (filled) setColor(0xFF16A34A.toInt()) else {
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), 0xFF16A34A.toInt())
            }
        }
        setPadding(dp(12), dp(11), dp(12), dp(11))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
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
