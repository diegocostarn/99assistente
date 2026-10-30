package com.assistente99

/**
 * Resultado da leitura de uma solicitação de corrida.
 */
data class RideInfo(
    val price: Double,
    val pickupKm: Double?,
    val tripKm: Double?,
    val pickupMin: Int?,
    val tripMin: Int?,
    /** "Est. de R$ X/km" que a própria Uber mostra no card (usado só para conferir a leitura do OCR). */
    val estPerKm: Double? = null,
    /** Endereço de embarque (lido do card, quando o app mostra). */
    val pickupAddress: String? = null,
    /** Endereço de destino (lido do card, quando o app mostra). */
    val dropoffAddress: String? = null
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

    // Linha que COMEÇA com o preço, mesmo que venha mais coisa depois na mesma linha
    // ("R$24,60 100% dos ganhos", ou "R$7,10 100% dos ganhos R$1,36/km" quando o OCR junta tudo).
    // Não vale "R$1,36/km" (estimativa por km), nem "+R$ ..." (bônus).
    private val priceStartRegex = Regex(
        """^R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)(?![\d.,]|\s*/\s*km)""",
        RegexOption.IGNORE_CASE
    )

    // O mesmo, mas em qualquer ponto de um texto grande (quando a tela entrega tudo num bloco só).
    private val priceInTextRegex = Regex(
        """(?<![+\d])R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)(?![\d.,]|\s*/\s*km)""",
        RegexOption.IGNORE_CASE
    )

    private val distRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)

    // Aceita "4 min" (99) e "4 minutos" (Uber).
    private val minRegex = Regex("""(\d+)\s*min(?:utos?)?\b""", RegexOption.IGNORE_CASE)

    // "8 min (4.3 km)", "10 minutos (6,1 km)", "6 minutos (2,1 km) de distância".
    // Parênteses opcionais: o OCR às vezes não os reconhece.
    private val pairRegex = Regex(
        """(\d+)\s*min(?:utos?)?\s*\(?\s*(\d+(?:[.,]\d+)?)\s*(km|m)\b\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    // O OCR costuma ler "R$" como "RS" ou "R5".
    private val ocrCurrencyRegex = Regex("""\bR[S5]\s?(?=\d)""")

    // Números lidos pelo OCR com letras parecidas no lugar de dígitos: "8.l km" (8.1), "l,00" (1,00).
    private val ocrNumberTokenRegex = Regex("""(?<!\p{L})[0-9lIO]+(?:[.,][0-9lIO]+)?(?!\p{L})""")

    /** Troca l/I por 1 e O por 0, mas só em "números" que já têm pelo menos um dígito de verdade. */
    private fun fixOcrDigits(line: String): String = ocrNumberTokenRegex.replace(line) { m ->
        val t = m.value
        if (t.any { it.isDigit() } && t.any { it == 'l' || it == 'I' || it == 'O' }) {
            t.map { c -> when (c) { 'l', 'I' -> '1'; 'O' -> '0'; else -> c } }.joinToString("")
        } else {
            t
        }
    }

    // "Est. de R$1,15/km": estimativa da Uber, já com o km até o passageiro e o bônus.
    private val estPerKmRegex = Regex("""R\$\s*(\d+(?:[.,]\d{1,2})?)\s*/\s*km""", RegexOption.IGNORE_CASE)

    /** Diferença máxima aceita entre o R$/km calculado e a estimativa da Uber. */
    private const val EST_TOLERANCE = 0.15

    /** Lê as linhas do OCR e corrige os erros típicos, SEM conferir com a estimativa da Uber. */
    fun parseOcrRaw(lines: List<String>): RideInfo? =
        parse(lines.map { line -> fixOcrDigits(ocrCurrencyRegex.replace(line) { "R\$ " }) })

    /**
     * A leitura é plausível? Se o card trouxe a estimativa da Uber e o nosso R$/km diverge dela
     * em mais de 15%, algum número foi mal lido pelo OCR (preço ou km).
     */
    fun isPlausible(ride: RideInfo): Boolean {
        val est = ride.estPerKm ?: return true
        if (est <= 0.0) return true
        val computed = ride.perKm(true) ?: return false
        return kotlin.math.abs(computed - est) / est <= EST_TOLERANCE
    }

    /** Como [parseOcrRaw], mas devolve null se a leitura não bater com a estimativa da Uber. */
    fun parseOcr(lines: List<String>): RideInfo? =
        parseOcrRaw(lines)?.takeIf { isPlausible(it) }

    fun parse(rawTexts: List<String>): RideInfo? {
        // Formatação de moeda e unidades costuma usar espaço não separável (U+00A0),
        // que o "\s" do regex não reconhece. Troca por espaço comum antes de tudo.
        val texts = rawTexts.map { normalize(it) }

        val price = findPrice(texts) ?: return null
        val joined = texts.joinToString(" | ")
        val est = estPerKmRegex.find(joined)?.groupValues?.get(1)?.let { parseBrNumber(it) }
        val addresses = extractAddresses(texts)

        val pairs = pairRegex.findAll(joined).mapNotNull { m ->
            val min = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val km = toKm(m.groupValues[2], m.groupValues[3]) ?: return@mapNotNull null
            min to km
        }.toList()

        if (pairs.isNotEmpty()) {
            return if (pairs.size >= 2) {
                RideInfo(price, pairs[0].second, pairs[1].second, pairs[0].first, pairs[1].first, est, addresses.first, addresses.second)
            } else {
                RideInfo(price, null, pairs[0].second, null, pairs[0].first, est, null, addresses.second)
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

        return RideInfo(price, pickupKm, tripKm, pickupMin, tripMin, est)
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
            priceStartRegex.find(t.trim())?.let { return parseBrNumber(it.groupValues[1]) }
        }
        for (t in texts) {
            val lower = t.lowercase()
            if ("/km" in lower || "incluíd" in lower || "est." in lower || t.trimStart().startsWith("+")) continue
            priceRegex.find(t)?.let { return parseBrNumber(it.groupValues[1]) }
        }
        return null
    }

    // Linhas que não fazem parte de um endereço, mas aparecem entre os blocos do card.
    private val skipWords = listOf(
        "verificado", "priority", "exclusivo", "incluíd", "est. de", "radar de viagens",
        "100% dos ganho", "corridas", "taxa99", "perfil ", "cartão verif", "pgto. no app"
    )
    private val endWords = listOf(
        "tenho interesse", "aceitar", "viagem longa", "selecionar", "recusar", "ver detalhes",
        "escolher", "isso é tudo"
    )
    private val ratingRegex = Regex("""^\S{0,2}\s*\d[.,]\d{1,2}\s*\(\d+\)""")

    /**
     * Texto que vem logo depois do "N min (X km)" na MESMA linha (na lista de solicitações da 99
     * o endereço vem junto: "(3 min 707 m) Condomínio X, Rua Y, 1455 - Natal"). Só conta se parecer endereço.
     */
    private fun pairRemainder(line: String): String? {
        val m = pairRegex.find(line) ?: return null
        val rest = line.substring(m.range.last + 1).trim().trimStart(')', '-', ':', ' ')
        return if (rest.length >= 10 && rest.contains(',')) rest else null
    }

    /**
     * Endereços do card, pela posição: o que vem depois do 1º "N min (X km)" é o embarque,
     * e o que vem depois do 2º é o destino (até "Tenho interesse"/"Aceitar"/"Escolher").
     * Devolve (embarque, destino); com um único par, só o destino.
     */
    private fun extractAddresses(texts: List<String>): Pair<String?, String?> {
        val idx = texts.indices.filter { pairRegex.containsMatchIn(texts[it]) }
        if (idx.isEmpty()) return null to null

        fun isJunk(t: String): Boolean {
            val l = t.lowercase()
            return t.trim().length < 4 || '★' in t || ratingRegex.containsMatchIn(t.trim()) || skipWords.any { it in l }
        }

        fun clean(lines: List<String>): String? {
            val parts = lines.map { it.trim() }.filter { it.isNotEmpty() && !isJunk(it) }
            if (parts.isEmpty()) return null
            val ordered = if (parts.size >= 2 && !parts[0].contains(',') && parts.drop(1).any { it.contains(',') }) {
                // O OCR às vezes devolve a cidade ("Parnamirim") antes do resto do endereço.
                parts.drop(1) + parts[0]
            } else {
                parts
            }
            val text = ordered.joinToString(" ").replace(Regex("""\s+"""), " ").trim().trimEnd(',', ' ')
            return text.ifBlank { null }
        }

        fun after(from: Int): List<String> {
            val out = ArrayList<String>()
            for (j in from + 1 until texts.size) {
                val l = texts[j].lowercase()
                if (endWords.any { it in l }) break
                if (priceStartRegex.containsMatchIn(texts[j].trim())) break // começou outra corrida
                out += texts[j]
                if (out.size >= 4) break
            }
            return out
        }

        return if (idx.size >= 2) {
            val between = texts.subList(idx[0] + 1, idx[1]).take(4)
            val pickup = clean(listOfNotNull(pairRemainder(texts[idx[0]])) + between)
            pickup to clean(listOfNotNull(pairRemainder(texts[idx[1]])) + after(idx[1]))
        } else {
            null to clean(listOfNotNull(pairRemainder(texts[idx[0]])) + after(idx[0]))
        }
    }

    /**
     * Telas com várias corridas ao mesmo tempo (lista "Solicitações" da 99): cada corrida começa numa
     * linha que é só o preço ("R$24,60"). Lê cada trecho separadamente, para a estimativa por km
     * e os km de uma corrida não se misturarem com os da outra.
     * Se não houver pelo menos 2 trechos com "N min (X km)", trata a tela como uma corrida só.
     */
    fun parseAll(rawTexts: List<String>): List<RideInfo> {
        val texts = rawTexts.map { normalize(it) }
        val starts = texts.indices.filter { priceStartRegex.containsMatchIn(texts[it].trim()) }
        if (starts.size < 2) splitMerged(texts)?.let { return it }
        if (starts.size >= 2) {
            val segments = starts.indices.map { n ->
                texts.subList(starts[n], if (n + 1 < starts.size) starts[n + 1] else texts.size)
            }
            if (segments.count { seg -> seg.any { pairRegex.containsMatchIn(it) } } >= 2) {
                return segments.mapNotNull { parse(it) }
            }
        }
        return listOfNotNull(parse(rawTexts))
    }

    /**
     * Quando a tela entrega várias corridas num bloco de texto só (sem uma linha por elemento),
     * separa pelo preço e quebra a linha antes de cada "N min (X km)", para achar os endereços.
     * Devolve null se não achar pelo menos 2 corridas com os dois pares "min (km)".
     */
    private val endMergedRegex = Regex("""escolher|tenho interesse|aceitar|selecionar|isso é tudo""", RegexOption.IGNORE_CASE)

    private fun splitMerged(texts: List<String>): List<RideInfo>? {
        val all = texts.joinToString("\n")
        val cuts = priceInTextRegex.findAll(all).map { it.range.first }.toList()
        if (cuts.size < 2) return null
        val pairStart = Regex("""(?<![\d(.,])\(?\s*(?=\d+\s*min(?:utos?)?\s*\(?\s*\d+(?:[.,]\d+)?\s*(?:km|m)\b)""", RegexOption.IGNORE_CASE)
        val segments = cuts.indices.map { n ->
            var chunk = all.substring(cuts[n], if (n + 1 < cuts.size) cuts[n + 1] else all.length)
            // O destino termina no botão ("Escolher", "Aceitar"...): o resto não é endereço.
            endMergedRegex.find(chunk)?.let { chunk = chunk.substring(0, it.range.first) }
            pairStart.replace(chunk) { "\n" + it.value }.split('\n')
        }
        if (segments.count { seg -> seg.any { pairRegex.containsMatchIn(it) } } < 2) return null
        return segments.mapNotNull { parse(it) }.takeIf { it.size >= 2 }
    }

    /** [parseAll] com as correções de OCR, sem conferir com a estimativa por km. */
    fun parseOcrAllRaw(lines: List<String>): List<RideInfo> =
        parseAll(lines.map { line -> fixOcrDigits(ocrCurrencyRegex.replace(line) { "R\$ " }) })

    /** [parseOcrAllRaw] só com as leituras plausíveis. */
    fun parseOcrAll(lines: List<String>): List<RideInfo> =
        parseOcrAllRaw(lines).filter { isPlausible(it) }

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
