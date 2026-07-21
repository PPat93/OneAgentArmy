package com.parrotworks.oneagentarmy.data.local.crypto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

// Needs a real (or emulated) Android Keystore, hence androidTest rather than a JVM unit test -
// "AndroidKeyStore" isn't a provider that exists outside an actual Android runtime.
@RunWith(AndroidJUnit4::class)
class ApiKeyCipherInstrumentedTest {

    private val cipher = ApiKeyCipher()

    @Test
    fun decryptReversesEncrypt() {
        val plaintext = "sk-test-1234567890abcdef"

        val blob = cipher.encrypt(plaintext)

        assertEquals(plaintext, cipher.decrypt(blob))
    }

    @Test
    fun encryptingTheSamePlaintextTwiceProducesDifferentCiphertextAndIv() {
        val plaintext = "sk-test-1234567890abcdef"

        val first = cipher.encrypt(plaintext)
        val second = cipher.encrypt(plaintext)

        // A fresh random IV per call is what makes AES-GCM safe to reuse a key with -
        // if these ever matched, that would be a real cryptographic bug, not just a quirk.
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.ciphertext, second.ciphertext)
        // Both still decrypt back to the same plaintext independently.
        assertEquals(plaintext, cipher.decrypt(first))
        assertEquals(plaintext, cipher.decrypt(second))
    }

    @Test
    fun decryptingATamperedBlobThrows() {
        val blob = cipher.encrypt("sk-test-1234567890abcdef")
        // Flip one byte of the actual ciphertext (not the base64 text itself, which could
        // produce invalid base64 and fail for the wrong reason) to simulate corruption or a
        // mismatched key - the scenario DataStoreSettingsRepository's defensive catch guards
        // against (e.g. a DataStore file restored from a backup taken on a different device).
        val bytes = Base64.decode(blob.ciphertext, Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        val tampered = blob.copy(ciphertext = Base64.encodeToString(bytes, Base64.NO_WRAP))

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(tampered)
        }
    }

    @Test
    fun roundTripsAnEmptyString() {
        val blob = cipher.encrypt("")

        assertEquals("", cipher.decrypt(blob))
    }
}
