package io.github.adven27.env.db.oracle

import io.github.adven27.env.core.ExternalSystem
import io.github.adven27.env.core.ExternalSystemConfig
import mu.KLogging
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.net.InetAddress
import java.sql.SQLSyntaxErrorException

@Suppress("TooManyFunctions", "LongParameterList", "unused")
open class OracleTemporarySchemaSystem @JvmOverloads constructor(
    private var sysConfig: Config = Config(),
    private val createSchemaScript: String = "call CREATE_SCHEMA(?, ?)",
    private val dropSchemaScript: String = "call DROP_SCHEMA(?)",
    private var sysInitScriptPath: String? = "classpath:schemaManagementProcedures.sql",
    private var initScriptPath: String? = null,
    private val afterStart: OracleTemporarySchemaSystem.() -> Unit = { }
) : ExternalSystem, AutoCloseable {
    override lateinit var config: Config

    private val jdbcTemplate = JdbcTemplate(
        DriverManagerDataSource(
            sysConfig.url,
            sysConfig.username,
            sysConfig.password
        ).also {
            it.setDriverClassName(sysConfig.driver)
        }
    )

    override fun start(fixedEnv: Boolean) {
        config = Config(
            url = sysConfig.url,
            username = if (fixedEnv) defaultScheme() else dynamicScheme(),
            password = "test"
        )

        if (sysInitScriptPath != null) {
            executeScript(path = sysInitScriptPath!!, delimiter = "/")
        }

        try {
            jdbcTemplate.update(createSchemaScript, config.username, config.password)
        } catch (e: BadSqlGrammarException) {
            if (e.cause is SQLSyntaxErrorException && e.cause?.message?.startsWith("ORA-01920") == true) {
                logger.warn("Scheme already exists. Recreating...", e)
                jdbcTemplate.update(dropSchemaScript, config.username)
                jdbcTemplate.update(createSchemaScript, config.username, config.password)
            }
        }

        apply(afterStart)

        if (initScriptPath != null) {
            executeScript(initScriptPath!!)
        }

        isRunning = true
    }

    override fun stop() {
        try {
            jdbcTemplate.update(dropSchemaScript, config.username)
        } catch (expected: Exception) {
            logger.warn("Failed to close ${config.username} оn the first try", expected)
            Thread.sleep(STOP_RETRY_DELAY)
            try {
                jdbcTemplate.update(dropSchemaScript, config.username)
            } catch (expected: Exception) {
                logger.warn("Failed to close ${config.username} оn the second try", expected)
            }
        }
        isRunning = false
    }

    override fun running() = isRunning

    data class Config @JvmOverloads constructor(
        val url: String = "jdbc:oracle:thin:@host:port:sid",
        val username: String = "test",
        val password: String = "test",
        val driver: String = "oracle.jdbc.OracleDriver"
    ) : ExternalSystemConfig(PROP_URL to url, PROP_USER to username, PROP_PASSWORD to password, PROP_DRIVER to driver) {
        companion object {
            private const val PREFIX = "env.db.oracle."
            const val PROP_URL = "${PREFIX}url"
            const val PROP_USER = "${PREFIX}username"
            const val PROP_PASSWORD = "${PREFIX}password"
            const val PROP_DRIVER = "${PREFIX}driver"
        }
    }

    private var isRunning = false

    @Suppress("FunctionOnlyReturningConstant")
    fun getDriverClassName() = "oracle.jdbc.OracleDriver"

    fun getJdbcUrl() = config.url

    fun getUsername() = config.username
    fun getPassword() = config.password

    override fun close() = stop()

    fun withInitScript(initScriptPath: String): OracleTemporarySchemaSystem {
        this.initScriptPath = initScriptPath
        return this
    }

    fun withSysInitScript(sysInitScriptPath: String): OracleTemporarySchemaSystem {
        this.sysInitScriptPath = sysInitScriptPath
        return this
    }

    private fun executeScript(path: String, delimiter: String = ScriptUtils.DEFAULT_STATEMENT_SEPARATOR) {
        connection().use { conn ->
            ScriptUtils.executeSqlScript(
                conn,
                EncodedResource(DefaultResourceLoader().getResource(path)),
                false,
                false,
                ScriptUtils.DEFAULT_COMMENT_PREFIX,
                delimiter,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER
            )
        }
    }

    private fun connection() = jdbcTemplate.dataSource!!.connection

    companion object : KLogging() {
        const val STOP_RETRY_DELAY: Long = 3000

        fun defaultScheme() = "test_${InetAddress.getLocalHost().hostAddress.replace(".", "_")}"
        fun dynamicScheme() = "test_${System.currentTimeMillis()}"
    }
}
