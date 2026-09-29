package com.kusal.solargridxmobile.data.repository

import com.kusal.solargridxmobile.data.api.ApiClient
import com.kusal.solargridxmobile.data.local.SessionManager
import com.kusal.solargridxmobile.data.model.LoginRequest
import com.kusal.solargridxmobile.data.model.LoginResponse
import com.kusal.solargridxmobile.data.model.RegisterRequest

class AccountPendingApprovalException(message: String) : Exception(message)
class AccountInactiveException(message: String) : Exception(message)

class AuthRepository(private val sessionManager: SessionManager) {
    private val authApi = ApiClient.authService

    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = authApi.login(LoginRequest(email.trim(), password))
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                sessionManager.saveSession(data)
                Result.success(data)
            } else {
                val raw = response.errorBody()?.string()
                val errorMsg = parseErrorMessage(raw, "Invalid email or password.")
                when {
                    errorMsg.contains("pending", ignoreCase = true) -> {
                        Result.failure(AccountPendingApprovalException(errorMsg))
                    }
                    errorMsg.contains("inactive", ignoreCase = true) ||
                    errorMsg.contains("deactivated", ignoreCase = true) ||
                    errorMsg.contains("deactivation", ignoreCase = true) -> {
                        Result.failure(AccountInactiveException(errorMsg))
                    }
                    else -> {
                        Result.failure(Exception(errorMsg))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        nic: String,
        name: String,
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            val response = authApi.register(
                RegisterRequest(nic.trim(), name.trim(), email.trim(), password)
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val raw = response.errorBody()?.string()
                val errorMsg = parseErrorMessage(raw, "Registration failed.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        return try {
            val json = org.json.JSONObject(errorBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("title") -> json.getString("title")
                else -> errorBody
            }
        } catch (_: Exception) {
            errorBody
        }
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
    fun getUserNic(): String? = sessionManager.getUserNic()
    fun logout() = sessionManager.clearSession()
}
