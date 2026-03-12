package com.example.xcpro.weglide.data

import android.content.Context
import android.net.Uri
import com.example.xcpro.common.documents.DocumentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class WeGlideIgcDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun readBytes(documentUri: String): ByteArray = withContext(Dispatchers.IO) {
        openStream(documentUri).use { stream ->
            stream.readBytes()
        }
    }

    suspend fun sha256Hex(documentUri: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        openStream(documentUri).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun asDocumentRef(documentUri: String): DocumentRef = DocumentRef(uri = documentUri)

    private fun openStream(documentUri: String): InputStream {
        val parsed = Uri.parse(documentUri)
        return if (parsed.scheme.isNullOrBlank()) {
            FileInputStream(File(documentUri))
        } else {
            context.contentResolver.openInputStream(parsed)
                ?: error("Unable to open IGC document: $documentUri")
        }
    }
}
