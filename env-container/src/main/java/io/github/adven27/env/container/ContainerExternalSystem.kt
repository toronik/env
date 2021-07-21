package io.github.adven27.env.container

import io.github.adven27.env.core.GenericExternalSystem
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.function.BiFunction

/**
 * System implementation based on docker container
 */
@Suppress("unused")
open class ContainerExternalSystem<T : GenericContainer<*>, C : Any> @JvmOverloads constructor(
    system: T,
    start: BiFunction<Boolean, T, Any>,
    afterStart: T.() -> Unit = { }
) : GenericExternalSystem<T, Any>(
    system,
    Any(),
    start = start,
    stop = { it.stop() },
    running = { it.isRunning },
    afterStart = afterStart,
)

fun String.parseImage(): DockerImageName = DockerImageName.parse(this)

infix fun String.asCompatibleSubstituteFor(other: String): DockerImageName =
    parseImage().asCompatibleSubstituteFor(other)

infix fun String.asCompatibleSubstituteFor(other: DockerImageName): DockerImageName =
    parseImage().asCompatibleSubstituteFor(other)
