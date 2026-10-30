package com.assistente99

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * "Fechar o app": o serviço de acessibilidade continua ativado no Android (só o usuário pode desligá-lo),
 * mas o app fica em pausa: sem botão flutuante, sem card, sem leitura. Abrir o app de novo retoma tudo.
 */
object AppState {
    @Volatile var paused = false
    @Volatile var pausedAt = 0L

    private val activities = ArrayList<WeakReference<Activity>>()

    fun track(a: Activity) {
        activities.removeAll { it.get() == null }
        activities.add(WeakReference(a))
    }

    fun untrack(a: Activity) {
        activities.removeAll { it.get() == null || it.get() === a }
    }

    /** Fecha todas as telas abertas do app. */
    fun finishAll() {
        activities.mapNotNull { it.get() }.forEach { it.finishAndRemoveTask() }
        activities.clear()
    }
}
