package com.example.xcpro.screens.replay

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.xcpro.igc.usecase.IgcShareRequest

internal fun buildIgcShareChooserIntent(
    context: Context,
    request: IgcShareRequest
): Intent {
    val uri = Uri.parse(request.document.uri)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = request.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, request.subject)
        putExtra(Intent.EXTRA_TEXT, request.text)
        clipData = ClipData.newUri(context.contentResolver, "igc_file", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(shareIntent, request.chooserTitle)
}

internal fun launchIgcShareChooser(
    context: Context,
    request: IgcShareRequest
): Result<Unit> {
    return runCatching {
        context.startActivity(buildIgcShareChooserIntent(context, request))
    }
}
