package expo.modules.main.sshkey

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import expo.modules.main.keystore.KeyStoreConstants
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SshKeyModule : Module() {
  companion object {
    private const val TAG = "SshKeyModule"
    private const val WORD_LIST_ASSET = "eff_large_wordlist.txt"
    private const val WORD_COUNT = 6
    private const val WORDS_IN_LIST = 7776

    private const val PRIVATE_KEY_FILE = "ssh_key_ed25519.enc"
    private const val PUBLIC_KEY_FILE = "ssh_key_ed25519.pub"

    private const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
  }

  override fun definition() = ModuleDefinition {
    Name("SshKeyModule")

    AsyncFunction("getKey") {
      getKey()
    }

    AsyncFunction("generateKeyPair") {
      generateKeyPair()
    }

    AsyncFunction("getKeyPassphrase") {
      getKeyPassphrase()
    }
  }

  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("No React context available")

  private val secureRandom = SecureRandom()

  // ── getKey ─────────────────────────────────────────────────────

  private fun getKey(): Map<String, Any?>? {
    val publicKeyFile = java.io.File(context.filesDir, PUBLIC_KEY_FILE)
    if (!publicKeyFile.exists()) return null
    return mapOf("publicKey" to publicKeyFile.readText())
  }

  // ── generateKeyPair ─────────────────────────────────────────────

  private fun generateKeyPair(): Map<String, Any?> {
    val passphrase = generatePassphrase()
    val keyPair = generateEd25519KeyPair()

    val privateKeyBytes = keyPair.private.encoded
    val publicKeyOpenSsh = encodeOpenSshPublicKey(keyPair.public.encoded)

    // Encrypt private key with passphrase
    val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
    val encryptedPrivateKey = encryptPrivateKey(privateKeyBytes, passphrase, salt)

    // Store passphrase in Android KeyStore (excluded from backup)
    storePassphraseInKeystore(passphrase)

    // Write files to filesDir (auto-backed up by Android)
    java.io.File(context.filesDir, PRIVATE_KEY_FILE).writeBytes(encryptedPrivateKey)
    java.io.File(context.filesDir, PUBLIC_KEY_FILE).writeText(publicKeyOpenSsh)

    return mapOf("publicKey" to publicKeyOpenSsh)
  }

  // ── getKeyPassphrase ───────────────────────────────────────────

  private fun getKeyPassphrase(): String {
    val prefs = context.getSharedPreferences("ssh_key_prefs", Context.MODE_PRIVATE)
    val encryptedB64 = prefs.getString("encrypted_passphrase", null)
      ?: throw IllegalStateException("No encrypted passphrase stored")
    val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)

    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    val privateKey = keyStore.getKey(KeyStoreConstants.MAIN_KEYSTORE_ALIAS, null) as java.security.PrivateKey

    val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    val decrypted = cipher.doFinal(encrypted)

    return String(decrypted, Charsets.UTF_8)
  }

  // ── Passphrase generation (diceware) ────────────────────────────

  private fun generatePassphrase(): String {
    val words = loadWordList()
    return (1..WORD_COUNT).joinToString(" ") {
      words[secureRandom.nextInt(WORDS_IN_LIST)]
    }
  }

  private fun loadWordList(): List<String> {
    return context.assets.open(WORD_LIST_ASSET).bufferedReader().useLines { lines ->
      lines.mapNotNull { line ->
        val tabIndex = line.indexOf('\t')
        if (tabIndex == -1) null
        else line.substring(tabIndex + 1).trim()
      }.toList()
    }
  }

  // ── Ed25519 key pair generation ─────────────────────────────────

  private fun generateEd25519KeyPair(): java.security.KeyPair {
    val generator = KeyPairGenerator.getInstance("Ed25519")
    generator.initialize(256, secureRandom)
    return generator.generateKeyPair()
  }

  // ── OpenSSH public key encoding ─────────────────────────────────

  private fun encodeOpenSshPublicKey(encodedPublicKey: ByteArray): String {
    val rawKey = extractEd25519RawPublicKey(encodedPublicKey)

    val keyType = "ssh-ed25519"
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)

    writeSshString(dos, keyType.toByteArray())
    writeSshString(dos, rawKey)

    return "$keyType ${Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)}"
  }

  private fun extractEd25519RawPublicKey(x509Encoded: ByteArray): ByteArray {
    return x509Encoded.copyOfRange(x509Encoded.size - 32, x509Encoded.size)
  }

  private fun writeSshString(dos: DataOutputStream, data: ByteArray) {
    dos.writeInt(data.size)
    dos.write(data)
  }

  // ── Private key encryption (AES-256-GCM + PBKDF2) ───────────────

  private fun encryptPrivateKey(privateKeyBytes: ByteArray, passphrase: String, salt: ByteArray): ByteArray {
    val key = deriveKey(passphrase, salt)
    val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    val ciphertext = cipher.doFinal(privateKeyBytes)

    val output = ByteArray(salt.size + iv.size + ciphertext.size)
    System.arraycopy(salt, 0, output, 0, salt.size)
    System.arraycopy(iv, 0, output, salt.size, iv.size)
    System.arraycopy(ciphertext, 0, output, salt.size + iv.size, ciphertext.size)
    return output
  }

  private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
  }

  // ── Keystore passphrase storage ─────────────────────────────────

  private fun storePassphraseInKeystore(passphrase: String) {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    val generator = KeyPairGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_RSA,
      "AndroidKeyStore"
    )

    val spec = KeyGenParameterSpec.Builder(
      KeyStoreConstants.MAIN_KEYSTORE_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(2048)
      .build()

    generator.initialize(spec)
    val keyPair = generator.generateKeyPair()

    val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keyPair.public)
    val encryptedPassphrase = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))

    val prefs = context.getSharedPreferences("ssh_key_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("encrypted_passphrase", Base64.encodeToString(encryptedPassphrase, Base64.NO_WRAP)).apply()
  }
}

