package com.realms.app.data.realms

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RealmsNetwork {

    // IMPORTANTE: Retrofit richiede lo slash finale oppure una baseUrl "directory"
    private const val BASE_URL = "https://realms-api-612950264784.europe-west8.run.app/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = run {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())   // 🔑 aggiunge Authorization: Bearer <FirebaseToken>
            .addInterceptor(logging)            // log base (non mostra il token)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api: RealmsApi = retrofit.create(RealmsApi::class.java)

    val repository: RealmsRepository = RealmsRepository(api)
}
