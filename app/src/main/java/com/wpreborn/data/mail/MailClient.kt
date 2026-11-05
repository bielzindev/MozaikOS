package com.wpreborn.data.mail

import android.content.Context
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility

data class MailHeader(
    val uid: Long,
    val from: String,
    val fromAddress: String,
    val subject: String,
    val date: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val preview: String,
    val attachments: Boolean,
)

data class MailAttachment(val index: Int, val name: String, val size: Int, val mime: String)

data class MailMessage(
    val header: MailHeader,
    val to: String,
    val cc: String,
    val html: String?,
    val text: String?,
    val attachments: List<MailAttachment>,
    val messageId: String?,
    val replyTo: String,
)

data class MailFolder(val fullName: String, val name: String, val unread: Int, val special: String?)

/** A failed login or connection, with a message to show. */
class MailException(message: String, val auth: Boolean = false) : Exception(message)

/**
 * Talks to the mail servers: IMAP for reading (folders, messages, flags, moving to the trash)
 * and SMTP for sending, with JavaMail. One connection per account is kept open and reused.
 */
object MailClient {
    private val lock = Mutex()
    private val stores = ConcurrentHashMap<String, Store>()

    private fun props(a: MailAccount) = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", a.imapHost)
        put("mail.imaps.port", a.imapPort.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.connectiontimeout", "15000")
        put("mail.imaps.timeout", "30000")
        put("mail.smtp.host", a.smtpHost)
        put("mail.smtp.port", a.smtpPort.toString())
        put("mail.smtp.auth", "true")
        if (a.smtpSsl) put("mail.smtp.ssl.enable", "true") else put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "30000")
        put("mail.mime.charset", "UTF-8")
    }

    private fun store(a: MailAccount, pwd: String): Store {
        stores[a.id]?.takeIf { it.isConnected }?.let { return it }
        val st = Session.getInstance(props(a)).getStore("imaps")
        try {
            st.connect(a.imapHost, a.imapPort, a.user, pwd)
        } catch (e: AuthenticationFailedException) {
            throw MailException(e.message ?: "authentication failed", auth = true)
        }
        stores[a.id] = st
        return st
    }

    private suspend fun <T> io(a: MailAccount, pwd: String, block: (Store) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                block(store(a, pwd))
            } catch (e: MailException) {
                throw e
            } catch (e: javax.mail.MessagingException) {
                // A dropped connection: once more with a fresh one.
                stores.remove(a.id)?.let { runCatching { it.close() } }
                try {
                    block(store(a, pwd))
                } catch (e2: Exception) {
                    throw MailException(e2.message ?: e2.javaClass.simpleName)
                }
            }
        }
    }

    /** Logs in to both servers; null when it works, else what went wrong. */
    suspend fun test(a: MailAccount, pwd: String): MailException? = withContext(Dispatchers.IO) {
        try {
            stores.remove(a.id)?.let { runCatching { it.close() } }
            lock.withLock { store(a, pwd) }
            val t = Session.getInstance(props(a)).getTransport("smtp")
            try {
                t.connect(a.smtpHost, a.smtpPort, a.user, pwd)
            } catch (e: AuthenticationFailedException) {
                throw MailException(e.message ?: "authentication failed", auth = true)
            } finally {
                runCatching { t.close() }
            }
            null
        } catch (e: MailException) {
            e
        } catch (e: Exception) {
            MailException(e.message ?: e.javaClass.simpleName)
        }
    }

    fun forget(a: MailAccount) {
        stores.remove(a.id)?.let { runCatching { it.close() } }
    }

    private fun name(a: InternetAddress?) = a?.personal?.takeIf { it.isNotBlank() } ?: a?.address.orEmpty()

    private fun header(f: IMAPFolder, m: Message, preview: String = "") = MailHeader(
        uid = f.getUID(m),
        from = name(m.from?.firstOrNull() as? InternetAddress),
        fromAddress = (m.from?.firstOrNull() as? InternetAddress)?.address.orEmpty(),
        subject = m.subject.orEmpty(),
        date = (m.sentDate ?: m.receivedDate ?: Date()).time,
        seen = m.isSet(Flags.Flag.SEEN),
        flagged = m.isSet(Flags.Flag.FLAGGED),
        preview = preview,
        attachments = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false),
    )

    private fun textOf(p: Part, depth: Int = 0): Pair<String?, String?> {
        if (depth > 8) return null to null
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return null to null
            var html: String? = null
            var text: String? = null
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(bp.disposition, true)) continue
                val (h, t) = textOf(bp, depth + 1)
                if (html == null) html = h
                if (text == null) text = t
            }
            return html to text
        }
        if (Part.ATTACHMENT.equals(p.disposition, true)) return null to null
        return when {
            p.isMimeType("text/html") -> (p.content as? String) to null
            p.isMimeType("text/plain") -> null to (p.content as? String)
            else -> null to null
        }
    }

    private fun previewOf(m: Message): String = runCatching {
        val (html, text) = textOf(m)
        val raw = text ?: html?.let { org.jsoup.Jsoup.parse(it).text() }.orEmpty()
        raw.replace(Regex("\\s+"), " ").trim().take(160)
    }.getOrDefault("")

    private fun attachmentsOf(p: Part, out: MutableList<Part>, depth: Int = 0) {
        if (depth > 8) return
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return
            for (i in 0 until mp.count) attachmentsOf(mp.getBodyPart(i), out, depth + 1)
        } else if (Part.ATTACHMENT.equals(p.disposition, true) || (p.fileName != null && !p.isMimeType("text/*"))) {
            out += p
        }
    }

    /** The newest [count] messages of [folder], newest first; the first ones with a preview line. */
    suspend fun headers(a: MailAccount, pwd: String, folder: String = "INBOX", count: Int = 50): List<MailHeader> = io(a, pwd) { st ->
        val f = st.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_ONLY)
        try {
            val total = f.messageCount
            if (total == 0) return@io emptyList()
            val msgs = f.getMessages(maxOf(1, total - count + 1), total)
            f.fetch(msgs, FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add(UIDFolder.FetchProfileItem.UID)
            })
            msgs.reversed().mapIndexed { i, m -> header(f, m, if (i < 20) previewOf(m) else "") }
        } finally {
            runCatching { f.close(false) }
        }
    }

    suspend fun message(a: MailAccount, pwd: String, folder: String, uid: Long): MailMessage? = io(a, pwd) { st ->
        val f = st.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_WRITE)
        try {
            val m = f.getMessageByUID(uid) ?: return@io null
            val (html, text) = textOf(m)
            val parts = ArrayList<Part>()
            attachmentsOf(m, parts)
            if (!m.isSet(Flags.Flag.SEEN)) m.setFlag(Flags.Flag.SEEN, true)
            MailMessage(
                header = header(f, m),
                to = m.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { name(it as? InternetAddress) }.orEmpty(),
                cc = m.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { name(it as? InternetAddress) }.orEmpty(),
                html = html,
                text = text,
                attachments = parts.mapIndexed { i, p ->
                    MailAttachment(i, runCatching { MimeUtility.decodeText(p.fileName) }.getOrNull() ?: "anexo-${i + 1}", p.size, p.contentType.substringBefore(';').lowercase())
                },
                messageId = (m as? MimeMessage)?.messageID,
                replyTo = (m.replyTo?.firstOrNull() as? InternetAddress)?.address ?: (m.from?.firstOrNull() as? InternetAddress)?.address.orEmpty(),
            )
        } finally {
            runCatching { f.close(false) }
        }
    }

    /** Saves attachment [index] of a message into [dir]; returns the file. */
    suspend fun attachment(a: MailAccount, pwd: String, folder: String, uid: Long, index: Int, dir: File): File? = io(a, pwd) { st ->
        val f = st.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_ONLY)
        try {
            val m = f.getMessageByUID(uid) ?: return@io null
            val parts = ArrayList<Part>()
            attachmentsOf(m, parts)
            val p = parts.getOrNull(index) as? MimeBodyPart ?: return@io null
            val name = (runCatching { MimeUtility.decodeText(p.fileName) }.getOrNull() ?: "anexo").replace('/', '_')
            dir.mkdirs()
            val out = File(dir, name)
            p.saveFile(out)
            out
        } finally {
            runCatching { f.close(false) }
        }
    }

    suspend fun setFlag(a: MailAccount, pwd: String, folder: String, uid: Long, flag: Flags.Flag, on: Boolean) = io(a, pwd) { st ->
        val f = st.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_WRITE)
        try {
            f.getMessageByUID(uid)?.setFlag(flag, on)
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun special(st: Store, attr: String, names: List<String>): Folder? {
        val all = runCatching { st.defaultFolder.list("*").toList() }.getOrDefault(emptyList())
        return all.firstOrNull { (it as? IMAPFolder)?.attributes?.any { a -> a.equals(attr, true) } == true }
            ?: all.firstOrNull { f -> names.any { it.equals(f.fullName, true) || it.equals(f.name, true) } }
    }

    /** Moves a message to the trash (or deletes it for good when it is already there). */
    suspend fun delete(a: MailAccount, pwd: String, folder: String, uid: Long) = io(a, pwd) { st ->
        val trash = special(st, "\\Trash", listOf("[Gmail]/Trash", "[Gmail]/Lixeira", "Trash", "Lixeira", "Deleted Items", "Itens Excluídos", "Deleted"))
        val f = st.getFolder(folder) as IMAPFolder
        f.open(Folder.READ_WRITE)
        try {
            val m = f.getMessageByUID(uid) ?: return@io
            if (trash != null && !trash.fullName.equals(folder, true)) f.copyMessages(arrayOf(m), trash)
            m.setFlag(Flags.Flag.DELETED, true)
            f.expunge()
        } finally {
            runCatching { f.close(false) }
        }
    }

    suspend fun folders(a: MailAccount, pwd: String): List<MailFolder> = io(a, pwd) { st ->
        st.defaultFolder.list("*").filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }.map { f ->
            val attrs = (f as? IMAPFolder)?.attributes.orEmpty().map { it.lowercase() }
            val special = listOf("\\inbox", "\\sent", "\\drafts", "\\trash", "\\junk", "\\flagged", "\\all", "\\archive").firstOrNull { it in attrs }
                ?: if (f.fullName.equals("INBOX", true)) "\\inbox" else null
            MailFolder(f.fullName, f.name, runCatching { f.unreadMessageCount }.getOrDefault(0), special)
        }.sortedWith(compareBy({ if (it.special == "\\inbox") 0 else if (it.special != null) 1 else 2 }, { it.name.lowercase() }))
    }

    /** Unread messages in the inbox and the newest uid (for notifications). */
    suspend fun inboxStatus(a: MailAccount, pwd: String): Pair<Int, List<MailHeader>> = io(a, pwd) { st ->
        val f = st.getFolder("INBOX") as IMAPFolder
        f.open(Folder.READ_ONLY)
        try {
            val unread = f.unreadMessageCount
            val total = f.messageCount
            val msgs = if (total == 0) emptyArray() else f.getMessages(maxOf(1, total - 9), total)
            f.fetch(msgs, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID) })
            unread to msgs.reversed().map { header(f, it) }
        } finally {
            runCatching { f.close(false) }
        }
    }

    /** Sends a message (and keeps a copy in Sent where the server does not do it itself). */
    suspend fun send(
        a: MailAccount,
        pwd: String,
        to: String,
        cc: String,
        subject: String,
        body: String,
        inReplyTo: String? = null,
        files: List<File> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        val session = Session.getInstance(props(a))
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(a.email, a.name.ifBlank { null }, "UTF-8"))
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.replace(';', ','), false))
        if (cc.isNotBlank()) msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc.replace(';', ','), false))
        msg.setSubject(subject, "UTF-8")
        msg.sentDate = Date()
        if (inReplyTo != null) {
            msg.setHeader("In-Reply-To", inReplyTo)
            msg.setHeader("References", inReplyTo)
        }
        if (files.isEmpty()) {
            msg.setText(body, "UTF-8")
        } else {
            val mp = MimeMultipart()
            mp.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
            files.forEach { file -> mp.addBodyPart(MimeBodyPart().apply { attachFile(file); fileName = MimeUtility.encodeText(file.name, "UTF-8", null) }) }
            msg.setContent(mp)
        }
        msg.saveChanges()
        val t = session.getTransport("smtp")
        try {
            t.connect(a.smtpHost, a.smtpPort, a.user, pwd)
            t.sendMessage(msg, msg.allRecipients)
        } catch (e: AuthenticationFailedException) {
            throw MailException(e.message ?: "authentication failed", auth = true)
        } catch (e: Exception) {
            throw MailException(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { t.close() }
        }
        // Gmail files sent mail itself; other servers need the copy.
        if (!a.imapHost.contains("gmail")) {
            runCatching {
                io(a, pwd) { st ->
                    special(st, "\\Sent", listOf("Sent", "Sent Items", "Itens Enviados", "Enviados", "Sent Messages"))?.let { sent ->
                        msg.setFlag(Flags.Flag.SEEN, true)
                        sent.appendMessages(arrayOf(msg))
                    }
                }
            }
        }
    }
}

/** The last list of each folder, so Mail opens at once and works without a connection. */
class MailCache(context: Context) {
    private val prefs = context.getSharedPreferences("mail_cache", Context.MODE_PRIVATE)

    fun load(account: String, folder: String): List<MailHeader> = runCatching {
        val a = JSONArray(prefs.getString("$account|$folder", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            MailHeader(o.getLong("u"), o.optString("f"), o.optString("fa"), o.optString("s"), o.optLong("d"), o.optBoolean("r"), o.optBoolean("fl"), o.optString("p"), o.optBoolean("a"))
        }
    }.getOrDefault(emptyList())

    fun save(account: String, folder: String, list: List<MailHeader>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("u", it.uid).put("f", it.from).put("fa", it.fromAddress).put("s", it.subject).put("d", it.date).put("r", it.seen).put("fl", it.flagged).put("p", it.preview).put("a", it.attachments))
        }
        prefs.edit().putString("$account|$folder", a.toString()).apply()
    }
}
