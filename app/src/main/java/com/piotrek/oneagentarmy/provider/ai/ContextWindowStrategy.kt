package com.piotrek.oneagentarmy.provider.ai

import com.piotrek.oneagentarmy.model.Message

fun interface ContextWindowStrategy {
    fun apply(history: List<Message>): List<Message>
}

object ContextWindowStrategies {
    val FullHistory = ContextWindowStrategy { it }

    fun lastN(n: Int) = ContextWindowStrategy { history ->
        if (history.size <= n) history else history.takeLast(n)
    }
}
