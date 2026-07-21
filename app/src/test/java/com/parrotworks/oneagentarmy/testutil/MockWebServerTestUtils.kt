package com.parrotworks.oneagentarmy.testutil

import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

// The *ApiClient classes hardcode their provider's real URL - there's no seam to inject a
// different base URL for tests. Instead of touching production code just to make it
// testable, this interceptor rewrites every outgoing request's scheme/host/port to the
// MockWebServer's before it hits the network, leaving the path/method/headers/body untouched.
fun redirectingClient(mockWebServer: MockWebServer): OkHttpClient {
    val mockUrl = mockWebServer.url("/")
    return OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val redirected = original.newBuilder()
                .url(
                    original.url.newBuilder()
                        .scheme(mockUrl.scheme)
                        .host(mockUrl.host)
                        .port(mockUrl.port)
                        .build(),
                )
                .build()
            chain.proceed(redirected)
        }
        .build()
}
