package com.assistente99

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notificação silenciosa com a última corrida: fica na barra de notificações como atalho rápido,
 * sem som, sem vibração e sem aparecer por cima da tela. Mostra o que também aparece nas
 * "Últimas viagens" e tem botões para abrir o embarque e o destino no mapa.
 */
object RideNotifier {

    private const val CHANNEL_ID = "ultima_corrida"
    private const val NOTIFICATION_ID = 100

    private val br = Locale("pt", "BR")

    // Evita repostar a notificação a cada leitura da mesma corrida (o app lê várias vezes por segundo).
    private var lastKey = ""
    private var lastFingerprint = ""

    /**
     * Chamado quando uma corrida é registrada no histórico. [isNew] = corrida nova (não uma releitura
     * da que já estava no histórico). Releituras só atualizam a notificação se for a mesma corrida
     * que ela já mostra e algo mudou (por exemplo, o endereço foi lido depois).
     */
    fun onRecorded(context: Context, e: RideLog.Entry, isNew: Boolean) {
        try {
            if (!Prefs(context).notifyLastRide) return
            val key = "${e.source}|${String.format(Locale.US, "%.2f", e.price)}"
            val fingerprint = listOf(
                e.perKm?.let { String.format(Locale.US, "%.2f", it) }, e.pickupKm, e.tripKm,
                e.pickupMin, e.tripMin, e.pickupAddress, e.dropoffAddress
            ).joinToString("|")
            val same = key == lastKey
            if (!isNew && !same) return
            if (same && fingerprint == lastFingerprint) return
            post(context, e)
            lastKey = key
            lastFingerprint = fingerprint
        } catch (_: Exception) {
            // Notificação é só um atalho: nunca deve atrapalhar a leitura das corridas.
        }
    }

    /** Remove a notificação (usado quando o histórico é apagado). */
    fun cancel(context: Context) {
        lastKey = ""
        lastFingerprint = ""
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, e: RideLog.Entry) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(context)

        val perKm = e.perKm?.let { "R$ ${fmt(it)}/km" } ?: "R$ --/km"
        val title = "${e.source.uppercase(br)} • $perKm • R$ ${fmt(e.price)}"

        val a = e.pickupMin
        val b = e.tripMin
        val tempo = when {
            a != null && b != null -> "$a min até o passageiro + $b min de viagem = ${a + b} min"
            b != null -> "$b min de viagem"
            else -> null
        }
        val ka = e.pickupKm
        val kb = e.tripKm
        val distancia = when {
            ka != null && kb != null -> "${fmt(ka)} km + ${fmt(kb)} km = ${fmt(ka + kb)} km"
            kb != null -> "${fmt(kb)} km"
            else -> null
        }

        // Linha curta (notificação fechada).
        val short = listOfNotNull(
            if (a != null && b != null) "${a + b} min" else b?.let { "$it min" },
            if (ka != null && kb != null) "${fmt(ka + kb)} km" else kb?.let { "${fmt(it)} km" }
        ).joinToString(" • ").ifEmpty { "Toque para ver as últimas viagens" }

        // Texto completo (notificação aberta).
        val big = buildString {
            tempo?.let { append("Tempo: $it\n") }
            distancia?.let { append("Distância: $it\n") }
            append("Embarque: ${e.pickupAddress ?: "endereço não lido"}\n")
            append("Destino: ${e.dropoffAddress ?: "endereço não lido"}")
        }

        val openLog = PendingIntent.getActivity(
            context, 0,
            Intent(context, RideLogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText(short)
            .setSubText(SimpleDateFormat("dd/MM HH:mm", br).format(Date(e.time)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(openLog)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)

        e.pickupAddress?.let { builder.addAction(0, "Embarque no mapa", mapIntent(context, 1, it)) }
        e.dropoffAddress?.let { builder.addAction(0, "Destino no mapa", mapIntent(context, 2, it)) }

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    /** Abre o endereço no app de mapas do celular (Google Maps, Waze etc., conforme o aparelho). */
    private fun mapIntent(context: Context, requestCode: Int, address: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW: aparece só na barra de notificações, sem som e sem pop-up na tela.
        val channel = NotificationChannel(CHANNEL_ID, "Última corrida", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Atalho silencioso com a última solicitação e botões para abrir o mapa"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun fmt(v: Double): String = String.format(br, "%.2f", v)
}
