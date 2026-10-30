package com.assistente99

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * "Essa é boa": avisa quando a solicitação atende ao que o motorista definiu
 * (R$/km mínimo e distância máxima até o passageiro) e, se ligado, aceita sozinho.
 */
object GoodAlert {

    /**
     * A corrida é boa se o R$/km (calculado como no card) chega ao mínimo e o passageiro está a no máximo
     * [maxPickupKm]. Se o km até o passageiro não foi lido, não conta como boa (não dá para garantir).
     */
    fun isGood(ride: RideInfo, minPerKm: Double, maxPickupKm: Double, includePickup: Boolean): Boolean {
        val perKm = ride.perKm(includePickup) ?: return false
        val pickup = ride.pickupKm ?: return false
        return perKm >= minPerKm && pickup <= maxPickupKm
    }

    fun isGood(ride: RideInfo, prefs: Prefs): Boolean =
        isGood(ride, prefs.goodMinPerKm.toDouble(), prefs.goodMaxPickupKm.toDouble(), prefs.includePickup)

    // Criado só quando precisa tocar o aviso (nos testes de unidade o Looper do Android não existe).
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Dois bipes no volume de alarme (toca mesmo com o celular no silencioso das notificações). */
    fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            handler.postDelayed({
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300) } catch (_: Exception) {}
            }, 280)
            handler.postDelayed({ try { tone.release() } catch (_: Exception) {} }, 900)
        } catch (_: Exception) {
        }
    }
}
