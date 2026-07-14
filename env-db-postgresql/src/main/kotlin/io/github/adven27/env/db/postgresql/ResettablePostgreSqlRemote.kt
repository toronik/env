package io.github.adven27.env.db.postgresql

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import java.sql.DriverManager

@Suppress("unused")
fun resettablePostgres(props: Map<String, String>): ExternalSystem = ResettablePostgreSqlRemote(props)

private fun shouldReset() = System.getProperty("SPECS_SUT_START")?.toBoolean() != false

private class ResettablePostgreSqlRemote(private val props: Map<String, String>) : ExternalSystem {
    override val config: ExternalSystemConfig = ExternalSystemConfig(props)

    override fun start(fixedEnv: Boolean) {
        props.forEach(System::setProperty)
        if (!shouldReset()) return

        val url = props.getValue("env.db.postgresql.url")
        val username = props.getValue("env.db.postgresql.username")
        val password = props.getValue("env.db.postgresql.password")

        val dbName = extractDbName(url)
        require(dbName.matches(Regex("[a-zA-Z0-9_]+"))) {
            "Invalid database name '$dbName': must match ^[a-zA-Z0-9_]+\$"
        }

        val maintenanceUrl = buildMaintenanceUrl(url)
        DriverManager.getConnection(maintenanceUrl, username, password).use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute("DROP DATABASE IF EXISTS $dbName WITH (FORCE)")
                st.execute("CREATE DATABASE $dbName")
            }
        }
    }

    override fun stop() = Unit

    override fun running() = true

    private fun extractDbName(url: String): String {
        // url like jdbc:postgresql://host:port/dbname?params
        val path = url.removePrefix("jdbc:postgresql://")
        val afterHostPort = path.substringAfter("/")
        return afterHostPort.substringBefore("?").substringBefore("&").trim()
    }

    private fun buildMaintenanceUrl(url: String): String {
        // Replace the db name in the path with "postgres"
        val prefix = "jdbc:postgresql://"
        val withoutScheme = url.removePrefix(prefix)
        val hostPort = withoutScheme.substringBefore("/")
        val afterPath = withoutScheme.substringAfter("/")
        val queryPart = if (afterPath.contains("?")) "?" + afterPath.substringAfter("?") else ""
        return "$prefix$hostPort/postgres$queryPart"
    }
}
