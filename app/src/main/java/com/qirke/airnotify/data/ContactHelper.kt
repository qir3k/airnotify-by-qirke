package com.qirke.airnotify.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactHelper {

    fun hasContactPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolves an incoming phone number to a display name in the user's contacts.
     */
    fun resolveContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank() || !hasContactPermission(context)) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Returns a list of contact names marked as starred/favorites in Android contacts.
     */
    fun getStarredContacts(context: Context): List<String> {
        if (!hasContactPermission(context)) return emptyList()

        val results = mutableListOf<String>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.STARRED
        )
        val selection = "${ContactsContract.Contacts.STARRED} = 1"

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, projection, selection, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (it.moveToNext()) {
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            results.add(name.trim())
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return results.distinct().sorted()
    }

    /**
     * Returns all contacts (name to primary number) to allow user selection.
     */
    fun getAllContacts(context: Context): List<Pair<String, String>> {
        if (!hasContactPermission(context)) return emptyList()

        val list = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx)
                    val num = it.getString(numIdx)
                    if (!name.isNullOrBlank()) {
                        list.add(name.trim() to (num ?: ""))
                    }
                }
            }
        } catch (_: Exception) {}

        return list.distinctBy { it.first }
    }
}
