package com.example.xcpro.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri

fun shareRequest(context: Context, request: ShareRequest) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = request.mime
        putExtra(Intent.EXTRA_STREAM, Uri.parse(request.document.uri))
        request.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        request.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, request.chooserTitle))
}
