package com.kusal.solargridxmobile.data.api

import android.content.Context
import com.kusal.solargridxmobile.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Configured for physical phone or emulator over local network
    const val BASE_URL = "http://192.168.11.192:5000/api/"


    private var retrofit: Retrofit? = null
    private var sessionManager: SessionManager? = null

    fun initialize(context: Context) {
        sessionManager = SessionManager(context.applicationContext)
    }

    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val token = sessionManager?.getToken()

            val requestBuilder = original.newBuilder()
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            requestBuilder.header("Accept", "application/json")

            return chain.proceed(requestBuilder.build())
        }
    }

    private fun getClient(): Retrofit {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor())
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val normalizedBaseUrl = if (BASE_URL.endsWith("/")) BASE_URL else "$BASE_URL/"

            retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    val authService: AuthApiService by lazy {
        getClient().create(AuthApiService::class.java)
    }

    val reservationService: ReservationApiService by lazy {
        getClient().create(ReservationApiService::class.java)
    }
    val transferService: TransferApiService by lazy {
        getClient().create(TransferApiService::class.java)
    }

    val stationService: StationApiService by lazy {
        getClient().create(StationApiService::class.java)
    }
}
