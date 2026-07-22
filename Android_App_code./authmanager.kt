package com.example.arglasses


import android.content.Context
import java.security.MessageDigest

class AuthManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("auth_data", Context.MODE_PRIVATE)

    fun isRegistered(): Boolean {
        return prefs.contains("email") &&
                prefs.contains("password_hash")
    }

    fun register(email: String, password: String): Boolean {
        if (!isValidGmail(email)) return false

        val hash = hash(password)

        prefs.edit()
            .putString("email", email)
            .putString("password_hash", hash)
            .putBoolean("logged_in", true)
            .apply()

        return true
    }

    fun login(email: String, password: String): Boolean {
        val storedEmail = prefs.getString("email", null)
        val storedHash = prefs.getString("password_hash", null)

        if (storedEmail == null || storedHash == null)
            return false

        return if (
            storedEmail == email &&
            storedHash == hash(password)
        ) {
            prefs.edit()
                .putBoolean("logged_in", true)
                .apply()
            true
        } else {
            false
        }
    }

    fun logout() {
        prefs.edit()
            .putBoolean("logged_in", false)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("logged_in", false)
    }

    fun getEmail(): String? {
        return prefs.getString("email", null)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())

        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun isValidGmail(email: String): Boolean {
        val regex = Regex("^[A-Za-z0-9+_.-]+@gmail\\.com$")
        return regex.matches(email)
    }
}
