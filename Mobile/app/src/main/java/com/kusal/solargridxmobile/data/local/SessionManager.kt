package com.kusal.solargridxmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import com.kusal.solargridxmobile.data.model.LoginResponse
import com.kusal.solargridxmobile.data.model.UserProfile

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("solargridx_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_NIC = "user_nic"
        private const val KEY_NAME = "user_name"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_ROLE = "user_role"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveSession(response: LoginResponse) {
        prefs.edit().apply {
            putString(KEY_TOKEN, response.token)
            putString(KEY_NIC, response.nic)
            putString(KEY_NAME, response.name)
            putString(KEY_EMAIL, response.email)
            putString(KEY_ROLE, response.role)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUserNic(): String? = prefs.getString(KEY_NIC, null)

    fun getUserName(): String? = prefs.getString(KEY_NAME, null)

    fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getUserRole(): String? = prefs.getString(KEY_ROLE, null)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false) && getToken() != null

    fun getUserProfile(): UserProfile? {
        val token = getToken() ?: return null
        val nic = getUserNic() ?: ""
        val name = getUserName() ?: ""
        val email = getUserEmail() ?: ""
        val role = getUserRole() ?: "Prosumer"
        return UserProfile(nic, name, email, role, token)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
