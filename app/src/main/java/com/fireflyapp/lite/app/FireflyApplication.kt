package com.fireflyapp.lite.app

import android.content.Context
import android.app.Application

class FireflyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.applySavedLanguage(this)
    }
}
