package com.wpreborn.data.contacts

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: Uri?,
    val thumbUri: Uri?,
    val starred: Boolean,
    val hasPhone: Boolean,
)

data class ContactPhone(val number: String, val type: String)
data class ContactEmail(val address: String, val type: String)

data class ContactDetail(
    val contact: Contact,
    val phones: List<ContactPhone>,
    val emails: List<ContactEmail>,
    val addresses: List<String>,
    val birthday: String?,
    val organization: String?,
)

/** Wraps the Android contacts provider. Requires READ_CONTACTS. */
class ContactsRepository(private val context: Context) {

    suspend fun all(): List<Contact> = withContext(Dispatchers.IO) {
        val list = ArrayList<Contact>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ),
                null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(2)
                    if (name != null) list += Contact(
                        id = c.getLong(0),
                        lookupKey = c.getString(1) ?: "",
                        name = name,
                        photoUri = c.getString(3)?.let(Uri::parse),
                        thumbUri = c.getString(4)?.let(Uri::parse),
                        starred = c.getInt(5) == 1,
                        hasPhone = c.getInt(6) == 1,
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        list
    }

    suspend fun detail(contact: Contact): ContactDetail = withContext(Dispatchers.IO) {
        val phones = ArrayList<ContactPhone>()
        val emails = ArrayList<ContactEmail>()
        val addresses = ArrayList<String>()
        var birthday: String? = null
        var org: String? = null
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3,
                ),
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contact.id.toString()),
                null,
            )?.use { c ->
                val res = context.resources
                while (c.moveToNext()) {
                    val data = c.getString(1) ?: ""
                    val type = c.getInt(2)
                    val label = c.getString(3)
                    if (data.isNotEmpty()) when (c.getString(0)) {
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val t = CommonDataKinds.Phone.getTypeLabel(res, type, label).toString()
                            if (phones.none { normalize(it.number) == normalize(data) }) phones += ContactPhone(data, t)
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                            emails += ContactEmail(data, CommonDataKinds.Email.getTypeLabel(res, type, label).toString())
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> addresses += data
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                            if (type == CommonDataKinds.Event.TYPE_BIRTHDAY) birthday = data
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> org = data
                    }
                }
            }
        } catch (_: SecurityException) {
        }
        ContactDetail(contact, phones, emails, addresses, birthday, org)
    }

    /** Toggles the "favorite" flag. Needs WRITE_CONTACTS; returns false when not allowed. */
    suspend fun setStarred(contact: Contact, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    /** Looks up a display name and photo for a phone number (call log, SMS threads). */
    fun lookupNumber(number: String): Pair<String, Uri?>? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1)?.let(Uri::parse) else null }
        } catch (_: Exception) {
            null
        }
    }

    fun contactUri(contact: Contact): Uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)

    companion object {
        fun normalize(n: String) = n.filter { it.isDigit() || it == '+' }.takeLast(9)
    }
}
