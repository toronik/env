package io.github.adven27.env.db.postgresql

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.envRemoteOn
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem.Config.Companion.PROP_DRIVER
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem.Config.Companion.PROP_PASSWORD
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem.Config.Companion.PROP_URL
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem.Config.Companion.PROP_USER
import org.testcontainers.utility.DockerImageName

/**
 * Type-safe interface for all PostgreSQL systems: a container ([PostgreSqlContainerSystem]) or an already running
 * server ([resettablePostgres]). Resolve it from an environment with `env<PostgreSqlSystem>()`.
 */
interface PostgreSqlSystem : ExternalSystem {
    /** Connection settings, the same for every implementation. Available after start. */
    fun jdbc(): PostgreSqlContainerSystem.Config = with(config.properties) {
        PostgreSqlContainerSystem.Config(
            jdbcUrl = getValue(PROP_URL),
            username = getValue(PROP_USER),
            password = getValue(PROP_PASSWORD),
            driver = getValue(PROP_DRIVER)
        )
    }
}

const val TEST_DB_URL = "TEST_DB_URL"
const val TEST_DB_USER = "TEST_DB_USER"
const val TEST_DB_PASSWORD = "TEST_DB_PASSWORD"

private const val POSTGRESQL_DRIVER = "org.postgresql.Driver"

/**
 * PostgreSQL picked by the [io.github.adven27.env.core.ENV_REMOTE] toggle.
 *
 * - `ENV_REMOTE=true`: the database `TEST_DB_URL` (+ [dbSuffix]) on an already running server, dropped and
 *   created again on start (`DROP DATABASE ... WITH (FORCE)`), so `TEST_DB_URL` is required.
 *   `TEST_DB_USER`/`TEST_DB_PASSWORD` default to `test`.
 * - otherwise: [PostgreSqlContainerSystem] from [image]; Docker is needed only when it starts.
 *
 * Use [PostgreSqlSystem.jdbc] to get connection settings regardless of the branch.
 *
 * @param dbSuffix appended to the database name of `TEST_DB_URL`, e.g. a separate database for a migration test
 * @param env source of `ENV_REMOTE` (the system property still wins) and `TEST_DB_*`
 */
@JvmOverloads
fun postgres(
    image: DockerImageName = PostgreSqlContainerSystem.DEFAULT_IMAGE,
    dbSuffix: String = "",
    env: (String) -> String? = System::getenv
): PostgreSqlSystem = if (envRemoteOn(System::getProperty, env)) {
    resettablePostgres(remotePostgresProperties(env, dbSuffix))
} else {
    PostgreSqlContainerSystem(image)
}

internal fun remotePostgresProperties(env: (String) -> String?, dbSuffix: String): Map<String, String> {
    val url = checkNotNull(env(TEST_DB_URL)) {
        "ENV_REMOTE=true, but $TEST_DB_URL is not set: set $TEST_DB_URL=jdbc:postgresql://<host>:<port>/<own database>" +
            " (the database is dropped and created again on start) or unset ENV_REMOTE to use Testcontainers"
    }
    return mapOf(
        PROP_URL to url.withDbSuffix(dbSuffix),
        PROP_USER to (env(TEST_DB_USER) ?: "test"),
        PROP_PASSWORD to (env(TEST_DB_PASSWORD) ?: "test"),
        PROP_DRIVER to POSTGRESQL_DRIVER
    )
}

private fun String.withDbSuffix(suffix: String): String {
    val path = substringBefore('?')
    return path + suffix + substring(path.length)
}
