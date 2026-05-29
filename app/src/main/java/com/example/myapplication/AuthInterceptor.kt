package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class AuthInterceptor(
    private val tokenProvider: () -> String? = { TokenHolder.get() },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        if (request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val token = tokenProvider() ?: return chain.proceed(request)
        val authed = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}

class UnauthorizedAuthenticator(
    private val appContext: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        scope.launch { UserSession(appContext).clear() }
        return null
    }
}
