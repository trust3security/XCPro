package com.example.xcpro.testing

import okhttp3.OkHttpClient

class OkHttpClientRegistry {
    private val clients = mutableListOf<OkHttpClient>()

    fun register(client: OkHttpClient): OkHttpClient {
        clients += client
        return client
    }

    fun shutdownAll() {
        clients.forEach { client ->
            client.shutdownForTests()
        }
        clients.clear()
    }
}

fun OkHttpClient.shutdownForTests() {
    runCatching { dispatcher.cancelAll() }
    runCatching { connectionPool.evictAll() }
    runCatching { cache?.close() }
    runCatching { dispatcher.executorService.shutdownNow() }
}
