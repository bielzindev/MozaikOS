package com.wpreborn.data.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the contact editor edits. Phone types are CommonDataKinds.Phone.TYPE_*. */
data class EditableContact(
    val first: String = "",
    val last: String = "",
    val phones: List<Pair<String, Int>> = emptyList(),
    val emails: List<String> = emptyList(),
    val company: String = "",
)

/**
 * Creates, edits and deletes contacts in the phone's contacts (on the device, or the account
 * the contact already belongs to), so People needs no other app. Requires WRITE_CONTACTS.
 */
class ContactWriter(private val context: Context) {
    private val resolver get() = context.contentResolver

    private fun rawContactId(contactId: Long): Long? = runCatching {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()

    suspend fun load(contactId: Long): EditableContact = withContext(Dispatchers.IO) {
        var first = ""
        var last = ""
        var company = ""
        val phones = ArrayList<Pair<String, Int>>()
        val emails = ArrayList<String>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3),
                "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    when (c.getString(0)) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            first = c.getString(2).orEmpty()
                            last = c.getString(3).orEmpty()
                            if (first.isEmpty() && last.isEmpty()) first = c.getString(1).orEmpty()
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> c.getString(1)?.let { phones += it to c.getInt(2) }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> c.getString(1)?.let { emails += it }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> company = c.getString(1).orEmpty()
                    }
                }
            }
        }
        EditableContact(first, last, phones.distinctBy { it.first }, emails.distinct(), company)
    }

    /** Saves [data] as a new contact (contactId null) or over an existing one. True when done. */
    suspend fun save(contactId: Long?, data: EditableContact): Boolean = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()
        val existing = contactId?.let(::rawContactId)
        fun data(mime: String) = if (existing != null) {
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValue(ContactsContract.Data.RAW_CONTACT_ID, existing)
        } else {
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        }.withValue(ContactsContract.Data.MIMETYPE, mime)
        if (existing == null) {
            // A contact on the phone itself (no account), like the phone's own app makes.
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        } else {
            // The edited kinds are written again from the editor.
            for (mime in listOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)) {
                ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?", arrayOf(existing.toString(), mime))
                    .build()
            }
        }
        ops += data(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, data.first.trim())
            .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, data.last.trim())
            .build()
        data.phones.filter { it.first.isNotBlank() }.forEach { (n, t) ->
            ops += data(CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Phone.NUMBER, n.trim()).withValue(CommonDataKinds.Phone.TYPE, t).build()
        }
        data.emails.filter { it.isNotBlank() }.forEach { e ->
            ops += data(CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Email.ADDRESS, e.trim()).withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME).build()
        }
        if (data.company.isNotBlank()) {
            ops += data(CommonDataKinds.Organization.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Organization.COMPANY, data.company.trim()).build()
        }
        runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops); true }.getOrDefault(false)
    }

    suspend fun delete(contact: Contact): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contact.lookupKey)
            resolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
}
