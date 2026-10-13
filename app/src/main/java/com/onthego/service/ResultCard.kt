package com.onthego.service

import android.content.Context
import android.widget.Toast

/** Result card popup after each transcription. Copy is automatic. */
object ResultCard {
    fun show(ctx: Context, text: String) {
        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
        // TODO v0.1: overlay card with Copy(done) / Retry / Close + duration pref
    }
}
