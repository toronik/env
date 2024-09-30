package io.github.adven27.env.jar.application

import io.github.adven27.env.core.Environment.Companion.findAvailableTcpPort
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.jar.application.WaitingStrategy.HealthCheckUrl
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import java.io.File
import java.time.Duration
import java.time.Duration.ofSeconds
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.locks.ReentrantLock

private val logger = logger {}

@Suppress("SpreadOperator")
abstract class JarApplication @JvmOverloads constructor(
    private var jar: File,
    private val waitingStrategy: WaitingStrategy,
    protected var configureBeforeStart: (fixedEnv: Boolean, config: Config) -> Config = { _, cfg -> cfg },
    protected val startupTimeout: Duration = START_TIMEOUT
) : ExternalSystem {
    constructor(
        jar: File,
        waitingStrategy: WaitingStrategy,
        config: Config = Config(),
        startupTimeout: Duration = START_TIMEOUT
    ) : this(jar, waitingStrategy, { _, cfg -> cfg }, startupTimeout)

    override lateinit var config: Config
    private val system = ProcessBuilder().directory(jar.parentFile).inheritIO()
    private val lock = ReentrantLock()
    protected lateinit var process: Process

    init {
        check(jar.exists() && jar.canRead()) { "The jar file must exist and be readable: $jar" }
    }

    override fun running() = waitingStrategy.ready()
    override fun describe() = process.toString() + " " + system.command() + "waiting " + waitingStrategy
    abstract fun waitStarted(startupTimeout: Duration)

    override fun start(fixedEnv: Boolean) {
        lock.lock()
        try {
            config = configureBeforeStart(fixedEnv, config)
            system.command((listOf("java") + config.systemProperties + listOf("-jar", jar.name) + config.args))
            logger.info("Starting $jar: ${system.command()}")
            process = system.start()
            waitStarted(startupTimeout)
        } catch (expected: Throwable) {
            stop()
            throw expected
        } finally {
            lock.unlock()
        }
    }

    override fun stop() {
        lock.lock()
        try {
            logger.info { "Stopping process..." }
            process.destroyForcibly()?.waitFor(STOP_TIMEOUT.seconds, SECONDS)
        } finally {
            logger.info { "Process stopped" }
            lock.unlock()
        }
    }

    class Config(
        val args: Array<String> = arrayOf(),
        val systemProperties: Array<String> = arrayOf()
    ) : ExternalSystemConfig() {
        fun addArgs(vararg args: String) = Config(this.args + arrayOf(*args), this.systemProperties)
        fun addSystemProperties(vararg properties: String) =
            Config(this.args, this.systemProperties + arrayOf(*properties))
    }

    companion object {
        val START_TIMEOUT: Duration = ofSeconds(180)
        val STOP_TIMEOUT: Duration = ofSeconds(15)
    }
}

@Suppress("unused")
open class JarTask @JvmOverloads constructor(
    jar: File,
    configureBeforeStart: (fixedEnv: Boolean, config: Config) -> Config = { _, cfg -> cfg },
    startupTimeout: Duration = START_TIMEOUT,
    private val waitingStrategy: WaitingStrategy = WaitingStrategy.ExecutionFinished()
) : JarApplication(jar, waitingStrategy, configureBeforeStart, startupTimeout) {

    override fun waitStarted(startupTimeout: Duration) = waitingStrategy.wait(startupTimeout, process)
}

@Suppress("LongParameterList", "unused")
open class JarWebService @JvmOverloads constructor(
    jar: File,
    private val defaultPort: Int,
    config: Config,
    private val configurePort: (fixedEnv: Boolean, port: Int, config: Config) -> Config = { _, _, cfg -> cfg },
    healthPath: String = "/actuator/info",
    startupTimeout: Duration = START_TIMEOUT,
    private val waitingStrategy: WaitingStrategy = HealthCheckUrl(healthPath)
) : JarApplication(jar, waitingStrategy, config, startupTimeout) {
    var port: Int = defaultPort

    override fun start(fixedEnv: Boolean) {
        port = if (fixedEnv) defaultPort else findAvailableTcpPort()
        configureBeforeStart = { f, config -> configurePort(f, port, config) }
        super.start(fixedEnv)
    }

    override fun waitStarted(startupTimeout: Duration) =
        waitingStrategy.wait(startupTimeout, "http://localhost:$port")
}
