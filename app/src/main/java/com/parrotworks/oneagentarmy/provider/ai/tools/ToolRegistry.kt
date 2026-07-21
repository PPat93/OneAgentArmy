package com.parrotworks.oneagentarmy.provider.ai.tools

// Adding a new agent skill = registering its ToolDefinition here (see AppContainer)
// plus handling its name in ChatViewModel's dispatch.
class ToolRegistry(
    val definitions: List<ToolDefinition>,
)
