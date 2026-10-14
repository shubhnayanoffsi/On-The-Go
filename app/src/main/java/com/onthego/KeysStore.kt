package com.onthego

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys only. Encrypted at rest. Never log these values. */
object KeysStore {
    private const val PREF = "otg-keys"
    private const val STT = "sttKey"
    private const val FIX = "fixKey"

    private fun prefs(ctx: Context): SharedPreferences {
        val mk = MasterKey.Builder(ctx.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx.applicationContext,
            PREF,
            mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getSttKey(ctx: Context): String = prefs(ctx).getString(STT, "") ?: ""
    fun setSttKey(ctx: Context, v: String) { prefs(ctx).edit().putString(STT, v).apply() }
    fun clearSttKey(ctx: Context) { prefs(ctx).edit().remove(STT).apply() }

    fun getFixKey(ctx: Context): String = prefs(ctx).getString(FIX, "") ?: ""
    fun setFixKey(ctx: Context, v: String) { prefs(ctx).edit().putString(FIX, v).apply() }
    fun clearFixKey(ctx: Context) { prefs(ctx).edit().remove(FIX).apply() }
}
