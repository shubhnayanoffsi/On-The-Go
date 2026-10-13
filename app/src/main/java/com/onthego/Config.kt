package com.onthego

import android.content.Context
import org.yaml.snakeyaml.Yaml

/** Single config.yaml for all settings. Plain file, no DB in v0.1. */
object Config {
    var language: String = "en"
    var vadEnabled: Boolean = true
    var aiFixMode: String = "off"
    var clipboard: Boolean = true
    var card: Boolean = true
    var historyFile: Boolean = true

    fun load(ctx: Context) {
        // v0.1: defaults + asset config.yaml; user edits via Settings screen later.
    }
}
