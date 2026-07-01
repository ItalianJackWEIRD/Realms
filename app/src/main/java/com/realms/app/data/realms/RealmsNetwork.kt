package com.realms.app.data.realms

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RealmsNetwork {

    // ⚠️ DEMO LOCALE: sostituisci 192.168.1.42 con l'IP del TUO PC sulla rete del demo
    //    (Windows: ipconfig) e lascia la porta 8080.
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // 🔥 serve per vedere se l'Authorization header parte davvero
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())   // 🔑 aggiunge Authorization
            .addInterceptor(logging)             // 🔍 log headers
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private val api: RealmsApi by lazy { retrofit.create(RealmsApi::class.java) }

    val repository: RealmsRepository by lazy { RealmsRepository(api) }
}
