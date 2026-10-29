package com.assistente99

/**
 * Resultado da leitura de uma solicitação de corrida.
 */
data class RideInfo(
    val price: Double,
    val pickupKm: Double?,
    val tripKm: Double?,
    val pickupMin: Int?,
    val tripMin: Int?
) {
    /** Km total considerado no cálculo. */
    fun totalKm(includePickup: Boolean): Double {
        val trip = tripKm ?: 0.0
        val pickup = if (includePickup) (pickupKm ?: 0.0) else 0.0
        return trip + pickup
    }

    fun perKm(includePickup: Boolean): Double? {
        val km = totalKm(includePickup)
        return if (km > 0.0) price / km else null
    }

    fun totalMin(includePickup: Boolean): Int {
        return (tripMin ?: 0) + if (includePickup) (pickupMin ?: 0) else 0
    }

    fun perHour(includePickup: Boolean): Double? {
        val min = totalMin(includePickup)
        return if (min > 0) price / min * 60.0 else null
    }
}

/**
 * Extrai preço, distâncias e tempos a partir dos textos visíveis na tela do app.
 *
 * Heurística (o layout do app muda de tempos em tempos):
 *  - preço: primeiro "R$ 12,50" encontrado;
 *  - distâncias: valores em "km" ou "m", na ordem em que aparecem.
 *    Com 2 ou mais, a 1ª é o deslocamento até o passageiro e a 2ª é a viagem.
 *    Com apenas 1, ela é tratada como viagem;
 *  - tempos: valores em "min", mesma regra de ordem.
 */
object RideParser {

    private val priceRegex = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)""")
    private val distRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    // Aceita "4 min" (99) e "4 minutos" (Uber).
    private val minRegex = Regex("""(\d+)\s*min(?:utos?)?\b""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): RideInfo? {
        val joined = texts.joinToString(" | ")

        val price = priceRegex.find(joined)?.groupValues?.get(1)?.let { parseBrNumber(it) }
            ?: return null

        val distances = distRegex.findAll(joined).mapNotNull { m ->
            val value = parseBrNumber(m.groupValues[1]) ?: return@mapNotNull null
            if (m.groupValues[2].equals("m", ignoreCase = true)) value / 1000.0 else value
        }.toList()
        if (distances.isEmpty()) return null

        val minutes = minRegex.findAll(joined).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        val pickupKm: Double?
        val tripKm: Double?
        if (distances.size >= 2) {
            pickupKm = distances[0]
            tripKm = distances[1]
        } else {
            pickupKm = null
            tripKm = distances[0]
        }

        val pickupMin: Int?
        val tripMin: Int?
        if (minutes.size >= 2) {
            pickupMin = minutes[0]
            tripMin = minutes[1]
        } else {
            pickupMin = null
            tripMin = minutes.firstOrNull()
        }

        return RideInfo(price, pickupKm, tripKm, pickupMin, tripMin)
    }

    /** Converte "1.234,56" ou "12,5" ou "3.4" em Double. */
    private fun parseBrNumber(s: String): Double? {
        val clean = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
        return clean.toDoubleOrNull()
    }
}
