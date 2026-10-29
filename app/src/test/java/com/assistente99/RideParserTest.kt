package com.assistente99

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideParserTest {

    @Test
    fun parsesPickupAndTrip() {
        val ride = RideParser.parse(
            listOf("R$ 18,50", "4 min (1,2 km)", "Passageiro 4,9", "15 min (8,3 km)", "Aceitar")
        )
        assertNotNull(ride)
        ride!!
        assertEquals(18.5, ride.price, 0.001)
        assertEquals(1.2, ride.pickupKm!!, 0.001)
        assertEquals(8.3, ride.tripKm!!, 0.001)
        assertEquals(18.5 / 9.5, ride.perKm(true)!!, 0.001)
        assertEquals(18.5 / 8.3, ride.perKm(false)!!, 0.001)
    }

    @Test
    fun parsesUberStyleMinutosText() {
        val ride = RideParser.parse(
            listOf("R$ 22,40", "6 minutos (2,1 km) de distância", "Viagem de 18 minutos (9,4 km)", "Aceitar")
        )!!
        assertEquals(22.4, ride.price, 0.001)
        assertEquals(2.1, ride.pickupKm!!, 0.001)
        assertEquals(9.4, ride.tripKm!!, 0.001)
        assertEquals(6, ride.pickupMin)
        assertEquals(18, ride.tripMin)
    }

    @Test
    fun convertsMetersToKm() {
        val ride = RideParser.parse(listOf("R$ 7,00", "500 m", "3,5 km"))!!
        assertEquals(0.5, ride.pickupKm!!, 0.001)
        assertEquals(3.5, ride.tripKm!!, 0.001)
    }

    @Test
    fun singleDistanceIsTrip() {
        val ride = RideParser.parse(listOf("R$ 12,00", "6,0 km"))!!
        assertNull(ride.pickupKm)
        assertEquals(2.0, ride.perKm(true)!!, 0.001)
    }

    @Test
    fun handlesThousandsSeparator() {
        val ride = RideParser.parse(listOf("R$ 1.234,50", "100 km"))!!
        assertEquals(1234.5, ride.price, 0.001)
    }

    @Test
    fun parsesUberRadarCard() {
        val ride = RideParser.parse(
            listOf(
                "Priority",
                "R$12,01",
                "Est. de R$1,15/km",
                "4,78 (487)",
                "Verificado",
                "+R$ 1,98 incluída para retirada",
                "8 min (4.3 km)",
                "R. Missionario Gunnar Vingren, Capim Macio, Natal",
                "10 minutos (6.1 km)",
                "Rua Creuza Dantas Maciel, 19, Parque do Pitimbu, Parnamirim",
                "Tenho interesse"
            )
        )!!
        assertEquals(12.01, ride.price, 0.001)
        assertEquals(4.3, ride.pickupKm!!, 0.001)
        assertEquals(6.1, ride.tripKm!!, 0.001)
        assertEquals(8, ride.pickupMin)
        assertEquals(10, ride.tripMin)
        // Mesmo valor que o app da Uber estima: R$ 1,15/km.
        assertEquals(1.15, ride.perKm(true)!!, 0.01)
    }

    @Test
    fun parsesUberOcrLinesFromRecording() {
        // Linhas como o OCR deve devolver para o card da gravação (R$ lido como "RS", sem parênteses).
        val ride = RideParser.parseOcr(
            listOf(
                "UberX", "Exclusivo", "RS 41,74", "Est. de R$1,16/km", "4,75 (117)",
                "20 min (12.5 km)", "R. Duque de Caxias, Bela Parnamirim, Macaiba",
                "32 minutos (23.5 km)", "Av. Gov. Rafael Fernandes, 801, Alecrim, Natal",
                "Viagem longa (mais de 30 min)", "Aceitar"
            )
        )!!
        assertEquals(41.74, ride.price, 0.001)
        assertEquals(12.5, ride.pickupKm!!, 0.001)
        assertEquals(23.5, ride.tripKm!!, 0.001)
        assertEquals(1.16, ride.perKm(true)!!, 0.01)
    }

    @Test
    fun ocrReadsLetterLAsDigitOne_realCard1() {
        // Linhas reais lidas por OCR no celular (07:23:43): "8.l km" deve virar 8.1 km.
        val ride = RideParser.parseOcr(
            listOf(
                ":X", "Arig", "ni", "BR-1", "Po UDU", "2 UberX", "R$ 12,70", "Est. de R$1,15/km",
                "\u2605 4,92 (2000)", "9 +R$ l,00 inclu\u00eddo", "8 min (2.9 km)", "Verificado",
                "Rua Rio Curu\u00e1, Pitimbu, Natal", "h 12 minutos (8.l km)", "Parnamirim",
                "Aeroporto Augusto Severo, S/N, Ema\u00fas,", "X", "Tenho interesse", "Pa"
            )
        )!!
        assertEquals(12.70, ride.price, 0.001)
        assertEquals(2.9, ride.pickupKm!!, 0.001)
        assertEquals(8.1, ride.tripKm!!, 0.001)
        assertEquals(1.15, ride.perKm(true)!!, 0.01) // igual ao "Est. de R$1,15/km" da Uber
    }

    @Test
    fun ocrReadsLetterLAsDigitOne_realCard2() {
        val ride = RideParser.parseOcr(
            listOf(
                "Ambr\u00f3sio", "Francisco Ferro", "101", "BR-101", "UberX", "Radar de Viagens2",
                "R$ 15,40", "Est. de R$1,28/km", "t 4,67 (1714)", "OVerificado", "3 min (0.9 km)",
                "Av. Dos Caiapos, Pitimbu, Natal", "22 minutos (11.l km)",
                "Av. Hermes da Fonseca, 980, Tirol, Natal", "Tenho interesse"
            )
        )!!
        assertEquals(15.40, ride.price, 0.001)
        assertEquals(0.9, ride.pickupKm!!, 0.001)
        assertEquals(11.1, ride.tripKm!!, 0.001)
        assertEquals(1.28, ride.perKm(true)!!, 0.01) // igual ao "Est. de R$1,28/km" da Uber
    }

    @Test
    fun ocrRejectsReadingThatContradictsUberEstimate() {
        // Mesmo card real, mas o OCR leu "(3.1 km)" no lugar de "(8.1 km)": daria R$ 2,11/km contra a estimativa 1,15.
        val lines = listOf(
            "R$ 12,70", "Est. de R$1,15/km", "8 min (2.9 km)", "12 minutos (3.1 km)", "Tenho interesse"
        )
        val raw = RideParser.parseOcrRaw(lines)!!
        assertEquals(1.15, raw.estPerKm!!, 0.001)
        assertEquals(false, RideParser.isPlausible(raw))
        assertNull(RideParser.parseOcr(lines))
    }

    @Test
    fun ocrAcceptsReadingThatMatchesUberEstimate() {
        val lines = listOf(
            "R$ 15,40", "Est. de R$1,28/km", "3 min (0.9 km)", "22 minutos (11.l km)", "Tenho interesse"
        )
        assertNotNull(RideParser.parseOcr(lines))
    }

    @Test
    fun withoutEstimateTheReadingIsKept() {
        // 99 e telas sem "Est. de": não há com o que conferir, então a leitura é aceita.
        assertNotNull(RideParser.parseOcr(listOf("R$ 9,00", "5 min (1.0 km)", "10 minutos (3.0 km)")))
    }

    @Test
    fun ocrFixDoesNotTouchWordsOrCleanNumbers() {
        val ride = RideParser.parseOcr(listOf("R$ 10,00", "Il Lugar Olho", "5 min (2.0 km)", "10 minutos (4.5 km)"))!!
        assertEquals(2.0, ride.pickupKm!!, 0.001)
        assertEquals(4.5, ride.tripKm!!, 0.001)
    }

    @Test
    fun ocrToleratesMissingParentheses() {
        val ride = RideParser.parseOcr(listOf("R5 12,01", "8 min 4.3 km", "10 minutos 6.1 km"))!!
        assertEquals(12.01, ride.price, 0.001)
        assertEquals(4.3, ride.pickupKm!!, 0.001)
        assertEquals(6.1, ride.tripKm!!, 0.001)
    }

    @Test
    fun handlesNonBreakingSpaces() {
        val ride = RideParser.parse(
            listOf("R$ 12,01", "8 min (4.3 km)", "10 minutos (6.1 km)")
        )!!
        assertEquals(12.01, ride.price, 0.001)
        assertEquals(4.3, ride.pickupKm!!, 0.001)
        assertEquals(6.1, ride.tripKm!!, 0.001)
    }

    @Test
    fun ignoresScreensWithoutPriceOrDistance() {
        assertNull(RideParser.parse(listOf("Você está online", "Buscando corridas")))
        assertNull(RideParser.parse(listOf("R$ 10,00")))
    }
}
