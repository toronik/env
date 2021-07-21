package io.github.adven27.env.core

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
    fun config(): Any
    fun describe(): String = toString()
}

open class GenericExternalSystem<T, C : Any> @JvmOverloads constructor(
    protected var system: T,
    private var config: C,
    private val start: BiFunction<Boolean, T, C>,
    private val stop: Consumer<T>,
    private val running: Function<T, Boolean> = Function { true },
    private val afterStart: T.() -> Unit = { }
) : ExternalSystem {
    override fun start(fixedEnv: Boolean) {
        config = start.apply(fixedEnv, system)
        afterStart.invoke(system)
    }

    override fun stop() {
        stop.accept(system)
    }

    override fun running(): Boolean = running.apply(system)
    override fun describe() = system.toString()
    override fun config() = config
}

interface EnvironmentStrategy {
    fun fixedEnv(): Boolean

    class SystemPropertyToggle @JvmOverloads constructor(
        private val property: String = "SPECS_ENV_FIXED",
        private val orElse: Boolean = false
    ) : EnvironmentStrategy {
        override fun fixedEnv(): Boolean = System.getProperty(property, orElse.toString()).toBoolean()
    }
}
