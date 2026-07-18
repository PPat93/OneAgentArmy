package com.piotrek.oneagentarmy.model

import java.time.Instant

data class Fact(
    val id: String,
    val title: String,
    val content: String,
    val isGlobal: Boolean,
    val createdAt: Instant,
)
