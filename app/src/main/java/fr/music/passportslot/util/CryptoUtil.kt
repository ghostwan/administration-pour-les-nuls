package fr.music.passportslot.util

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES encryption for the ANTS authentication flow.
 * The frontend encrypts the front_auth_token using AES-CBC before
 * sending it as the username to the /token endpoint.
 */
object CryptoUtil {

    /**
     * Encrypt the given plaintext using AES/CBC/PKCS5Padding.
     *
     * The ANTS frontend uses:
     * - Key: Base64-decoded "s0hCXLUggPAUwUxsNrEjYg==" (16 bytes = AES-128)
     * - IV: derived from "f2f4e6d6f7z" padded to 16 bytes
     */
    fun encryptAuthToken(
        plaintext: String = Constants.FRONT_AUTH_TOKEN,
        keyBase64: String = Constants.AES_KEY,
        ivString: String = Constants.AES_IV
    ): String {
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Pad IV to 16 bytes (AES block size)
        val ivBytes = ivString.toByteArray(Charsets.UTF_8)
        val paddedIv = ByteArray(16)
        System.arraycopy(ivBytes, 0, paddedIv, 0, minOf(ivBytes.size, 16))
        val ivSpec = IvParameterSpec(paddedIv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}
