package io.github.adven27.env.mq.ibmmq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants.WMQ_CM_CLIENT
import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds
import javax.jms.Session
import javax.jms.Session.AUTO_ACKNOWLEDGE

@Suppress("unused", "MagicNumber")
open class IbmMQContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    private val defaultPort: Int = PORT,
    private val defaultPortAdm: Int = PORT_ADM,
    private val afterStart: IbmMQContainerSystem.() -> Unit = { },
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {
    override lateinit var config: IbmMqConfig

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: IbmMQContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart,
    )

    override fun start(fixedEnv: Boolean) {
        withEnv("MQ_QMGR_NAME", "QM1")
        withEnv("LICENSE", "accept")
        withExposedPorts(PORT, PORT_ADM)
        waitingFor(
            forLogMessage(".*The queue manager task 'AUTOCONFIG' has ended.*", 1).withStartupTimeout(ofSeconds(120)),
        )
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("IBMMQ"))
        if (fixedEnv) {
            addFixedExposedPort(defaultPort, PORT)
            addFixedExposedPort(defaultPortAdm, PORT_ADM)
        }
        start()
    }

    override fun start() {
        super.start()
        config = IbmMqConfig(port = getMappedPort(PORT))
        apply(afterStart)
    }

    override fun running() = isRunning

    companion object : KLogging() {
        private const val PORT = 1414
        private const val PORT_ADM = 9443

        @JvmField
        val DEFAULT_IMAGE = "ibmcom/mq:latest".parseImage()
    }
}

/**
 * Remote mq system representation. On init creates 2 temporary queues.
 * Removing of this queues is a responsibility of the remote queue broker.
 */
@Suppress("unused")
data class RemoteMqWithTemporaryQueues(private val connectionFactory: MQConnectionFactory) {
    val config: IbmMqConfig

    constructor(host: String, port: Int, manager: String, channel: String) : this(
        MQConnectionFactory().apply {
            this.transportType = WMQ_CM_CLIENT
            this.hostName = host
            this.port = port
            this.queueManager = manager
            this.channel = channel
            this.temporaryModel = "SYSTEM.JMS.TEMPQ.MODEL"
        },
    )

    init {
        config = connectionFactory.createConnection().createSession(false, AUTO_ACKNOWLEDGE).let { session ->
            IbmMqConfig(
                host = connectionFactory.hostName,
                port = connectionFactory.port,
                manager = connectionFactory.queueManager,
                channel = connectionFactory.channel,
                devQueue1 = session.tempQueue(),
                devQueue2 = session.tempQueue(),
                devQueue3 = session.tempQueue(),
            )
        }
    }

    private fun Session.tempQueue() = this.createTemporaryQueue().queueName
}

/**
 * Config based on default IBM MQ container configuration.
 * @see <a href="http://github.com/ibm-messaging/mq-container/blob/master/docs/developer-config.md">http://github.com/ibm-messaging</a>
 */
data class IbmMqConfig @JvmOverloads constructor(
    val host: String = "localhost",
    val port: Int = 1414,
    val manager: String = "QM1",
    val channel: String = "DEV.APP.SVRCONN",
    val devQueue1: String = "DEV.QUEUE.1",
    val devQueue2: String = "DEV.QUEUE.2",
    val devQueue3: String = "DEV.QUEUE.3",
) : ExternalSystemConfig(
    PROP_HOST to host,
    PROP_PORT to port.toString(),
    PROP_MANAGER to manager,
    PROP_CHANNEL to channel,
    PROP_DEV_Q1 to devQueue1,
    PROP_DEV_Q2 to devQueue2,
    PROP_DEV_Q3 to devQueue3,
) {
    @Suppress("unused")
    val jmsTester1 = jmsConfig(devQueue1)

    @Suppress("unused")
    val jmsTester2 = jmsConfig(devQueue2)

    @Suppress("unused")
    val jmsTester3 = jmsConfig(devQueue3)

    private fun jmsConfig(q: String) = Config(host, port.toInt(), q, manager, channel)

    data class Config(val host: String, val port: Int, val queue: String, val manager: String, val channel: String)

    companion object : KLogging() {
        const val PROP_HOST = "env.mq.ibm.host"
        const val PROP_PORT = "env.mq.ibm.port"
        const val PROP_MANAGER = "env.mq.ibm.manager"
        const val PROP_CHANNEL = "env.mq.ibm.channel"
        const val PROP_DEV_Q1 = "env.mq.ibm.devQueue1"
        const val PROP_DEV_Q2 = "env.mq.ibm.devQueue2"
        const val PROP_DEV_Q3 = "env.mq.ibm.devQueue3"
    }
}
