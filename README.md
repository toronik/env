![CI](https://github.com/Adven27/env/workflows/CI/badge.svg)

# env

Java library for a microservice environment emulation.

Idea is to be agnostic to tools used for specific external system emulation (e.g. docker, remote server, java standalone app or process). It enables to build up mixed environment.

### How to use

1. Add needed dependencies:

```groovy
testImplementation "io.github.adven27:env-db-postgresql:<version>"
testImplementation "io.github.adven27:env-db-mssql:<version>"
testImplementation "io.github.adven27:env-db-mysql:<version>"
testImplementation "io.github.adven27:env-db-oracle:<version>"
testImplementation "io.github.adven27:env-db-oracle-temp:<version>"
testImplementation "io.github.adven27:env-db-db2:<version>"
testImplementation "io.github.adven27:env-jar-application:<version>"
testImplementation "io.github.adven27:env-mq-rabbit:<version>"
testImplementation "io.github.adven27:env-mq-ibmmq:<version>"
testImplementation "io.github.adven27:env-grpc-mock:<version>"
testImplementation "io.github.adven27:env-redis:<version>"
testImplementation "io.github.adven27:env-wiremock:<version>"
```

2. Set up systems:

```kotlin
class SomeEnvironment : Environment(
    "KAFKA" to EmbeddedKafkaSystem("my.topic"),
    "POSTGRES" to PostgreSqlContainerSystem(),
    "EXTERNAL_API" to WiremockSystem()
) {
    fun kafka() = env<EmbeddedKafkaSystem>().config.bootstrapServers
    fun api() = env<WiremockSystem>().client
}
```

3. Use in tests:

```kotlin
class MyTestClass {
    companion object {
        private val ENV: SomeEnvironment = SomeEnvironment()

        @BeforeClass @JvmStatic
        fun setup() {
           ENV.up()
        }

        @AfterClass @JvmStatic
        fun teardown() {
           ENV.down()
        }
    }

    @Test fun testSomething() {
        //some interactions with environment
        ENV.api().resetRequests()
        //some test
        ...
    }
}
```

### Run as standalone process

Environment class implementation could be run as standalone java application with `io.github.adven27.env.core.EnvStarter`

For example as gradle task:

```groovy
task runEnv(type: JavaExec) {
    group = "Execution"
    description = "Run some environment"
    classpath = sourceSets.test.runtimeClasspath
    main = "io.github.adven27.env.core.EnvStarter"

    args 'SomeEnvironment'
    systemProperty 'SPECS_ENV_START', true
    systemProperty 'SPECS_ENV_FIXED', true
    standardInput = System.in
}
```

## Examples

For more info see [demo project](https://github.com/Adven27/service-tests/blob/master/demo/src/test/kotlin/specs/Specs.kt#L51)

