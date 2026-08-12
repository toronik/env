package postgresql

import io.github.adven27.env.core.Environment
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.DriverManager

class InitScriptTest {
    private val sut = InitScriptEnvironment()

    @Test
    fun initScriptIsAppliedOnStartup() {
        sut.up()

        with(sut.postgres()) {
            DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                connection.createStatement().executeQuery("SELECT count(*) FROM probe").use {
                    it.next()
                    assertEquals(1, it.getInt(1))
                }
            }
        }
    }

    @After
    fun tearDown() {
        sut.down()
    }
}

class InitScriptEnvironment : Environment(
    "POSTGRES" to PostgreSqlContainerSystem().apply { withInitScript("init-probe.sql") }
) {
    fun postgres() = env<PostgreSqlContainerSystem>()
}
