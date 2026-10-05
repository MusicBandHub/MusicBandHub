package com.musicbandhub.app.auth

import com.musicbandhub.app.data.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthRepository {
    private val client = OkHttpClient(); private val json = "application/json".toMediaType()
    fun signUp(email: String, password: String): Result<String> = request("/auth/v1/signup", email, password)
    fun signIn(email: String, password: String): Result<String> = request("/auth/v1/token?grant_type=password", email, password)
    private fun request(path: String, email: String, password: String): Result<String> = runCatching {
        require(!SupabaseConfig.URL.contains("YOUR_PROJECT")) { "Supabase URL is not configured" }; require(email.contains("@")) { "Введите корректный email" }; require(password.length >= 6) { "Пароль должен быть не короче 6 символов" }
        val body = JSONObject().put("email", email).put("password", password).toString().toRequestBody(json)
        val req = Request.Builder().url(SupabaseConfig.URL + path).post(body).addHeader("apikey", SupabaseConfig.ANON_KEY).addHeader("Content-Type", "application/json").build()
        client.newCall(req).execute().use { response -> val text = response.body?.string().orEmpty(); if (!response.isSuccessful) error("Supabase ${response.code}: $text"); val token = JSONObject(text).optString("access_token"); if (token.isBlank()) error("Регистрация выполнена. Подтвердите email, затем войдите."); token }
    }
}
