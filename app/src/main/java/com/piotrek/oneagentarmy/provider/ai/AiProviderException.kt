package com.piotrek.oneagentarmy.provider.ai

sealed class AiProviderException(message: String) : Exception(message) {
    data object MissingApiKey : AiProviderException("No API key configured")
    data class InvalidApiKey(val detail: String?) : AiProviderException("API key was rejected")
    data object NoConnectivity : AiProviderException("No network connectivity")
    data class RateLimited(val retryAfterSeconds: Int?, val detail: String?) : AiProviderException("Rate limited")
    data class ServerError(val statusCode: Int, val detail: String?) : AiProviderException("Server error: $statusCode")
    data class Unknown(val detail: String) : AiProviderException(detail)
}
