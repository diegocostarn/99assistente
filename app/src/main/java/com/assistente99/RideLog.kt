package com.assistente99

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Histórico das últimas 10 solicitações de corrida, guardado só no aparelho.
 *
 * A mesma solicitação é lida várias vezes por segundo enquanto está na tela. Para não encher o
 * histórico, uma leitura do mesmo app com o mesmo preço, dentro de [DEDUP_MS], atualiza a
 * entrada existente em vez de criar outra.
 */
class RideLog(context: Context) {

    data class Entry(
        val time: Long,
        val source: String,
        val price: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val pickupMin: Int?,
        val tripMin: Int?,
        val perKm: Double?,
        val pickupAddress: String?,
        val dropoffAddress: String?,
        val rating: Double? = null,
        val rides: Int? = null
    )

    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("assistente99_log", Context.MODE_PRIVATE)

    fun all(): List<Entry> = synchronized(LOCK) { read() }

    fun clear() {
        synchronized(LOCK) { sp.edit().remove(KEY).apply() }
        RideNotifier.cancel(appContext)
    }

    /** Registra (ou atualiza) uma solicitação. [source] é "Uber" ou "99". */
    fun record(source: String, ride: RideInfo, includePickup: Boolean) {
        if (ride.tripKm == null || ride.price <= 0.0) return
        synchronized(LOCK) {
            val list = read().toMutableList()
            var shown: Entry? = null
            var isNew = false
            val now = System.currentTimeMillis()
            val i = list.indexOfFirst {
                it.source == source && abs(it.price - ride.price) < 0.005 && now - it.time < DEDUP_MS
            }
            if (i >= 0) {
                val old = list[i]
                // Leituras que trazem a estimativa da Uber já foram conferidas: essas podem corrigir os números.
                val confirmed = ride.estPerKm != null
                val pickupKm = if (confirmed) ride.pickupKm else (old.pickupKm ?: ride.pickupKm)
                val tripKm = if (confirmed) ride.tripKm else (old.tripKm ?: ride.tripKm)
                list[i] = old.copy(
                    // O horário passa a ser o da última leitura: uma solicitação que fica muito
                    // tempo na tela continua sendo a mesma entrada.
                    time = now,
                    pickupKm = pickupKm,
                    tripKm = tripKm,
                    pickupMin = if (confirmed) ride.pickupMin else (old.pickupMin ?: ride.pickupMin),
                    tripMin = if (confirmed) ride.tripMin else (old.tripMin ?: ride.tripMin),
                    perKm = perKmOf(old.price, pickupKm, tripKm, includePickup),
                    pickupAddress = longer(old.pickupAddress, ride.pickupAddress),
                    dropoffAddress = longer(old.dropoffAddress, ride.dropoffAddress),
                    rating = ride.rating ?: old.rating,
                    rides = ride.rides ?: old.rides
                )
                shown = list[i]
            } else {
                list.add(
                    0,
                    Entry(
                        time = now,
                        source = source,
                        price = ride.price,
                        pickupKm = ride.pickupKm,
                        tripKm = ride.tripKm,
                        pickupMin = ride.pickupMin,
                        tripMin = ride.tripMin,
                        perKm = ride.perKm(includePickup),
                        pickupAddress = ride.pickupAddress,
                        dropoffAddress = ride.dropoffAddress,
                        rating = ride.rating,
                        rides = ride.rides
                    )
                )
                shown = list[0]
                isNew = true
            }
            write(list.take(MAX))
            // Atalho na barra de notificações com a corrida que acabou de ser lida.
            shown?.let { RideNotifier.onRecorded(appContext, it, isNew) }
        }
    }

    private fun perKmOf(price: Double, pickupKm: Double?, tripKm: Double?, includePickup: Boolean): Double? {
        val km = (tripKm ?: 0.0) + if (includePickup) (pickupKm ?: 0.0) else 0.0
        return if (km > 0.0) price / km else null
    }

    private fun longer(a: String?, b: String?): String? =
        if ((b?.length ?: 0) > (a?.length ?: 0)) b else a

    private fun write(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("time", e.time)
            o.put("source", e.source)
            o.put("price", e.price)
            e.pickupKm?.let { o.put("pickupKm", it) }
            e.tripKm?.let { o.put("tripKm", it) }
            e.pickupMin?.let { o.put("pickupMin", it) }
            e.tripMin?.let { o.put("tripMin", it) }
            e.perKm?.let { o.put("perKm", it) }
            e.pickupAddress?.let { o.put("pickupAddress", it) }
            e.dropoffAddress?.let { o.put("dropoffAddress", it) }
            e.rating?.let { o.put("rating", it) }
            e.rides?.let { o.put("rides", it) }
            arr.put(o)
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private fun read(): List<Entry> {
        val json = sp.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { n ->
                val o = arr.getJSONObject(n)
                Entry(
                    time = o.getLong("time"),
                    source = o.getString("source"),
                    price = o.getDouble("price"),
                    pickupKm = o.dbl("pickupKm"),
                    tripKm = o.dbl("tripKm"),
                    pickupMin = o.int("pickupMin"),
                    tripMin = o.int("tripMin"),
                    perKm = o.dbl("perKm"),
                    pickupAddress = o.str("pickupAddress"),
                    dropoffAddress = o.str("dropoffAddress"),
                    rating = o.dbl("rating"),
                    rides = o.int("rides")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    companion object {
        const val MAX = 10

        /** Mesma solicitação (mesmo app e preço) lida de novo dentro deste prazo = mesma entrada. */
        private const val DEDUP_MS = 90_000L

        private const val KEY = "entries"
        private val LOCK = Any()

        /** Nome do app de origem a partir do pacote. */
        fun sourceLabel(pkg: String): String = when (pkg) {
            "com.ubercab.driver" -> "Uber"
            "com.app99.driver" -> "99"
            else -> pkg.substringAfterLast('.').ifBlank { "?" }
        }
    }
}
