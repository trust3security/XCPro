package com.trust3.xcpro.map.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.trust3.xcpro.common.documents.DocumentRef

/**
 * Resolve the display name for a SAF Uri, if present.
 */
internal fun resolveDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}

internal fun documentRefForUri(context: Context, uri: Uri): DocumentRef =
    DocumentRef(uri = uri.toString(), displayName = resolveDisplayName(context, uri))
