package com.wpreborn.data.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A mail account: its address and the IMAP (reading) and SMTP (sending) servers. */
data class MailAccount(
    val id: String,
    val email: String,
    val name: String,
    val user: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    /** SMTP over SSL from the start (465); otherwise STARTTLS (587). */
    val smtpSsl: Boolean,
) {
    /** The title of the account in Mail: "OUTLOOK", "GMAIL", or the address. */
    val title: String get() = when {
        imapHost.contains("gmail") -> "Gmail"
        imapHost.contains("outlook") || imapHost.contains("office365") -> "Outlook"
        imapHost.contains("yahoo") -> "Yahoo"
        imapHost.contains("me.com") -> "iCloud"
        else -> email.substringAfter('@')
    }
}

/** The servers of the usual providers, chosen from the address. */
object MailPresets {
    fun forEmail(email: String, name: String = ""): MailAccount {
        val domain = email.substringAfter('@').lowercase()
        fun acc(imap: String, smtp: String, smtpPort: Int, ssl: Boolean) =
            MailAccount(UUID.randomUUID().toString(), email, name, email, imap, 993, smtp, smtpPort, ssl)
        return when (domain) {
            "gmail.com", "googlemail.com" -> acc("imap.gmail.com", "smtp.gmail.com", 465, true)
            "outlook.com", "hotmail.com", "live.com", "msn.com", "outlook.com.br", "hotmail.com.br" -> acc("outlook.office365.com", "smtp-mail.outlook.com", 587, false)
            "yahoo.com", "yahoo.com.br", "ymail.com", "rocketmail.com" -> acc("imap.mail.yahoo.com", "smtp.mail.yahoo.com", 465, true)
            "icloud.com", "me.com", "mac.com" -> acc("imap.mail.me.com", "smtp.mail.me.com", 587, false)
            "uol.com.br" -> acc("imap.uol.com.br", "smtps.uol.com.br", 465, true)
            "bol.com.br" -> acc("imap.bol.com.br", "smtps.bol.com.br", 465, true)
            "terra.com.br" -> acc("imap.terra.com.br", "smtp.terra.com.br", 587, false)
            "zoho.com" -> acc("imap.zoho.com", "smtp.zoho.com", 465, true)
            else -> acc("imap.$domain", "smtp.$domain", 465, true)
        }
    }

    /** Providers that need an "app password" instead of the normal one for other mail apps. */
    fun needsAppPassword(email: String) = email.substringAfter('@').lowercase().let {
        it in setOf("gmail.com", "googlemail.com", "yahoo.com", "yahoo.com.br", "icloud.com", "me.com", "mac.com", "outlook.com", "hotmail.com", "live.com")
    }
}

/**
 * The mail accounts. Passwords are encrypted with a key kept in the phone's secure keystore
 * (Android Keystore); they never leave the phone except to log in to the account's servers.
 */
class MailAccounts private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("mail_accounts", Context.MODE_PRIVATE)
    private val _accounts = MutableStateFlow(read())
    val accounts: StateFlow<List<MailAccount>> = _accounts.asStateFlow()

    fun add(account: MailAccount, password: String) {
        prefs.edit().putString("pwd_" + account.id, encrypt(password)).apply()
        save(_accounts.value.filterNot { it.email.equals(account.email, true) } + account)
    }

    fun remove(id: String) {
        prefs.edit().remove("pwd_$id").apply()
        save(_accounts.value.filterNot { it.id == id })
    }

    fun password(id: String): String? = prefs.getString("pwd_$id", null)?.let { runCatching { decrypt(it) }.getOrNull() }

    private fun save(list: List<MailAccount>) {
        _accounts.value = list
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("email", it.email).put("name", it.name).put("user", it.user)
                    .put("imapHost", it.imapHost).put("imapPort", it.imapPort).put("smtpHost", it.smtpHost).put("smtpPort", it.smtpPort).put("smtpSsl", it.smtpSsl),
            )
        }
        prefs.edit().putString("accounts", a.toString()).apply()
    }

    private fun read(): List<MailAccount> = runCatching {
        val a = JSONArray(prefs.getString("accounts", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            MailAccount(
                o.getString("id"), o.getString("email"), o.optString("name"), o.optString("user", o.getString("email")),
                o.getString("imapHost"), o.optInt("imapPort", 993), o.getString("smtpHost"), o.optInt("smtpPort", 465), o.optBoolean("smtpSsl", true),
            )
        }
    }.getOrDefault(emptyList())

    // --- Android Keystore (AES/GCM) -----------------------------------------------------------
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(s.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        return String(c.doFinal(b, 12, b.size - 12))
    }

    companion object {
        private const val ALIAS = "mozaik_mail"
        @Volatile private var instance: MailAccounts? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: MailAccounts(context.applicationContext).also { instance = it } }
    }
}
