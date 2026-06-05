package dev.koml.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

internal actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = Long.MAX_VALUE
        socketTimeoutMillis = 60_000
    }
    followRedirects = true
    expectSuccess = false
}
