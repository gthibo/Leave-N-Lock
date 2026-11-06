package io.github.gthibo.leavenlock.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun twelveHourByDefault() {
        assertEquals("hm", TimeFormat.pattern("jm", is24Hour = false))
        assertEquals("hms", TimeFormat.pattern("jms", is24Hour = false))
        assertEquals("MMMdhm", TimeFormat.pattern("MMMdjm", is24Hour = false))
    }

    @Test
    fun twentyFourHourWhenThePhoneUsesIt() {
        assertEquals("Hm", TimeFormat.pattern("jm", is24Hour = true))
        assertEquals("MMMdHm", TimeFormat.pattern("MMMdjm", is24Hour = true))
    }
}
