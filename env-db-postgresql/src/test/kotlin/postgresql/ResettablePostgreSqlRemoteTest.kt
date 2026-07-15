package postgresql

import io.github.adven27.env.db.postgresql.resettablePostgres
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class ResettablePostgreSqlRemoteTest {

    private val postgres = PostgreSQLContainer<Nothing>("postgres:15")

    @Before
    fun startContainer() {
        postgres.start()
        // create the test db and seed it with a junk table
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE DATABASE roomtest_x")
            }
        }
        val roomUrl = postgres.jdbcUrl.replace("/test", "/roomtest_x")
        DriverManager.getConnection(roomUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE junk(id int)")
                st.execute("INSERT INTO junk VALUES (1)")
            }
        }
    }

    @After
    fun stopContainer() {
        System.clearProperty("SPECS_SUT_START")
        postgres.stop()
    }

    @Test
    fun `start with reset gate unset drops and recreates the database`() {
        System.clearProperty("SPECS_SUT_START") // gate absent → shouldReset = true

        val host = postgres.host
        val port = postgres.firstMappedPort
        val username = postgres.username
        val password = postgres.password

        val props = mapOf(
            "env.db.postgresql.url" to "jdbc:postgresql://$host:$port/roomtest_x",
            "env.db.postgresql.username" to username,
            "env.db.postgresql.password" to password,
            "env.db.postgresql.driver" to "org.postgresql.Driver"
        )

        val system = resettablePostgres(props)
        system.start(false)

        assertTrue(system.running())

        // DB should exist (recreated) but junk table should be gone
        val roomUrl = "jdbc:postgresql://$host:$port/roomtest_x"
        DriverManager.getConnection(roomUrl, username, password).use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'junk'"
                )
                rs.next()
                assertEquals("junk table should be gone after reset", 0, rs.getInt(1))
            }
        }
    }

    @Test
    fun `start with SPECS_SUT_START=false is connect-only and data survives`() {
        System.setProperty("SPECS_SUT_START", "false") // connect-only mode

        val host = postgres.host
        val port = postgres.firstMappedPort
        val username = postgres.username
        val password = postgres.password

        val props = mapOf(
            "env.db.postgresql.url" to "jdbc:postgresql://$host:$port/roomtest_x",
            "env.db.postgresql.username" to username,
            "env.db.postgresql.password" to password,
            "env.db.postgresql.driver" to "org.postgresql.Driver"
        )

        val system = resettablePostgres(props)
        system.start(false)

        assertTrue(system.running())

        // Data should survive (no reset performed)
        val roomUrl = "jdbc:postgresql://$host:$port/roomtest_x"
        DriverManager.getConnection(roomUrl, username, password).use { conn ->
            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT COUNT(*) FROM junk")
                rs.next()
                assertEquals("data should survive in connect-only mode", 1, rs.getInt(1))
            }
        }
    }
}
