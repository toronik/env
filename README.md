![CI](https://github.com/Adven27/env/workflows/CI/badge.svg)

# env

Java library for a microservice environment emulation

### How to use

1. Add needed dependencies:

```groovy
testImplementation "io.github.adven27:env-db-postgresql:3.0.0"
testImplementation "io.github.adven27:env-db-mysql:3.0.0"
testImplementation "io.github.adven27:env-db-oracle:3.0.0"
testImplementation "io.github.adven27:env-db-db2:3.0.0"
testImplementation "io.github.adven27:env-mq-rabbit:3.0.0"
testImplementation "io.github.adven27:env-mq-ibmmq:3.0.0"
testImplementation "io.github.adven27:env-mq-redis:3.0.0"
testImplementation "io.github.adven27:env-grpc-mock:3.0.0"
testImplementation "io.github.adven27:env-wiremock:3.0.0"
```

2. Set up systems:

```kotlin
class SomeEnvironment : Environment(
    "RABBIT" to RabbitContainerSystem(),
    "IBMMQ" to IbmMQContainerSystem(),
    "KAFKA" to KafkaContainerSystem(),
    "REDIS" to RedisContainerSystem(),
    "POSTGRES" to PostgreSqlContainerSystem(),
    "ORACLE" to OracleContainerSystem(),
    "MYSQL" to MySqlContainerSystem(),
    "GRPC" to GrpcMockContainerSystem(1, listOf("common.proto", "wallet.proto")).apply {
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("GRPC-$serviceId"))
    },
    "WIREMOCK" to WiremockSystem()
) {
    fun rabbit() = find<RabbitContainerSystem>("RABBIT")
    fun mock() = find<WiremockSystem>("WIREMOCK").server
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
        ENV.mock().resetRequests()
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

