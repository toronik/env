package io.github.adven27.env.mq.ibmmq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.wmq.WMQConstants.WMQ_CM_CLIENT
import io.github.adven27.env.container.parseImage
import io.github.adven27.env.core.Environment.Companion.setProperties
import io.github.adven27.env.core.Environment.Prop
import io.github.adven27.env.core.Environment.Prop.Companion.set
import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.PortsExposingStrategy
import io.github.adven27.env.core.PortsExposingStrategy.SystemPropertyToggle
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_CHANNEL
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_DEV_Q1
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_DEV_Q2
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_DEV_Q3
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_HOST
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_MANAGER
import io.github.adven27.env.mq.ibmmq.IbmMqConfig.Companion.PROP_PORT
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait.forLogMessage
import org.testcontainers.utility.DockerImageName
import java.time.Duration.ofSeconds
import javax.jms.Session
import javax.jms.Session.AUTO_ACKNOWLEDGE

@Suppress("unused", "MagicNumber")
class IbmMQContainerSystem @JvmOverloads constructor(
    dockerImageName: DockerImageName = DEFAULT_IMAGE,
    portsExposingStrategy: PortsExposingStrategy = SystemPropertyToggle(),
    fixedPort: Int = PORT,
    fixedPortAdm: Int = PORT_ADM,
    private var config: IbmMqConfig = IbmMqConfig(),
    private val afterStart: IbmMQContainerSystem.() -> Unit = { },
) : GenericContainer<Nothing>(dockerImageName), ExternalSystem {

    @JvmOverloads
    constructor(imageName: DockerImageName = DEFAULT_IMAGE, afterStart: IbmMQContainerSystem.() -> Unit) : this(
        dockerImageName = imageName,
        afterStart = afterStart
    )

    init {
        withEnv("MQ_QMGR_NAME", "QM1")
        withEnv("LICENSE", "accept")
        withExposedPorts(PORT, PORT_ADM)
        waitingFor(
            forLogMessage(".*The queue manager task 'AUTOCONFIG' has ended.*", 1).withStartupTimeout(ofSeconds(120))
        )
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("IBMMQ"))
        if (portsExposingStrategy.fixedPorts()) {
            addFixedExposedPort(fixedPort, PORT)
            addFixedExposedPort(fixedPortAdm, PORT_ADM)
        }
    }

    override fun start() {
        super.start()
        config = IbmMqConfig(port = config.port.name set getMappedPort(PORT).toString())
        apply(afterStart)
    }

    override fun running() = isRunning

    fun config() = config

    override fun describe() = super.describe() + "\n\t" + config.asMap().entries.joinToString("\n\t") { it.toString() }

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
        }
    )

    init {
        config = connectionFactory.createConnection().createSession(false, AUTO_ACKNOWLEDGE).let { session ->
            IbmMqConfig(
                host = PROP_HOST set connectionFactory.hostName,
                port = PROP_PORT set connectionFactory.port.toString(),
                manager = PROP_MANAGER set connectionFactory.queueManager,
                channel = PROP_CHANNEL set connectionFactory.channel,
                devQueue1 = PROP_DEV_Q1 set session.tempQueue(),
                devQueue2 = PROP_DEV_Q2 set session.tempQueue(),
                devQueue3 = PROP_DEV_Q3 set session.tempQueue(),
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
    val host: Prop = PROP_HOST set "localhost",
    val port: Prop = PROP_PORT set "1414",
    val manager: Prop = PROP_MANAGER set "QM1",
    val channel: Prop = PROP_CHANNEL set "DEV.APP.SVRCONN",
    val devQueue1: Prop = PROP_DEV_Q1 set "DEV.QUEUE.1",
    val devQueue2: Prop = PROP_DEV_Q2 set "DEV.QUEUE.2",
    val devQueue3: Prop = PROP_DEV_Q3 set "DEV.QUEUE.3",
) {
    @Suppress("unused")
    val jmsTester1 = jmsConfig(devQueue1.value)

    @Suppress("unused")
    val jmsTester2 = jmsConfig(devQueue2.value)

    @Suppress("unused")
    val jmsTester3 = jmsConfig(devQueue3.value)

    private fun jmsConfig(q: String) = Config(host.value, port.value.toInt(), q, manager.value, channel.value)

    constructor(
        host: String = "localhost",
        port: Int = 1414,
        manager: String = "QM1",
        channel: String = "DEV.APP.SVRCONN"
    ) : this(
        host = PROP_HOST set host,
        port = PROP_PORT set port.toString(),
        manager = PROP_MANAGER set manager,
        channel = PROP_CHANNEL set channel,
    )

    init {
        asMap().setProperties()
    }

    fun asMap(): Map<String, String> = mapOf(
        host.pair(),
        port.pair(),
        manager.pair(),
        channel.pair(),
        devQueue1.pair(),
        devQueue2.pair(),
        devQueue3.pair()
    )

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
