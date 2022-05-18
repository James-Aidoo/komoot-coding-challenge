package com.questdev.komootchallenge.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RemoteDataClient {
    const val FLICKER_API_KEY = "704473680ba3a1ba149d0e6b7c902818"
    private const val BASE_URL = "https://www.flickr.com/services/rest/"

    val retrofit: Retrofit
        get() =
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

    private val loggingInterceptor: HttpLoggingInterceptor
        get() = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)

    private val client: OkHttpClient
        get() =
            OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()
}
