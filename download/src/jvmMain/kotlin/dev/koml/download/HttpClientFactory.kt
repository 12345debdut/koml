package dev.koml.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = 60_000
    }
    followRedirects = true
    expectSuccess = false
}
