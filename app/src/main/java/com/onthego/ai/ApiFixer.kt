package com.onthego.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Grammar/style fix over text only (never audio). HttpURLConnection + org.json.
 * mode != "api" or blank text/key -> original text. Never throws.
 */
object ApiFixer {
    private const val TIMEOUT_MS = 30_000
    private const val SYS = "Fix grammar, return only fixed text"

    fun apply(mode: String, text: String, provider: String, endpoint: String, key: String): String {
        if (mode != "api" || text.isBlank() || key.isBlank()) return text
        return try {
            when (provider.lowercase()) {
                "gemini" -> fixGemini(text, key, endpoint)
                "grok", "xai", "x.ai" -> fixChat(text, key, endpoint.ifBlank { "https://api.x.ai/v1" })
                else -> fixChat(text, key, endpoint.ifBlank { "https://api.openai.com" })
            }
        } catch (_: Exception) {
            text
        }
    }

    /** Back-compat without Context (no key available): resolves provider/endpoint from Config. */
    fun apply(mode: String, text: String): String {
        if (mode != "api" || text.isBlank()) return text
        return try {
            apply(mode, text, com.onthego.Config.fixProvider, com.onthego.Config.fixEndpoint, "")
        } catch (_: Exception) {
            text
        }
    }

    private fun fixChat(text: String, key: String, baseOrUrl: String): String {
        val url = if ("chat/completions" in baseOrUrl) baseOrUrl
            else baseOrUrl.trimEnd('/') + "/v1/chat/completions"
        val body = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("temperature", 0)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYS))
                .put(JSONObject().put("role", "user").put("content", text)))
            .toString()
        val res = JSONObject(post(url, body, mapOf("Authorization" to "Bearer $key")))
        val out = res.optJSONArray("choices")
            ?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")?.trim()
        return out?.ifBlank { text } ?: text
    }

    private fun fixGemini(text: String, key: String, endpoint: String): String {
        val url = if ("generateContent" in endpoint) withKey(endpoint, key)
            else "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key"
        val body = JSONObject()
            .put("contents", org.json.JSONArray().put(JSONObject().put("parts",
                org.json.JSONArray().put(JSONObject().put("text", "$SYS:\n$text")))))
            .toString()
        val res = JSONObject(post(url, body, null))
        val out = res.optJSONArray("candidates")
            ?.optJSONObject(0)?.optJSONObject("content")
            ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text", "")?.trim()
        return out?.ifBlank { text } ?: text
    }

    private fun withKey(url: String, key: String): String =
        if ("key=" in url) url else url + (if ('?' in url) "&key=$key" else "?key=$key")

    private fun post(url: String, body: String, headers: Map<String, String>?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers?.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
                ?: throw RuntimeException("HTTP $code")
            val res = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw RuntimeException("HTTP $code: $res")
            return res
        } finally {
            c.disconnect()
        }
    }
}
