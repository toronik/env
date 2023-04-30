package io.github.adven27.env.container

import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.core.GenericExternalSystem
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.function.BiFunction

/**
 * System implementation based on docker container
 */
@Suppress("unused")
open class ContainerExternalSystem<T : GenericContainer<*>, C : ExternalSystemConfig> @JvmOverloads constructor(
    system: T,
    start: BiFunction<Boolean, T, ExternalSystemConfig>,
    afterStart: T.() -> Unit = { }
) : GenericExternalSystem<T, ExternalSystemConfig>(
    system,
    start = start,
    stop = { it.stop() },
    running = { it.isRunning },
    afterStart = afterStart
)

fun String.parseImage(): DockerImageName = DockerImageName.parse(this)

infix fun String.asCompatibleSubstituteFor(other: String): DockerImageName =
    parseImage().asCompatibleSubstituteFor(other)

infix fun String.asCompatibleSubstituteFor(other: DockerImageName): DockerImageName =
    parseImage().asCompatibleSubstituteFor(other)
