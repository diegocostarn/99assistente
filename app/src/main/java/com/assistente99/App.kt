package com.assistente99

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Pal.apply(this)
    }
}
