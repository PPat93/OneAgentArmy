package com.piotrek.oneagentarmy.provider.ai.openai

data class OpenAiModelOption(val id: String, val label: String)

val OPENAI_MODEL_OPTIONS = listOf(
    OpenAiModelOption("gpt-4.1-nano", "GPT-4.1 nano - najtańszy (\$0.10 / \$0.40 za 1M tokenów)"),
    OpenAiModelOption("gpt-5.6-luna", "GPT-5.6 Luna - lepsza jakość (\$1.00 / \$6.00 za 1M tokenów)"),
)

const val DEFAULT_OPENAI_MODEL = "gpt-4.1-nano"
