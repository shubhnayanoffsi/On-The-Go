package com.onthego

import android.content.Context

/** Single source of truth. Backed by SharedPreferences. English only UI. */
object Config {
    var language: String = "en" // en | auto
    var vadStrict: Int = 2 // 1..3, VAD always ON
    var aiFixMode: String = "off" // off | api
    var fixProvider: String = "openai" // openai | grok | gemini | custom
    var fixEndpoint: String = ""
    var sttProvider: String = "groq" // openai | groq | custom
    var sttEndpoint: String = ""
    var engineMode: String = "auto" // auto | offline | api
    var bubbleSize: String = "medium" // small | medium
    var bubbleX: Int = -1 // free position; -1 = default corner
    var bubbleY: Int = -1
    var autostartOnBoot: Boolean = true
    var updateChannel: String = "stable" // stable | beta
    var firstRunDone: Boolean = false

    private const val P = "otg-config"

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        language = s.getString("language", "en") ?: "en"
        vadStrict = s.getInt("vadStrict", 2).coerceIn(1, 3)
        aiFixMode = s.getString("aiFixMode", "off") ?: "off"
        fixProvider = s.getString("fixProvider", "openai") ?: "openai"
        fixEndpoint = s.getString("fixEndpoint", "") ?: ""
        sttProvider = s.getString("sttProvider", "groq") ?: "groq"
        sttEndpoint = s.getString("sttEndpoint", "") ?: ""
        engineMode = s.getString("engineMode", "auto") ?: "auto"
        bubbleSize = s.getString("bubbleSize", "medium") ?: "medium"
        bubbleX = s.getInt("bubbleX", -1)
        bubbleY = s.getInt("bubbleY", -1)
        autostartOnBoot = s.getBoolean("autostartOnBoot", true)
        updateChannel = s.getString("updateChannel", "stable") ?: "stable"
        firstRunDone = s.getBoolean("firstRunDone", false)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString("language", language)
            .putInt("vadStrict", vadStrict)
            .putString("aiFixMode", aiFixMode)
            .putString("fixProvider", fixProvider)
            .putString("fixEndpoint", fixEndpoint)
            .putString("sttProvider", sttProvider)
            .putString("sttEndpoint", sttEndpoint)
            .putString("engineMode", engineMode)
            .putString("bubbleSize", bubbleSize)
            .putInt("bubbleX", bubbleX)
            .putInt("bubbleY", bubbleY)
            .putBoolean("autostartOnBoot", autostartOnBoot)
            .putString("updateChannel", updateChannel)
            .putBoolean("firstRunDone", firstRunDone)
            .apply()
    }
}
