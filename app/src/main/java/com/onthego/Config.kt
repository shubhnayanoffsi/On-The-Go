package com.onthego

import android.content.Context

/** Single source of truth. Backed by SharedPreferences. English only UI. */
object Config {
    // whisper
    var language: String = "en" // en | auto
    // vad
    var vadEnabled: Boolean = true
    var vadStrict: Int = 2 // 1..3
    // ai fix: off | api (no local LLM in v0.1)
    var aiFixMode: String = "off"
    var apiProvider: String = ""
    var apiKey: String = ""
    // outputs
    var clipboard: Boolean = true
    var card: Boolean = true
    var cardDurationSeconds: Int = 5
    var historyFile: Boolean = true
    // bubble
    var bubblePosition: String = "bottom_right" // 4 corners
    var bubbleSize: String = "medium" // small | medium
    // system
    var autostartOnBoot: Boolean = true
    var updateChannel: String = "stable"

    private const val P = "otg-config"

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        language = s.getString("language", "en") ?: "en"
        vadEnabled = s.getBoolean("vadEnabled", true)
        vadStrict = s.getInt("vadStrict", 2)
        aiFixMode = s.getString("aiFixMode", "off") ?: "off"
        apiProvider = s.getString("apiProvider", "") ?: ""
        apiKey = s.getString("apiKey", "") ?: ""
        clipboard = s.getBoolean("clipboard", true)
        card = s.getBoolean("card", true)
        cardDurationSeconds = s.getInt("cardDurationSeconds", 5)
        historyFile = s.getBoolean("historyFile", true)
        bubblePosition = s.getString("bubblePosition", "bottom_right") ?: "bottom_right"
        bubbleSize = s.getString("bubbleSize", "medium") ?: "medium"
        autostartOnBoot = s.getBoolean("autostartOnBoot", true)
        updateChannel = s.getString("updateChannel", "stable") ?: "stable"
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString("language", language)
            .putBoolean("vadEnabled", vadEnabled)
            .putInt("vadStrict", vadStrict)
            .putString("aiFixMode", aiFixMode)
            .putString("apiProvider", apiProvider)
            .putString("apiKey", apiKey)
            .putBoolean("clipboard", clipboard)
            .putBoolean("card", card)
            .putInt("cardDurationSeconds", cardDurationSeconds)
            .putBoolean("historyFile", historyFile)
            .putString("bubblePosition", bubblePosition)
            .putString("bubbleSize", bubbleSize)
            .putBoolean("autostartOnBoot", autostartOnBoot)
            .putString("updateChannel", updateChannel)
            .apply()
    }
}
