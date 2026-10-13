package com.onthego.ai

/**
 * v0.1: AI fix is OFF by default (plain text out).
 * mode=api (v0.2): sends TEXT ONLY, never audio, to chosen provider.
 * No local LLM in this app. No keys stored in v0.1.
 */
object ApiFixer {
    fun apply(mode: String, text: String): String {
        if (mode != "api" || text.isBlank()) return text
        // TODO v0.2: POST text to provider with key from system keystore.
        return text
    }
}
