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
    fun extractsAddressesFromRealOcrCard1() {
        // Card real lido por OCR: o OCR trouxe "Parnamirim" antes do resto do endereço de destino.
        val ride = RideParser.parseOcr(
            listOf(
                ":X", "Arig", "ni", "BR-1", "Po UDU", "2 UberX", "R$ 12,70", "Est. de R$1,15/km",
                "\u2605 4,92 (2000)", "9 +R$ l,00 inclu\u00eddo", "8 min (2.9 km)", "Verificado",
                "Rua Rio Curu\u00e1, Pitimbu, Natal", "h 12 minutos (8.l km)", "Parnamirim",
                "Aeroporto Augusto Severo, S/N, Ema\u00fas,", "X", "Tenho interesse", "Pa"
            )
        )!!
        assertEquals("Rua Rio Curu\u00e1, Pitimbu, Natal", ride.pickupAddress)
        assertEquals("Aeroporto Augusto Severo, S/N, Ema\u00fas, Parnamirim", ride.dropoffAddress)
    }

    @Test
    fun extractsAddressesFromRecordedCard() {
        val ride = RideParser.parseOcr(
            listOf(
                "UberX", "Exclusivo", "R$ 41,74", "Est. de R$1,16/km", "4,75 (117)",
                "20 min (12.5 km)", "R. Duque de Caxias, Bela Parnamirim, Macaiba",
                "32 minutos (23.5 km)", "Av. Gov. Rafael Fernandes, 801, Alecrim, Natal",
                "Viagem longa (mais de 30 min)", "Aceitar"
            )
        )!!
        assertEquals("R. Duque de Caxias, Bela Parnamirim, Macaiba", ride.pickupAddress)
        assertEquals("Av. Gov. Rafael Fernandes, 801, Alecrim, Natal", ride.dropoffAddress)
        assertEquals(20, ride.pickupMin)
        assertEquals(32, ride.tripMin)
    }

    @Test
    fun noAddressesWhenCardHasNone() {
        val ride = RideParser.parse(listOf("R$ 9,00", "5 min (1.0 km)", "10 minutos (3.0 km)"))!!
        assertNull(ride.pickupAddress)
        assertNull(ride.dropoffAddress)
    }

    @Test
    fun parsesNinetyNineOfferCard() {
        // Solicitação da 99 (print do usuário): preço, "R$1,53/km" da própria 99, e dois blocos "N min (X km)".
        val ride = RideParser.parse(
            listOf(
                "Pgto. no app", "R$27,50", "1,2x", "R$1,53/km", "0% de Taxa99 semanal",
                "R$2,09 por espera longa incl.", "4,98", "179 corridas", "Perfil Essencial",
                "5 min (1,7 km)", "SuperFácil Atacado, BR-101, S/N - Emaús, Parnamirim - RN, 59149-290, Brasil",
                "20 min (16,3 km)", "CLI. MERC J L, Rua Areia Branca, 385, Macaíba - RN, 59280-000, Brasil"
            )
        )!!
        assertEquals(27.50, ride.price, 0.001)
        assertEquals(1.7, ride.pickupKm!!, 0.001)
        assertEquals(16.3, ride.tripKm!!, 0.001)
        assertEquals(1.53, ride.estPerKm!!, 0.001)
        assertEquals(1.53, ride.perKm(true)!!, 0.01) // igual ao R$1,53/km mostrado pela 99
        assertEquals("SuperFácil Atacado, BR-101, S/N - Emaús, Parnamirim - RN, 59149-290, Brasil", ride.pickupAddress)
        assertEquals("CLI. MERC J L, Rua Areia Branca, 385, Macaíba - RN, 59280-000, Brasil", ride.dropoffAddress)
    }

    @Test
    fun parsesNinetyNineRequestsListWithTwoRides() {
        // Tela "Solicitações" da 99 (print do usuário): duas corridas ao mesmo tempo, endereço na mesma linha do tempo/km.
        val rides = RideParser.parseAll(
            listOf(
                "Solicitações", "Escolha uma", "Km até o embarque",
                "Pop", "R$24,60", "100% dos ganhos", "4,87", "395 corridas", "Cartão verif.",
                "(3 min 707 m) Condomínio Canto Pássaros, Rua das Algarobas, 1455 - Pitimbú, Natal - RN, 59067-500, Brasil",
                "(27 min 20 km) casa do espeto, travessa olimpio maciel, Estrada Lagoa das Pedras, 41, Macaíba - RN, 59280-000, Bras",
                "Escolher",
                "Pop", "R$7,10", "100% dos ganho...", "R$1,36/km", "4,95", "531 corridas", "Perfil Essencial",
                "(4 min 1,5 km) Nova colina, Morada da Paz",
                "(7 min 3,7 km) IEADERN - Assembleia de Deus em Cidade Satélite, Rua da Gameleira, 2108",
                "Escolher", "Isso é tudo por enquanto"
            )
        )
        assertEquals(2, rides.size)

        val a = rides[0]
        assertEquals(24.60, a.price, 0.001)
        assertEquals(0.707, a.pickupKm!!, 0.001)
        assertEquals(20.0, a.tripKm!!, 0.001)
        assertNull(a.estPerKm) // a estimativa "R$1,36/km" é da corrida de baixo, não desta
        assertEquals("Condomínio Canto Pássaros, Rua das Algarobas, 1455 - Pitimbú, Natal - RN, 59067-500, Brasil", a.pickupAddress)

        val b = rides[1]
        assertEquals(7.10, b.price, 0.001)
        assertEquals(1.5, b.pickupKm!!, 0.001)
        assertEquals(3.7, b.tripKm!!, 0.001)
        assertEquals(1.36, b.estPerKm!!, 0.001)
        assertEquals(1.36, b.perKm(true)!!, 0.01) // igual ao R$1,36/km mostrado pela 99
        assertEquals("Nova colina, Morada da Paz", b.pickupAddress)
        assertEquals("IEADERN - Assembleia de Deus em Cidade Satélite, Rua da Gameleira, 2108", b.dropoffAddress)
    }

    @Test
    fun singleOfferWithBonusLineIsNotSplit() {
        // Só uma linha "só preço": continua sendo uma corrida só.
        val rides = RideParser.parseAll(listOf("R$ 12,70", "+R$ 1,00 incluído", "8 min (2.9 km)", "12 minutos (8.1 km)"))
        assertEquals(1, rides.size)
        assertEquals(12.70, rides[0].price, 0.001)
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

    private val list99Separate = listOf(
        "R$24,60", "100% dos ganhos", "4,87", "395 corridas", "Cartão verif.",
        "(3 min 707 m) Condomínio Canto Pássaros, Rua das Algarobas, 1455 - Pitimbú, Natal - RN",
        "(27 min 20 km) casa do espeto, travessa olimpio maciel, Macaíba - RN", "Escolher",
        "R$7,10", "100% dos ganho...", "R$1,36/km", "4,95", "531 corridas", "Perfil Essencial",
        "(4 min 1,5 km) Nova colina, Morada da Paz",
        "(7 min 3,7 km) IEADERN - Assembleia de Deus em Cidade Satélite, Rua da Gameleira, 2108",
        "Escolher", "Isso é tudo por enquanto"
    )

    @Test
    fun list99WithSeparateNodes() {
        val rides = RideParser.parseAll(list99Separate)
        assertEquals(2, rides.size)
        assertEquals(24.60, rides[0].price, 0.001)
        assertEquals(0.707, rides[0].pickupKm!!, 0.001)
        assertEquals(20.0, rides[0].tripKm!!, 0.001)
        assertEquals(7.10, rides[1].price, 0.001)
        assertEquals(3.7, rides[1].tripKm!!, 0.001)
    }

    @Test
    fun list99WithPriceAndBadgeOnSameLine() {
        val rides = RideParser.parseOcrAllRaw(
            listOf(
                "Pop", "R$24,60 100% dos ganhos", "4,87 • 395 corridas",
                "(3 min 707 m) Condomínio Canto Pássaros,", "Rua das Algarobas, 1455 - Pitimbú, Natal - RN,",
                "(27 min 20 km) casa do espeto, travessa", "olimpio maciel, Macaíba - RN", "Escolher",
                "Pop", "R$7,10 100% dos ganho... R$1,36/km",
                "(4 min 1,5 km) Nova colina, Morada da Paz", "(7 min 3,7 km) IEADERN - Assembleia de", "Escolher"
            )
        )
        assertEquals(2, rides.size)
        assertEquals(24.60, rides[0].price, 0.001)
        assertEquals(7.10, rides[1].price, 0.001)
        assertEquals(1.36, rides[1].estPerKm!!, 0.001)
    }

    @Test
    fun list99AllInOneBlock() {
        val rides = RideParser.parseAll(listOf(list99Separate.joinToString(" ")))
        assertEquals(2, rides.size)
        assertEquals(20.0, rides[0].tripKm!!, 0.001)
        assertEquals(1.5, rides[1].pickupKm!!, 0.001)
    }

    @Test
    fun readsPassengerRatingAndRides99() {
        val rides = RideParser.parseAll(list99Separate)
        assertEquals(4.87, rides[0].rating!!, 0.001)
        assertEquals(395, rides[0].rides)
        assertEquals(4.95, rides[1].rating!!, 0.001)
        assertEquals(531, rides[1].rides)
    }

    @Test
    fun readsRatingOnSameLineAsRides() {
        val ride = RideParser.parse(
            listOf("R$24,60", "4,87 • 1.395 corridas • Cartão verif.", "(3 min 707 m) Rua A, 10", "(27 min 20 km) Rua B, 20")
        )!!
        assertEquals(4.87, ride.rating!!, 0.001)
        assertEquals(1395, ride.rides)
    }

    @Test
    fun readsUberRatingWithStar() {
        val ride = RideParser.parse(listOf("R$ 12,70", "★ 4.93 (1.204)", "8 min (4.3 km)", "10 minutos (6.1 km)"))!!
        assertEquals(4.93, ride.rating!!, 0.001)
    }

    @Test
    fun noRatingWhenCardHasNone() {
        val ride = RideParser.parse(listOf("R$ 12,70", "Est. de R$1,15/km", "8 min (4.3 km)", "10 minutos (6.1 km)"))!!
        assertNull(ride.rating)
        assertNull(ride.rides)
    }

    @Test
    fun goodRideNeedsPerKmAndShortPickup() {
        // R$ 12,70 em 1,0 + 4,0 km = R$ 2,54/km, passageiro a 1,0 km: boa.
        val good = RideInfo(12.70, 1.0, 4.0, 3, 8)
        assertEquals(true, GoodAlert.isGood(good, 2.0, 1.5, true))
        // Passageiro longe demais.
        assertEquals(false, GoodAlert.isGood(RideInfo(12.70, 2.0, 3.0, 5, 8), 2.0, 1.5, true))
        // R$/km baixo.
        assertEquals(false, GoodAlert.isGood(RideInfo(7.10, 1.5, 3.7, 4, 7), 2.0, 1.5, true))
        // Sem o km até o passageiro, não dá para garantir.
        assertEquals(false, GoodAlert.isGood(RideInfo(12.70, null, 4.0, null, 8), 2.0, 1.5, true))
    }

    @Test
    fun bonusLineIsNotANewRide() {
        // 99: a linha "R$1,52 Tarifa base dinâmica incl." fica entre os dois pares e não é outra corrida.
        val lines = listOf(
            "Pgto. no app", "R$6,90", "R$3,14/km", "0% de Taxa99 semanal", "t 4,73 22 corridas",
            "O3 min (339 m)", "41,1x", "9 R$1,52 Tarifa base dinâmica incl.", "R$1,52 Tarifa base dinâmica incl.",
            "4 min (1,9 km)", "BurnFit, Rua Oiti, 20 - Pitimbú", "Perfil Essencial",
            "Condomínio Vereda do Sol, Cond Vereda", "Do Sol - R. da Saudade, 207"
        )
        val rides = RideParser.parseOcrAll(lines)
        assertEquals(1, rides.size)
        assertEquals(0.339, rides[0].pickupKm!!, 0.001)
        assertEquals(1.9, rides[0].tripKm!!, 0.001)
        assertEquals(6.90, rides[0].price, 0.001)
    }

    @Test
    fun bonusLineIsNeverThePrice() {
        // Preço ilegível pelo OCR ("R$25,."): não pode pegar o "R$1,14 Tarifa base dinâmica incl." como preço.
        val lines = listOf("R$2,01/km", "R$25,.", "R$1,14 Tarifa base dinâmica incl.", "3 min (841 m)", "21 min (12 km)")
        assertNull(RideParser.parse(lines))
    }
}
