package dev.koml.core.config

/**
 * Configuration passed to [dev.koml.engine.LlmKit.initialize].
 *
 * @property maxConcurrentSessions hard cap on how many [dev.koml.core.LlmSession]
 *   instances can be loaded at the same time. Each session owns its own
 *   native model + context allocation; this bound protects you from
 *   OOM-on-mobile when an app accidentally loads multiple large models.
 */
data class LlmKitConfig(
    val maxConcurrentSessions: Int = 1,
)
