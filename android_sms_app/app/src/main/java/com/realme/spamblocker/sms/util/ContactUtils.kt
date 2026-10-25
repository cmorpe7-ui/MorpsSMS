package com.realme.spamblocker.sms.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

data class ContactDetails(
    val name: String?,
    val photoUri: String?,
    val isContact: Boolean
)

object ContactUtils {

    private val cache = ConcurrentHashMap<String, ContactDetails>()

    /**
     * Resolves display name, contact photo URI, and contact existence with in-memory caching.
     */
    fun getContactDetails(context: Context, phoneNumber: String): ContactDetails {
        if (phoneNumber.isBlank()) return ContactDetails(null, null, false)
        val cleanNumber = phoneNumber.trim()
        cache[cleanNumber]?.let { return it }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleanNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            val details = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val thumbIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    val photoIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)

                    val name = if (nameIdx != -1) it.getString(nameIdx) else null
                    val thumb = if (thumbIdx != -1) it.getString(thumbIdx) else null
                    val photo = if (photoIdx != -1) it.getString(photoIdx) else null

                    ContactDetails(name = name, photoUri = thumb ?: photo, isContact = true)
                } else {
                    ContactDetails(name = null, photoUri = null, isContact = false)
                }
            } ?: ContactDetails(name = null, photoUri = null, isContact = false)

            cache[cleanNumber] = details
            details
        } catch (e: Exception) {
            ContactDetails(name = null, photoUri = null, isContact = false)
        }
    }

    /**
     * Checks if a phone number exists in user's contacts.
     */
    fun isContact(context: Context, phoneNumber: String): Boolean {
        return getContactDetails(context, phoneNumber).isContact
    }

    /**
     * Resolves the display name of a contact, or returns null if not saved.
     */
    fun getContactName(context: Context, phoneNumber: String): String? {
        return getContactDetails(context, phoneNumber).name
    }

    /**
     * Resolves contact photo thumbnail URI if available.
     */
    fun getContactPhotoUri(context: Context, phoneNumber: String): String? {
        return getContactDetails(context, phoneNumber).photoUri
    }

    fun clearCache() {
        cache.clear()
    }
}
