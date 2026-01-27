package com.realms.app.data.realms

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val builder = chain.request().newBuilder()

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val token = user.getIdToken(true).await().token
            if (!token.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $token")
            }
        }

        chain.proceed(builder.build())
    }
}
