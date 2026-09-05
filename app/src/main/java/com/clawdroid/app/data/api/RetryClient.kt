package com.clawdroid.app.data.api

import kotlinx.coroutines.delay
import java.io.IOException

class RetryClient(
    private val maxRateLimitRetries: Int = 5,
    private val maxServerRetries: Int = 3,
) {
    suspend fun <T> run(block: suspend () -> T): T {
        var attempt = 0
        var serverRetries = 0
        var lastError: Throwable? = null

        while (attempt <= maxRateLimitRetries) {
            try {
                return block()
            } catch (error: ApiRetryableException) {
                lastError = error
                when (error.code) {
                    429 -> {
                        delay(error.retryAfterMs ?: exponentialDelay(attempt))
                        attempt += 1
                    }

                    500, 502, 503, 504 -> {
                        if (serverRetries >= maxServerRetries) throw error
                        delay(exponentialDelay(serverRetries))
                        serverRetries += 1
                    }

                    else -> throw error
                }
            } catch (error: IOException) {
                lastError = error
                if (serverRetries >= maxServerRetries) throw error
                delay(exponentialDelay(serverRetries))
                serverRetries += 1
            }
        }

        throw lastError ?: IllegalStateException("Retry attempts exhausted")
    }

    private fun exponentialDelay(attempt: Int): Long {
        val boundedAttempt = attempt.coerceIn(0, 4)
        return 1_000L shl boundedAttempt
    }
}

class ApiRetryableException(
    val code: Int,
    message: String,
    val retryAfterMs: Long? = null,
) : RuntimeException(message)
