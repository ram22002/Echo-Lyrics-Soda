package dev.brahmkshatriya.echo.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SodaParseTest {

    @Test
    fun parsesTimedPayload() {
        val text = "[1000,2000](1000,500,0)First (1500,500,0)line\n[3000,1000]Second"
        val lines = parseSodaTimed(text)
        assertEquals(2, lines.size)
        assertEquals("First line", lines[0].text)
        assertEquals(3000L, lines[1].startMs)
        assertTrue(parseSodaTimed("no timing").isEmpty())
    }
}
