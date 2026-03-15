package fr.music.passportslot.util

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-GCM encryption for the ANTS authentication flow.
 *
 * The ANTS Angular frontend uses the Web Crypto API with AES-GCM:
 * - Key: Base64-decoded "s0hCXLUggPAUwUxsNrEjYg==" (16 bytes = AES-128)
 * - IV: SHA-256("f2f4e6d6f7z") truncated to 12 bytes
 * - Output: Base64-URL-safe( IV || ciphertext || GCM-auth-tag )
 */
object CryptoUtil {

    private const val GCM_TAG_LENGTH_BITS = 128 // 16 bytes
    private const val GCM_IV_LENGTH = 12

    /**
     * Encrypt the front_auth_token using AES-GCM, matching the ANTS frontend behavior.
     *
     * @return URL-safe Base64 string of (IV + ciphertext + GCM tag)
     */
    fun encryptAuthToken(
        plaintext: String = Constants.FRONT_AUTH_TOKEN,
        keyBase64: String = Constants.AES_KEY,
        ivSeed: String = Constants.AES_IV
    ): String {
        // 1. Decode the AES key from Base64
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 2. Derive IV: SHA-256 hash of the IV seed, take first 12 bytes
        val sha256 = MessageDigest.getInstance("SHA-256")
        val ivHash = sha256.digest(ivSeed.toByteArray(Charsets.UTF_8))
        val iv = ivHash.copyOf(GCM_IV_LENGTH)

        // 3. Encrypt using AES/GCM/NoPadding
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 4. Concatenate IV + ciphertext (which includes the GCM auth tag appended by Java)
        val result = ByteArray(iv.size + ciphertextWithTag.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertextWithTag, 0, result, iv.size, ciphertextWithTag.size)

        // 5. Encode as URL-safe Base64 (no padding, + -> -, / -> _)
        val base64 = Base64.encodeToString(result, Base64.NO_WRAP)
        return base64
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
    }
}
