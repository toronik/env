package io.github.adven27.env.mq.ibmmq

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.EnvironmentStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IbmMQContainerSystemTest {
    private val sut = SomeEnvironment()

    @Test
    fun fixedEnvironment() {
        System.setProperty(EnvironmentStrategy.SystemPropertyToggle.ENV_FIXED, "true")
        System.setProperty(Environment.ConfigResolver.FromSystemProperty.ENV_UP_TIMEOUT_SEC, "500")

        sut.up()

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
        assertEquals(IbmMqConfig(), sut.ibmMq().config)
        assertEquals("1414", System.getProperty("env.mq.ibm.port"))
    }

    @Test
    fun dynamicEnvironment() {
        System.setProperty(Environment.ConfigResolver.FromSystemProperty.ENV_UP_TIMEOUT_SEC, "500")

        sut.up()

        sut.systems.forEach { (_, s) -> assertTrue(s.running()) }
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class SomeEnvironment : Environment("IBMMQ" to IbmMQContainerSystem()) {
    fun ibmMq() = env<IbmMQContainerSystem>()
}
