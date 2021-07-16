package io.github.adven27.env.core

import java.util.function.Consumer
import java.util.function.Function

/**
 * Object responsible for managing some underlying system
 */
interface ExternalSystem {
    fun start()
    fun stop()
    fun running(): Boolean
    fun config(): Any
    fun describe(): String = toString()

    companion object {
        /*
        TODO https://youtrack.jetbrains.com/issue/KT-35716
        @JvmStatic
        @JvmOverloads
        */
        fun <T> generic(
            system: T,
            start: Consumer<T> = Consumer {},
            stop: Consumer<T> = Consumer {},
            running: Function<T, Boolean> = Function { true }
        ) = GenericExternalSystem(system, start, stop, running)
    }
}

open class GenericExternalSystem<T> @JvmOverloads constructor(
    val system: T,
    private val start: Consumer<T> = Consumer {},
    private val stop: Consumer<T> = Consumer {},
    private val running: Function<T, Boolean> = Function { true },
    private val afterStart: T.() -> Unit = { }
) : ExternalSystem {
    override fun start() {
        start.accept(system)
        afterStart.invoke(system)
    }

    override fun stop() {
        stop.accept(system)
    }

    override fun running(): Boolean = running.apply(system)
    override fun describe() = system.toString()
    override fun config() = Any()
}

interface FixedDynamicEnvironmentStrategy {
    fun fixedEnv(): Boolean

    class SystemPropertyToggle @JvmOverloads constructor(
        private val property: String = "SPECS_ENV_FIXED",
        private val orElse: Boolean = false
    ) : FixedDynamicEnvironmentStrategy {
        override fun fixedEnv(): Boolean = System.getProperty(property, orElse.toString()).toBoolean()
    }
}
