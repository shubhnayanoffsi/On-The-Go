package com.onthego.stt

import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** OpenAI-shaped audio/transcriptions POST. Pure HttpURLConnection + org.json. */
object ApiTranscriber {
    private const val OPENAI_URL = "https://api.openai.com/v1/audio/transcriptions"
    private const val GROQ_URL = "https://api.groq.com/openai/v1/audio/transcriptions"

    fun transcribe(pcm16k: ShortArray, provider: String, endpoint: String, apiKey: String): String {
        require(pcm16k.isNotEmpty()) { "empty audio" }
        val p = provider.lowercase()
        val url = when (p) {
            "groq" -> endpoint.ifBlank { GROQ_URL }
            "custom" -> endpoint
            else -> endpoint.ifBlank { OPENAI_URL }
        }
        require(url.isNotBlank()) { "empty STT endpoint" }
        val model = when (p) {
            "groq" -> "whisper-large-v3-turbo"
            "openai" -> "whisper-1"
            else -> "whisper-large-v3"
        }

        val wav = pcmToWav(pcm16k)
        val boundary = "otg${System.currentTimeMillis()}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 60_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        DataOutputStream(conn.outputStream.buffered()).use { o ->
            val crlf = "\r\n"
            o.writeBytes("--$boundary$crlf")
            o.writeBytes("Content-Disposition: form-data; name=\"model\"$crlf$crlf")
            o.writeBytes("$model$crlf")
            o.writeBytes("--$boundary$crlf")
            o.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"$crlf")
            o.writeBytes("Content-Type: audio/wav$crlf$crlf")
            o.write(wav)
            o.writeBytes("$crlf--$boundary--$crlf")
            o.flush()
        }
        val code = conn.responseCode
        val body = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
        } finally {
            conn.disconnect()
        }
        if (code !in 200..299) throw java.io.IOException("STT HTTP $code: ${body.take(200)}")
        return JSONObject(body).optString("text", "").trim()
    }

    private fun pcmToWav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val o = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        o.put("RIFF".toByteArray()); o.putInt(36 + data); o.put("WAVE".toByteArray())
        o.put("fmt ".toByteArray()); o.putInt(16); o.putShort(1); o.putShort(1)
        o.putInt(16000); o.putInt(16000 * 2); o.putShort(2); o.putShort(16)
        o.put("data".toByteArray()); o.putInt(data)
        for (s in pcm) o.putShort(s)
        return o.array()
    }
}
