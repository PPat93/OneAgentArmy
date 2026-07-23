package com.parrotworks.oneagentarmy.provider.ai

sealed class AiProviderException(message: String) : Exception(message) {
    data object MissingApiKey : AiProviderException("No API key configured")
    data class InvalidApiKey(val detail: String?) : AiProviderException("API key was rejected")
    data class NoConnectivity(val detail: String?) : AiProviderException("No network connectivity")
    data class Timeout(val detail: String?) : AiProviderException("Request timed out")
    data class RateLimited(val retryAfterSeconds: Int?, val detail: String?) : AiProviderException("Rate limited")
    data class ServerError(val statusCode: Int, val detail: String?) : AiProviderException("Server error: $statusCode")
    data class Unknown(val detail: String) : AiProviderException(detail)
}
