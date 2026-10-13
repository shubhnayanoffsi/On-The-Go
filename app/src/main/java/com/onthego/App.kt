package com.onthego

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Config.load(this)
    }
}
