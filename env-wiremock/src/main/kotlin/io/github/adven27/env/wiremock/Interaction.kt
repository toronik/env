package io.github.adven27.env.wiremock

import java.time.LocalDateTime

data class Interaction(
    val method: String,
    val url: String,
    val req: String,
    val resp: String,
    val date: LocalDateTime,
    val status: Int
)
