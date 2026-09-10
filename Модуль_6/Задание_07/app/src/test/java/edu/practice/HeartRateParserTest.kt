package edu.practice

import edu.practice.domain.HeartRateParser
import org.junit.Assert.*
import org.junit.Test

class HeartRateParserTest {
    @Test
    fun eightBit() {
        assertEquals(72, HeartRateParser.parse(byteArrayOf(0, 72)))
    }

    @Test
    fun sixteenBit() {
        assertEquals(300, HeartRateParser.parse(byteArrayOf(1, 44, 1)))
    }

    @Test
    fun unsigned() {
        assertEquals(200, HeartRateParser.parse(byteArrayOf(0, 200.toByte())))
    }

    @Test
    fun incomplete() {
        assertNull(HeartRateParser.parse(byteArrayOf()))
        assertNull(HeartRateParser.parse(byteArrayOf(1, 72)))
    }
}
