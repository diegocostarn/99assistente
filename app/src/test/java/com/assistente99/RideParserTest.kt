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
    fun ignoresScreensWithoutPriceOrDistance() {
        assertNull(RideParser.parse(listOf("Você está online", "Buscando corridas")))
        assertNull(RideParser.parse(listOf("R$ 10,00")))
    }
}
