package dev.koml.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        // GGUF downloads can take minutes — never time out the whole request
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = 60_000
    }
    followRedirects = true
    expectSuccess = false // we inspect status codes manually
}
