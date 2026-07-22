package com.parrotworks.oneagentarmy.provider.ai

import com.parrotworks.oneagentarmy.model.Message

fun interface ContextWindowStrategy {
    fun apply(history: List<Message>): List<Message>
}

object ContextWindowStrategies {
    fun lastN(n: Int) = ContextWindowStrategy { history ->
        if (history.size <= n) history else history.takeLast(n)
    }
}
