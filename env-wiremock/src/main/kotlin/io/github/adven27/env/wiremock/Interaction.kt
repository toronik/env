package io.github.adven27.env.wiremock

import java.time.LocalDateTime

data class Interaction(
    val method: String,
    val url: String,
    val req: String,
    val reqHeaders: Map<String, String> = emptyMap(),
    val resp: String,
    val respHeaders: Map<String, String> = emptyMap(),
    val date: LocalDateTime,
    val status: Int
)
