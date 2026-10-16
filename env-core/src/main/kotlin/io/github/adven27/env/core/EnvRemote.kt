package io.github.adven27.env.core

/**
 * Toggle for running against an already running test stack (e.g. sidecars of a shared dev environment)
 * instead of starting containers. The `ENV_REMOTE` system property wins over the environment variable of the same name.
 *
 * Factories like `postgres()` (env-db-postgresql) and `redis()` (env-redis) pick their branch by this toggle.
 */
const val ENV_REMOTE = "ENV_REMOTE"

fun envRemoteOn(): Boolean = envRemoteOn(System::getProperty, System::getenv)

/** Same as [envRemoteOn] with explicit sources of the system property and the environment variable. */
fun envRemoteOn(property: (String) -> String?, env: (String) -> String?): Boolean =
    (property(ENV_REMOTE) ?: env(ENV_REMOTE)).toBoolean()
