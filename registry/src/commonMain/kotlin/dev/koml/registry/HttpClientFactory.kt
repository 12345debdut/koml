package dev.koml.registry

import io.ktor.client.HttpClient

/**
 * Each platform builds a Ktor [HttpClient] with the right native engine
 * (OkHttp on JVM/Android, Darwin on iOS) plus JSON content negotiation for
 * the HuggingFace Hub API.
 */
internal expect fun defaultRegistryHttpClient(): HttpClient
