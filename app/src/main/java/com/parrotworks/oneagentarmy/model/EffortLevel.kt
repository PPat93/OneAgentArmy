package com.parrotworks.oneagentarmy.model

// Reasoning/thinking depth, requested only on models whose AiModelOption.supportsEffort
// is true (OpenAI's reasoning-capable tier, Anthropic's Sonnet 5 / Opus 5). Null means
// "no override" - the provider's own default is used, which is exactly today's behavior.
enum class EffortLevel { LOW, MEDIUM, HIGH }
