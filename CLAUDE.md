# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`env` is a Kotlin/Java library for emulating a microservice's external environment in tests (DBs, message queues, mocks, etc.). Published to Maven Central under `io.github.adven27`. The core abstraction is deliberately agnostic to *how* a system is emulated — Testcontainers/Docker, an embedded process, or a remote server — so a single `Environment` can mix them.

## Build & test

Gradle multi-module build. Use the wrapper.

```bash
./gradlew build                         # compile + test + lint (detekt + ktlint) all modules
./gradlew build --parallel              # what CI runs
./gradlew :env-db-postgresql:test       # test one module
./gradlew :example:test --tests "EnvTest.fixedEnvironment"   # single test
./gradlew ktlintFormat                  # auto-fix Kotlin style across a module's src
./gradlew detekt                        # static analysis only
```

Most modules' tests start real Docker containers via Testcontainers, so **a running Docker daemon is required** to run them. JDK 21 toolchain.

## Architecture

Everything hangs off two interfaces in `env-core` (`io.github.adven27.env.core`):

- **`ExternalSystem`** — one emulated system. Lifecycle: `start(fixedEnv)` / `stop()` / `running()`, plus a `config: ExternalSystemConfig`. Constructing an `ExternalSystemConfig` **propagates its properties to JVM system properties** as a side effect (see `ExternalSystemConfig.init`) — that's how the system under test discovers connection details (e.g. `env.db.postgresql.url`).
- **`Environment`** — an ordered map of `name -> ExternalSystem`. `up()` starts all systems concurrently (cached thread pool, one thread per system) with a timeout, rolling back via `down()` on any failure. Look up a started system with `env<RabbitContainerSystem>()` (by type) or `env<T>("NAME")` (by key).

Two implementation strategies for `ExternalSystem`, both in their own modules:
- **`GenericExternalSystem<T, C>`** (`env-core`) — wraps any object with `start`/`stop`/`running`/`afterStart` lambdas. Base for non-container systems.
- **`ContainerExternalSystem`** (`env-container`) — wraps a Testcontainers `GenericContainer`. Most `env-db-*` / `env-mq-*` modules build on this. Note: some systems (e.g. `PostgreSqlContainerSystem`) instead *extend* the Testcontainers class directly and implement `ExternalSystem`.

### The `fixedEnv` flag (important)

`start(fixedEnv: Boolean)` is the central concept for **fixed vs. dynamic** ports:
- `fixedEnv = true` → bind well-known fixed ports (e.g. Postgres on 5432). Used for stable local dev where the SUT has hardcoded connection config.
- `fixedEnv = false` → let the system pick random free ports. Used in CI / parallel test runs to avoid collisions.

The value comes from `Environment.Config.envStrategy` — default `SystemPropertyToggle` reads the `ENV_FIXED` system property (default `false`). See `env/example/.../EnvTest.kt` for both modes asserted.

### Config via system properties

`Environment.Config` is resolved from system properties by default (`ConfigResolver.FromSystemProperty`): `ENV_FIXED`, `ENV_DRY_RUN`, `ENV_UP_TIMEOUT_SEC` (300), `ENV_DOWN_TIMEOUT_SEC` (10).

### Standalone runner

`EnvStarter` (`env-core`) runs any `Environment` subclass as a standalone process (interactive REPL: `s`tatus / `u`p / `d`own / `e`xit), for local dev outside a test run. Invoked via `JavaExec` with the env class name as arg (see README).

## Module layout

- `env-core` — interfaces, `Environment`, `EnvStarter`, port utils. No Testcontainers dependency.
- `env-container` — Testcontainers base (`ContainerExternalSystem`, `String.parseImage()` helpers).
- `env-db-*`, `env-mq-*`, `env-redis`, `env-wiremock`, `env-grpc-mock`, `env-selenium`, `env-localstack`, `env-samba`, `env-jar-application` — one system implementation each. Add a new system by creating a `env-<kind>` module (register it in `settings.gradle`) and depending on `env-container` or `env-core`.
- `example` — runnable usage reference (`EnvTest.kt`); not published.

## Conventions

- **Adding a system**: also add it to `build.gradle`'s `publishMaven` task (the `dependsOn` list) and to `settings.gradle`.
- Config property keys are namespaced `env.<kind>.<system>.<field>` (e.g. `env.mq.rabbit.port`). Define them as constants on the `Config` companion.
- Public API targets Java interop (`@JvmOverloads`, `@JvmStatic`, `@JvmField`); keep that when touching constructors/companions.
- Version lives in one place: `ext.libVersion` in root `build.gradle`. Release commits are just the bumped version number (see git log); publishing is automated on GitHub Release via the `gradle-publish.yml` workflow.
- Style: ktlint + detekt (config in `config/detekt/detekt.yml`), 4-space indent, no trailing commas (`.editorconfig`).
