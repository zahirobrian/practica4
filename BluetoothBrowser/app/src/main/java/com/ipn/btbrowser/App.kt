package com.ipn.btbrowser

import android.app.Application
import android.content.SharedPreferences

class App : Application() {
    companion object {
        lateinit var prefs: SharedPreferences
    }
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("btbrowser_prefs", MODE_PRIVATE)
    }
}