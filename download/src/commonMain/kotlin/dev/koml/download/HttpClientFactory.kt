package dev.koml.download

import io.ktor.client.HttpClient

/**
 * Each platform builds its own [HttpClient] backed by the most appropriate
 * engine (OkHttp on JVM/Android, Darwin on iOS). Common configuration —
 * timeouts, redirect-following, etc. — happens inside each actual.
 */
internal expect fun defaultHttpClient(): HttpClient
