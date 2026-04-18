package com.trust3.xcpro.tasks

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

fun shareRequest(context: Context, request: ShareRequest) {
    val uris = request.allDocuments
        .map { Uri.parse(it.uri) }
        .distinct()

    if (uris.isEmpty()) {
        return
    }

    val shareIntent = Intent().apply {
        if (uris.size == 1) {
            action = Intent.ACTION_SEND
            type = request.mime
            putExtra(Intent.EXTRA_STREAM, uris.first())
            clipData = ClipData.newUri(context.contentResolver, "task_file", uris.first())
        } else {
            action = Intent.ACTION_SEND_MULTIPLE
            type = if (request.mime.isBlank()) "*/*" else request.mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            val first = uris.first()
            val clip = ClipData.newUri(context.contentResolver, "task_files", first)
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            clipData = clip
        }
        request.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        request.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, request.chooserTitle))
}
