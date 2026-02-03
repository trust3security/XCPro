package com.example.ui1.screens

import com.example.xcpro.common.documents.DocumentRef

data class FileItem(
    val name: String,
    val enabled: Boolean,
    val count: Int,
    val status: String,
    val document: DocumentRef
)

data class AirspaceClassItem(
    val className: String,
    val enabled: Boolean,
    val color: String,
    val description: String
)
