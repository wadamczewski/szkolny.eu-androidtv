package pl.prywatny.dziennik.tv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.prywatny.dziennik.tv.librus.MessageSummary
import pl.prywatny.dziennik.tv.librus.StudentData
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One Synergia login on this TV. The password is kept because Synergia sessions can't be renewed without it. */
@Serializable
data class Account(val login: String, val password: String, val studentName: String, val isParent: Boolean)

@Serializable
private data class AccountList(val accounts: List<Account>, val current: String?)

@Serializable
data class MessagesCache(val inbox: List<MessageSummary>? = null, val sent: List<MessageSummary>? = null)

/**
 * Accounts are encrypted with an AES key from the Android Keystore (never leaves the device);
 * cached data is plain JSON in the app's private files. Both are excluded from backups.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("dziennik", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    var accounts: List<Account> = emptyList()
        private set
    var currentLogin: String? = null
        private set

    init {
        val list = prefs.getString(KEY_ACCOUNTS, null)?.let { encrypted ->
            runCatching { json.decodeFromString<AccountList>(Crypto.decrypt(encrypted)) }.getOrNull()
        }
        accounts = list?.accounts ?: emptyList()
        currentLogin = list?.current
    }

    val current: Account? get() = accounts.firstOrNull { it.login == currentLogin } ?: accounts.firstOrNull()

    /** Adds or replaces an account (same login) and makes it current. */
    fun save(account: Account) {
        accounts = accounts.filter { it.login != account.login } + account
        currentLogin = account.login
        persist()
    }

    fun select(login: String) {
        currentLogin = login
        persist()
    }

    fun remove(login: String) {
        accounts = accounts.filter { it.login != login }
        currentLogin = accounts.firstOrNull()?.login
        file("data", login).delete()
        file("messages", login).delete()
        persist()
    }

    fun data(login: String): StudentData? = read(file("data", login))

    fun saveData(login: String, data: StudentData) = write(file("data", login), json.encodeToString(data))

    fun messages(login: String): MessagesCache = read(file("messages", login)) ?: MessagesCache()

    fun saveMessages(login: String, cache: MessagesCache) = write(file("messages", login), json.encodeToString(cache))

    private fun persist() {
        val plain = json.encodeToString(AccountList(accounts, currentLogin))
        prefs.edit().putString(KEY_ACCOUNTS, Crypto.encrypt(plain)).apply()
    }

    private fun file(kind: String, login: String) = File(dir, "$kind-${login.hashCode().toUInt()}.json")

    private inline fun <reified T> read(file: File): T? =
        if (file.exists()) runCatching { json.decodeFromString<T>(file.readText()) }.getOrNull() else null

    private fun write(file: File, text: String) {
        runCatching { file.writeText(text) }
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
    }
}

/** AES-GCM with a non-exportable key in the Android Keystore. */
private object Crypto {
    private const val ALIAS = "dziennik-accounts"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12))
        }
        return String(cipher.doFinal(sealed, 12, sealed.size - 12), Charsets.UTF_8)
    }
}
