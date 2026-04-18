package com.trust3.xcpro.testing

import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Shared timeout policy for JVM tests to prevent silent hangs.
 *
 * Defaults to 60 seconds per test method and allows explicit per-class
 * overrides up to 120 seconds for known heavy Robolectric tests.
 */
object TestTimeoutPolicy {
    private const val DEFAULT_TIMEOUT_SECONDS = 60L
    private const val MAX_TIMEOUT_SECONDS = 120L

    fun defaultRule(): Timeout = createRule(resolveConfiguredTimeoutSeconds())

    fun overrideRule(timeoutSeconds: Long): Timeout = createRule(timeoutSeconds.coerceAtMost(MAX_TIMEOUT_SECONDS))

    private fun createRule(timeoutSeconds: Long): Timeout = Timeout.builder()
        .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .withLookingForStuckThread(true)
        .build()

    private fun resolveConfiguredTimeoutSeconds(): Long {
        val configured = System.getProperty("xcpro.test.timeout.seconds")
            ?.toLongOrNull()
            ?.coerceIn(10L, MAX_TIMEOUT_SECONDS)
        return configured ?: DEFAULT_TIMEOUT_SECONDS
    }
}
