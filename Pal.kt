package com.assistente99

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Cores do tema claro/escuro, conforme a escolha em [Prefs.themeMode] (0 automático, 1 claro, 2 escuro). */
object Pal {
    fun dark(ctx: Context): Boolean = when (Prefs(ctx).themeMode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun bg(ctx: Context) = if (dark(ctx)) 0xFF0B0F17.toInt() else 0xFFF2F4F7.toInt()
    fun card(ctx: Context) = if (dark(ctx)) 0xFF161C28.toInt() else 0xFFFFFFFF.toInt()
    fun line(ctx: Context) = if (dark(ctx)) 0xFF2A3342.toInt() else 0xFFE5E7EB.toInt()
    fun ink(ctx: Context) = if (dark(ctx)) 0xFFF3F4F6.toInt() else 0xFF111827.toInt()
    fun muted(ctx: Context) = if (dark(ctx)) 0xFF9CA3AF.toInt() else 0xFF6B7280.toInt()
    fun field(ctx: Context) = if (dark(ctx)) 0xFF222B3A.toInt() else 0xFFF3F4F6.toInt()

    /** Aplica a escolha ao app todo (as telas se refazem sozinhas). */
    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs(ctx).themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
