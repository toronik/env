package io.github.adven27.env.wiremock

import io.github.adven27.env.core.Environment
import io.github.adven27.env.core.Environment.Companion.property
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.PROP_HOST
import io.github.adven27.env.wiremock.WiremockSystem.Config.Companion.PROP_PORT
import io.restassured.RestAssured
import io.restassured.RestAssured.get
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Test

class WiremockSystemTest {
    companion object {
        private fun up(sut: WiremockSystem) = object : Environment("WIREMOCK" to sut) {
            init {
                up()
            }
        }
    }

    @Test
    fun default() {
        up(WiremockSystem()).also {
            RestAssured.baseURI = "http://${PROP_HOST.property()}"
            RestAssured.port = PROP_PORT.property().toInt()
        }

        get("/test1").then().body("test", equalTo(1))
        get("/test2").then().body("test", equalTo(2))
    }
}
