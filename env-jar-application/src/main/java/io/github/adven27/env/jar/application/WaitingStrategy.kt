package io.github.adven27.env.jar.application

import mu.KLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

interface WaitingStrategy {
    fun wait(startupTimeout: Duration, vararg params: Any)
    fun ready(): Boolean

    class ExecutionFinished : WaitingStrategy {
        private var ready = false

        override fun ready() = ready

        override fun wait(startupTimeout: Duration, vararg params: Any) {
            try {
                val process = params[0] as Process
                if (!process.waitFor(startupTimeout.seconds, SECONDS)) {
                    logger.warn("Execution timeout exceeded. Destroying process forcibly.")
                    process.destroyForcibly()
                }
            } finally {
                ready = true
            }
        }

        companion object : KLogging()
    }

    class HealthCheckUrl(private val basePath: String) : WaitingStrategy {
        private val latch = CountDownLatch(1)
        private val poll = AtomicBoolean(true)

        override fun wait(startupTimeout: Duration, vararg params: Any) {
            try {
                waitStarted((params[0] as String) + basePath, startupTimeout)
            } finally {
                poll.set(false)
            }
        }

        override fun ready() = !poll.get()

        private fun waitStarted(url: String, startupTimeout: Duration) {
            Thread({ if (connected(URL(url))) latch.countDown() }, "Health check $url").start()
            if (!latch.await(startupTimeout.seconds, SECONDS)) {
                throw TimeoutException("Failed to connect to server within timeout $startupTimeout")
            }
        }

        @Suppress("NestedBlockDepth", "ReturnCount")
        private fun connected(url: URL): Boolean {
            do {
                try {
                    if (isOk(url)) return true else throw IllegalStateException("Unexpected status")
                } catch (expected: Exception) {
                    logger.info { "Health check failed: $url" }
                    if (pollingInterrupted()) break
                }
            } while (poll.get())
            return false
        }

        private fun pollingInterrupted() = if (poll.get()) isDelayInterrupted() else true

        private fun isDelayInterrupted() = try {
            Thread.sleep(POLL_DELAY_MILLIS)
            false
        } catch (e: InterruptedException) {
            true
        }

        private fun isOk(url: URL) = httpClient.newCall(Request.Builder().url(url).build()).execute().apply {
            logger.info { "Health check succeed: $this" }
        }.isSuccessful

        companion object : KLogging() {
            val httpClient = OkHttpClient()
            const val POLL_DELAY_MILLIS = 3000L
        }
    }
}
