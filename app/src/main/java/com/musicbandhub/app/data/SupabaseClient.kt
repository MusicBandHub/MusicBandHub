package com.musicbandhub.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseClient {
    private val http = OkHttpClient()
    private val json = "application/json".toMediaType()
    fun get(path: String, token: String): String = request("GET", path, token, null)
    fun post(path: String, token: String, body: JSONObject): String = request("POST", path, token, body.toString())
    fun patch(path: String, token: String, body: JSONObject): String = request("PATCH", path, token, body.toString())
    fun delete(path: String, token: String): String = request("DELETE", path, token, null)
    private fun request(method: String, path: String, token: String, body: String?): String {
        require(!SupabaseConfig.URL.contains("YOUR_PROJECT")) { "Supabase URL is not configured" }
        val builder = Request.Builder().url(SupabaseConfig.URL + path).addHeader("apikey", SupabaseConfig.ANON_KEY).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/json").addHeader("Prefer", "return=representation")
        when (method) { "POST" -> builder.post((body ?: "{}").toRequestBody(json)); "PATCH" -> builder.patch((body ?: "{}").toRequestBody(json)); "DELETE" -> builder.delete(); else -> builder.get() }
        http.newCall(builder.build()).execute().use { r -> val text = r.body?.string().orEmpty(); if (!r.isSuccessful) error("Supabase ${r.code}: $text"); return text }
    }
    fun parseArray(text: String): JSONArray = JSONArray(text)
    fun parseObject(text: String): JSONObject = JSONObject(text)
}
