package core

import io.github.adven27.env.core.envRemoteOn
import org.junit.Test
import kotlin.test.assertEquals

class EnvRemoteTest {

    @Test
    fun `system property wins over environment variable`() {
        listOf(
            Triple("true", null, true),
            Triple(null, "true", true),
            Triple("TRUE", "false", true),
            Triple("false", "true", false),
            Triple("false", null, false),
            Triple(null, "false", false),
            Triple(null, "yes", false),
            Triple(null, null, false)
        ).forEach { (property, env, expected) ->
            assertEquals(expected, envRemoteOn({ property }, { env }), "property=$property, env=$env")
        }
    }
}
