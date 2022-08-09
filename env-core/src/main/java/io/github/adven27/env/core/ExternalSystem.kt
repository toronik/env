package io.github.adven27.env.core

import io.github.adven27.env.core.Environment.Companion.propagateToSystemProperties
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

/**
 * Object responsible for managing some underlying system
 */
interface ExternalSystem {
    fun start(fixedEnv: Boolean)
    fun stop()
    fun running(): Boolean
    fun config(): ExternalSystemConfig
    fun describe(): String =
        toString() + config().properties.entries.joinToString(separator = "\n\t", prefix = "\n\t") { it.toString() }
}

open class GenericExternalSystem<T, C : ExternalSystemConfig> @JvmOverloads constructor(
    protected var system: T,
    private var config: C,
    private val start: BiFunction<Boolean, T, C>,
    private val stop: Consumer<T>,
    private val running: Function<T, Boolean> = Function { true },
    private val afterStart: T.() -> Unit = { },
) : ExternalSystem {
    override fun start(fixedEnv: Boolean) {
        config = start.apply(fixedEnv, system)
        afterStart.invoke(system)
    }

    override fun stop() = stop.accept(system)
    override fun running(): Boolean = running.apply(system)
    override fun config() = config
}

interface EnvironmentStrategy {
    fun fixedEnv(): Boolean

    class SystemPropertyToggle @JvmOverloads constructor(
        private val property: String = "SPECS_ENV_FIXED",
        private val orElse: Boolean = false,
    ) : EnvironmentStrategy {
        override fun fixedEnv(): Boolean = System.getProperty(property, orElse.toString()).toBoolean()
        override fun toString() = "System property toggle: $property, if absent: $orElse"
    }
}

open class ExternalSystemConfig(val properties: Map<String, String>) {
    constructor(vararg properties: Pair<String, String>) : this(properties.toMap())

    init {
        properties.propagateToSystemProperties()
    }
}
