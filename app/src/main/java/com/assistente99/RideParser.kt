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
 * Layout do radar da Uber (e semelhante na 99):
 *   "R$12,01"                      -> preço da corrida
 *   "Est. de R$1,15/km"            -> estimativa do próprio app (ignorada)
 *   "+R$ 1,98 incluída para ..."   -> bônus já somado ao preço (ignorado)
 *   "8 min (4.3 km)"               -> 1º par: deslocamento até o passageiro
 *   "10 minutos (6.1 km)"          -> 2º par: viagem
 *
 * Sem pares "min (km)", usa a ordem das distâncias soltas: 1ª = passageiro, 2ª = viagem
 * (se houver só uma, é a viagem).
 */
object RideParser {

    private val priceRegex = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)""")
    private val priceOnlyRegex = Regex("""^R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)$""")
    private val distRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)

    // Aceita "4 min" (99) e "4 minutos" (Uber).
    private val minRegex = Regex("""(\d+)\s*min(?:utos?)?\b""", RegexOption.IGNORE_CASE)

    // "8 min (4.3 km)", "10 minutos (6,1 km)", "6 minutos (2,1 km) de distância".
    private val pairRegex = Regex(
        """(\d+)\s*min(?:utos?)?\s*\(\s*(\d+(?:[.,]\d+)?)\s*(km|m)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawTexts: List<String>): RideInfo? {
        // Formatação de moeda e unidades costuma usar espaço não separável (U+00A0),
        // que o "\s" do regex não reconhece. Troca por espaço comum antes de tudo.
        val texts = rawTexts.map { normalize(it) }

        val price = findPrice(texts) ?: return null
        val joined = texts.joinToString(" | ")

        val pairs = pairRegex.findAll(joined).mapNotNull { m ->
            val min = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val km = toKm(m.groupValues[2], m.groupValues[3]) ?: return@mapNotNull null
            min to km
        }.toList()

        if (pairs.isNotEmpty()) {
            return if (pairs.size >= 2) {
                RideInfo(price, pairs[0].second, pairs[1].second, pairs[0].first, pairs[1].first)
            } else {
                RideInfo(price, null, pairs[0].second, null, pairs[0].first)
            }
        }

        val distances = distRegex.findAll(joined).mapNotNull { m ->
            toKm(m.groupValues[1], m.groupValues[2])
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

    /**
     * O preço é o texto que é só "R$ xx,xx". Se não houver, pega o primeiro "R$" de um texto
     * que não seja estimativa por km, bônus ("+R$ ...") nem "incluída".
     */
    private fun findPrice(texts: List<String>): Double? {
        for (t in texts) {
            priceOnlyRegex.find(t.trim())?.let { return parseBrNumber(it.groupValues[1]) }
        }
        for (t in texts) {
            val lower = t.lowercase()
            if ("/km" in lower || "incluíd" in lower || "est." in lower || t.trimStart().startsWith("+")) continue
            priceRegex.find(t)?.let { return parseBrNumber(it.groupValues[1]) }
        }
        return null
    }

    private fun normalize(s: String): String =
        s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    private fun toKm(number: String, unit: String): Double? {
        val value = parseBrNumber(number) ?: return null
        return if (unit.equals("m", ignoreCase = true)) value / 1000.0 else value
    }

    /** Converte "1.234,56", "12,5" ou "4.3" em Double. */
    private fun parseBrNumber(s: String): Double? {
        val clean = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
        return clean.toDoubleOrNull()
    }
}
