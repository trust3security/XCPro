package com.example.xcpro.map.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Resolve the display name for a SAF Uri, if present.
 */
internal fun resolveDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}
