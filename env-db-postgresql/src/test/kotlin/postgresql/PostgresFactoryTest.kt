package postgresql

import io.github.adven27.env.core.Environment
import io.github.adven27.env.db.postgresql.PostgreSqlContainerSystem
import io.github.adven27.env.db.postgresql.PostgreSqlSystem
import io.github.adven27.env.db.postgresql.postgres
import io.github.adven27.env.db.postgresql.resettablePostgres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.testcontainers.DockerClientFactory
import java.sql.DriverManager
import kotlin.test.assertFailsWith

/**
 * `postgres()` picks its branch by `ENV_REMOTE`. The coordinates tests need no Docker and no server;
 * the live tests run where their stack is: against a running server with `ENV_REMOTE=true` (`TEST_DB_URL`),
 * in a container otherwise (when Docker is available).
 */
class PostgresFactoryTest {
    private val remote = mapOf(
        "ENV_REMOTE" to "true",
        "TEST_DB_URL" to "jdbc:postgresql://test-db:5432/roomtest_x?stringtype=unspecified",
        "TEST_DB_USER" to "u",
        "TEST_DB_PASSWORD" to "p"
    )

    private val local = remote - "ENV_REMOTE"

    @Test
    fun `remote branch takes coordinates from TEST_DB_ and needs no Docker`() {
        assertEquals(
            PostgreSqlContainerSystem.Config("jdbc:postgresql://test-db:5432/roomtest_x?stringtype=unspecified", "u", "p"),
            postgres(env = remote::get).jdbc()
        )
    }

    @Test
    fun `remote user and password default to test`() {
        val jdbc = postgres(env = (remote - "TEST_DB_USER" - "TEST_DB_PASSWORD")::get).jdbc()

        assertEquals("test", jdbc.username)
        assertEquals("test", jdbc.password)
    }

    @Test
    fun `db suffix gives a separate database on the same server, with or without url parameters`() {
        mapOf(
            "jdbc:postgresql://test-db:5432/roomtest_x?stringtype=unspecified" to
                "jdbc:postgresql://test-db:5432/roomtest_x_migration?stringtype=unspecified",
            "jdbc:postgresql://test-db:5432/roomtest_x" to "jdbc:postgresql://test-db:5432/roomtest_x_migration"
        ).forEach { (url, expected) ->
            val system = postgres(dbSuffix = "_migration", env = (remote + ("TEST_DB_URL" to url))::get)

            assertEquals(expected, system.jdbc().jdbcUrl)
        }
    }

    @Test
    fun `remote branch without TEST_DB_URL is refused and names what to set`() {
        val e = assertFailsWith<IllegalStateException> { postgres(env = (remote - "TEST_DB_URL")::get) }

        assertTrue(e.message, "TEST_DB_URL" in e.message!!)
        assertTrue(e.message, "ENV_REMOTE" in e.message!!)
    }

    @Test
    fun `without the toggle it is a container and TEST_DB_ are ignored`() {
        assertTrue(postgres(env = local::get) is PostgreSqlContainerSystem)
        assertTrue(postgres(env = (local + ("ENV_REMOTE" to "false"))::get) is PostgreSqlContainerSystem)
    }

    @Test
    fun `both implementations resolve from an environment as PostgreSqlSystem`() {
        val env = Environment("DB" to resettablePostgres(remotePropsFrom(remote)), "OTHER" to postgres(env = local::get))

        assertEquals("u", env.env<PostgreSqlSystem>("DB").jdbc().username)
        assertTrue(env.env<PostgreSqlSystem>("OTHER") is PostgreSqlContainerSystem)
    }

    @Test
    fun `remote stack - database is recreated and jdbc points at it`() {
        assumeTrue("needs ENV_REMOTE=true and TEST_DB_URL", envRemote() && System.getenv("TEST_DB_URL") != null)

        val system = postgres()
        system.start(false)
        system.useConnection { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE junk(id int)") }
        }

        postgres().also { it.start(false) }.useConnection { connection ->
            connection.createStatement().use { st ->
                st.executeQuery("select current_database(), count(*) from information_schema.tables where table_name = 'junk'")
                    .use { rows ->
                        rows.next()
                        assertTrue(system.jdbc().jdbcUrl, system.jdbc().jdbcUrl.contains("/${rows.getString(1)}"))
                        assertEquals("the database is recreated on start", 0, rows.getInt(2))
                    }
            }
        }
    }

    @Test
    fun `containers - the same call starts Testcontainers`() {
        assumeTrue("needs Docker", !envRemote() && DockerClientFactory.instance().isDockerAvailable)

        val system = postgres()
        system.start(false)
        try {
            system.useConnection { connection ->
                connection.createStatement().use { st -> st.executeQuery("select 1").use { assertTrue(it.next()) } }
            }
        } finally {
            system.stop()
        }
    }

    private fun envRemote() = System.getenv("ENV_REMOTE")?.toBoolean() == true

    private fun PostgreSqlSystem.useConnection(block: (java.sql.Connection) -> Unit) =
        with(jdbc()) { DriverManager.getConnection(jdbcUrl, username, password).use(block) }

    private fun remotePropsFrom(env: Map<String, String>) = mapOf(
        PostgreSqlContainerSystem.Config.PROP_URL to env.getValue("TEST_DB_URL"),
        PostgreSqlContainerSystem.Config.PROP_USER to env.getValue("TEST_DB_USER"),
        PostgreSqlContainerSystem.Config.PROP_PASSWORD to env.getValue("TEST_DB_PASSWORD"),
        PostgreSqlContainerSystem.Config.PROP_DRIVER to "org.postgresql.Driver"
    )
}
