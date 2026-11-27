package io.github.gthibo.leavenlock.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PinMaskTest {

    @Test
    fun showsOneDotPerDigit() {
        val out = PinMask.filter(AnnotatedString("1234"))
        assertEquals("••••", out.text.text)
        assertEquals(OffsetMapping.Identity, out.offsetMapping)
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", PinMask.filter(AnnotatedString("")).text.text)
    }

    @Test
    fun isNotPasswordVisualTransformation() {
        // Compose flags any field using PasswordVisualTransformation as a password for autofill.
        assertFalse((PinMask as Any) is PasswordVisualTransformation)
    }
}
