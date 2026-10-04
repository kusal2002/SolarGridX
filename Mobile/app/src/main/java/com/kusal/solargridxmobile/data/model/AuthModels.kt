package com.kusal.solargridxmobile.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val nic: String,
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("nic") val nic: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String,
    @SerializedName("accountStatus") val accountStatus: String,
    @SerializedName("token") val token: String
)

data class UserProfile(
    val nic: String,
    val name: String,
    val email: String,
    val role: String,
    val token: String
)
