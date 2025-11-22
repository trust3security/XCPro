package com.example.ui1.screens

import android.net.Uri

data class FileItem(
    val name: String,
    val enabled: Boolean,
    val count: Int,
    val status: String,
    val uri: Uri
)

data class AirspaceClassItem(
    val className: String,
    val enabled: Boolean,
    val color: String,
    val description: String
)
