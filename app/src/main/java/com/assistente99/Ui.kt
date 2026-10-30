package com.assistente99

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * Peças de interface usadas em TODAS as telas do app, para manterem o mesmo visual:
 * cabeçalho escuro, cartões arredondados, botões verdes e seletores iguais.
 */
object Ui {
    val HEADER = 0xFF0F172A.toInt()
    val HEADER_END = 0xFF1F3A5F.toInt()
    val ACCENT = 0xFF16A34A.toInt()
    val GOOD = 0xFF22C55E.toInt()
    val BAD = 0xFFEF4444.toInt()
    val DANGER = 0xFFB91C1C.toInt()
    val GRAY = 0xFF6B7280.toInt()

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun round(ctx: Context, color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(ctx, 1), strokeColor)
    }

    fun text(ctx: Context, t: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** Cartão branco (ou escuro, no tema escuro) com borda fina. */
    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = round(ctx, Pal.card(ctx), 18, Pal.line(ctx))
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 12) }
    }

    fun sectionTitle(ctx: Context, t: String) = text(ctx, t, 12f, Pal.muted(ctx), true).apply {
        letterSpacing = 0.08f
        setPadding(dp(ctx, 4), dp(ctx, 22), 0, 0)
    }

    fun fieldLabel(ctx: Context, t: String) =
        text(ctx, t, 13f, Pal.muted(ctx)).apply { setPadding(0, dp(ctx, 12), 0, dp(ctx, 4)) }

    /** Botão cheio, largura total. */
    fun button(ctx: Context, t: String, color: Int = ACCENT, onClick: () -> Unit) = Button(ctx).apply {
        text = t
        transformationMethod = null
        textSize = 15f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = round(ctx, color, 14)
        stateListAnimator = null
        minHeight = dp(ctx, 48)
        minimumHeight = dp(ctx, 48)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 10) }
        setOnClickListener { onClick() }
    }

    /** Botão de contorno verde, para ações secundárias. */
    fun outlineButton(ctx: Context, t: String, onClick: () -> Unit) = Button(ctx).apply {
        text = t
        transformationMethod = null
        textSize = 14f
        setTextColor(if (Pal.dark(ctx)) 0xFF4CD07D.toInt() else ACCENT)
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(ctx, 12).toFloat()
            setStroke(dp(ctx, 2), ACCENT)
        }
        stateListAnimator = null
        minHeight = dp(ctx, 44)
        minimumHeight = dp(ctx, 44)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 8) }
        setOnClickListener { onClick() }
    }

    /** Linha clicável de atalho: emoji, título, subtítulo e seta. */
    fun tile(ctx: Context, emoji: String, title: String, sub: String, onClick: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = round(ctx, Pal.card(ctx), 18, Pal.line(ctx))
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 10) }
        isClickable = true
        isFocusable = true
        addView(text(ctx, emoji, 26f, Pal.ink(ctx)).apply { setPadding(0, 0, dp(ctx, 14), 0) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(ctx, title, 16f, Pal.ink(ctx), true))
            addView(text(ctx, sub, 13f, Pal.muted(ctx)))
        })
        addView(text(ctx, "›", 26f, Pal.muted(ctx)))
        setOnClickListener { onClick() }
    }

    /** Interruptor com título em negrito e subtítulo menor. */
    fun switchRow(ctx: Context, t: String, sub: String, checked: Boolean): SwitchCompat = SwitchCompat(ctx).apply {
        isChecked = checked
        textSize = 15f
        setTextColor(Pal.ink(ctx))
        setPadding(0, dp(ctx, 10), 0, dp(ctx, 10))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val s = android.text.SpannableString("$t\n$sub")
        s.setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, t.length, 0)
        s.setSpan(android.text.style.RelativeSizeSpan(0.82f), t.length + 1, s.length, 0)
        s.setSpan(android.text.style.ForegroundColorSpan(Pal.muted(ctx)), t.length + 1, s.length, 0)
        text = s
    }

    fun numberField(ctx: Context, value: Float) = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(if (value == 0f) "" else value.toString())
        setTextColor(Pal.ink(ctx))
        textSize = 16f
        background = round(ctx, Pal.field(ctx), 12)
        setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
    }

    /** Seletor com 2 a 4 opções lado a lado (ex.: Automático / Claro / Escuro). */
    fun segmented(ctx: Context, options: List<String>, selected: Int, onSelect: (Int) -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 4))
            options.forEachIndexed { i, name ->
                addView(Button(ctx).apply {
                    text = name
                    transformationMethod = null
                    textSize = 14f
                    stateListAnimator = null
                    val sel = i == selected
                    setTextColor(if (sel) Color.WHITE else Pal.ink(ctx))
                    background = round(ctx, if (sel) ACCENT else Pal.field(ctx), 12)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i > 0) marginStart = dp(ctx, 8)
                    }
                    setOnClickListener { onSelect(i) }
                })
            }
        }

    /** Barra deslizante com a cor do app. */
    fun seek(ctx: Context, max: Int, progress: Int, onChange: (Int) -> Unit) = SeekBar(ctx).apply {
        this.max = max
        this.progress = progress
        progressTintList = ColorStateList.valueOf(ACCENT)
        thumbTintList = ColorStateList.valueOf(ACCENT)
        setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /** Selo do app de origem: Uber preto, 99 laranja. */
    fun sourcePill(ctx: Context, source: String) = TextView(ctx).apply {
        text = source.uppercase()
        textSize = 12f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.06f
        setPadding(dp(ctx, 10), dp(ctx, 3), dp(ctx, 10), dp(ctx, 3))
        background = round(ctx, RideCardView.brandColor(source), 20)
    }

    /**
     * Estrutura padrão de uma tela interna: cabeçalho escuro com seta de voltar e título,
     * e o conteúdo rolável embaixo. Devolve o LinearLayout onde o conteúdo deve ser colocado.
     */
    fun screen(activity: Activity, title: String, subtitle: String? = null): LinearLayout {
        activity.window.statusBarColor = HEADER
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.bg(activity))
        }
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(HEADER, HEADER_END)).apply {
                val r = dp(activity, 28).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 22))
            addView(text(activity, "←  Voltar", 14f, 0xFFB7C4D6.toInt()).apply {
                setPadding(0, 0, dp(activity, 24), dp(activity, 10))
                setOnClickListener { activity.finish() }
            })
            addView(text(activity, title, 26f, Color.WHITE, true))
            if (subtitle != null) addView(text(activity, subtitle, 14f, 0xFFB7C4D6.toInt()).apply {
                setPadding(0, dp(activity, 4), 0, 0)
            })
        })
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 4), dp(activity, 16), dp(activity, 28))
        }
        root.addView(ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(content)
        })
        activity.setContentView(root)
        return content
    }

    @Suppress("unused")
    fun spacer(ctx: Context, h: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, h))
    }
}
