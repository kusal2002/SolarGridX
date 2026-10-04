package com.kusal.solargridxmobile.data.api

import com.kusal.solargridxmobile.data.model.LoginRequest
import com.kusal.solargridxmobile.data.model.LoginResponse
import com.kusal.solargridxmobile.data.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Any>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
