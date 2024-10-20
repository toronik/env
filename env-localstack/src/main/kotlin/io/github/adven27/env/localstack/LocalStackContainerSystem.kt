package io.github.adven27.env.localstack

import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import io.github.adven27.env.localstack.LocalStackContainerSystem.Config.Companion.PREFIX
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.testcontainers.containers.BindMode.READ_ONLY
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.S3
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.utility.DockerImageName

private val logger = logger {}

@Suppress("unused")
open class LocalStackContainerSystem @JvmOverloads constructor(
    private val services: Set<Service> = setOf(),
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = DEFAULT_PORT,
    private val startServices: LocalStackContainerSystem.(Set<Service>) -> Map<String, String> = { s ->
        s.fold(mapOf()) { props, service ->
            props + when (service) {
                S3 -> "test".let {
                    execInContainer(AWSLOCAL, S3.localStackName, "mb", "s3://$it")
                    execInContainer(
                        AWSLOCAL, S3.localStackName, "cp", S3_DIR, "s3://$it", "--recursive", "--include", "'*'"
                    )
                    mapOf("$PREFIX${S3.localStackName}.bucket" to it)
                }

                SQS -> "test".let {
                    execInContainer(AWSLOCAL, SQS.localStackName, "create-queue", "--queue-name", it)
                    mapOf("$PREFIX${SQS.localStackName}.queue" to it)
                }

                else -> mapOf()
            }
        }
    },
    private val afterStart: LocalStackContainerSystem.() -> Unit = { }
) : LocalStackContainer(dockerImageName), ExternalSystem {
    override lateinit var config: Config

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: LocalStackContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    constructor(vararg service: Service) : this(services = service.toSet())

    override fun start(fixedEnv: Boolean) {
        if (fixedEnv) addFixedExposedPort(defaultPort, DEFAULT_PORT)
        start()
    }

    override fun start() {
        withServices(*services.toTypedArray())
        withClasspathResourceMapping("s3", S3_DIR, READ_ONLY)
        super.start()
        config = Config(
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            endpoint = endpoint.toString(),
            props = services
                .flatMap {
                    listOf(
                        "$PREFIX${it.localStackName}.endpoint" to getEndpointOverride(it).toString(),
                        "$PREFIX${it.localStackName}.port" to getEndpointOverride(it).port.toString()
                    )
                }
                .toMap() + startServices(services)
        )
        apply(afterStart)
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val accessKey: String = "test",
        val secretKey: String = "test",
        val region: String = "us-east-1",
        val endpoint: String = "http://localhost:$DEFAULT_PORT",
        val props: Map<String, String> = mapOf()
    ) : ExternalSystemConfig(
        mapOf(
            PROP_ACCESS_KEY to accessKey,
            PROP_SECRET_KEY to secretKey,
            PROP_REGION to region,
            PROP_ENDPOINT to endpoint
        ) + props
    ) {
        companion object {
            const val PREFIX = "env.aws."
            const val PROP_ACCESS_KEY = "${PREFIX}access-key"
            const val PROP_SECRET_KEY = "${PREFIX}secret-key"
            const val PROP_REGION = "${PREFIX}region"
            const val PROP_ENDPOINT = "${PREFIX}endpoint"
        }
    }

    companion object {
        @JvmField
        val DEFAULT_IMAGE = "localstack/localstack".parseImage()
        const val DEFAULT_PORT = 27017
        public const val AWSLOCAL = "awslocal"
        private const val S3_DIR = "/home/test/s3"
    }
}
