package io.github.gthibo.leavenlock.data.bluelink

import io.github.gthibo.leavenlock.data.bluelink.us.UsCredentials
import io.github.gthibo.leavenlock.data.secure.SecureStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The adapter between the US client and the encrypted SecureStore. */
class SecureUsCredentialStoreTest {

    private val secure = mockk<SecureStore>(relaxed = true)
    private val store = SecureUsCredentialStore(secure)

    @Test
    fun loadReturnsBothFieldsWhenSaved() {
        every { secure.loadUsUsername() } returns "me@example.com"
        every { secure.loadUsPassword() } returns "pw"
        assertEquals(UsCredentials("me@example.com", "pw"), store.load())
    }

    @Test
    fun loadReturnsNullWhenEitherFieldIsMissing() {
        every { secure.loadUsUsername() } returns null
        every { secure.loadUsPassword() } returns "pw"
        assertNull(store.load())
        every { secure.loadUsUsername() } returns "me@example.com"
        every { secure.loadUsPassword() } returns null
        assertNull(store.load())
    }

    @Test
    fun saveAndClearGoToTheEncryptedStore() {
        store.save(UsCredentials("me@example.com", "pw"))
        verify { secure.saveUsCredentials("me@example.com", "pw") }
        store.clear()
        verify { secure.clearUsCredentials() }
    }
}
