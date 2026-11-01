package com.ringan.musik

import android.app.Application

class App : Application() {
    lateinit var library: Library
        private set

    override fun onCreate() {
        super.onCreate()
        library = Library(this)
    }
}
