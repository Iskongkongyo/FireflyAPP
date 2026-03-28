package com.fireflyapp.lite.app

import android.content.res.Configuration
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguageMode(
    val persistedValue: String,
    val languageTags: String?
) {
    FOLLOW_SYSTEM("system", null),
    CHINESE_SIMPLIFIED("zh-CN", "zh-CN"),
    ENGLISH("en", "en");

    companion object {
        fun fromPersistedValue(value: String?): AppLanguageMode {
            return values().firstOrNull { it.persistedValue == value } ?: FOLLOW_SYSTEM
        }
    }
}

object AppLanguageManager {
    fun getSavedMode(context: Context): AppLanguageMode {
        return AppLanguageMode.fromPersistedValue(
            preferences(context).getString(KEY_LANGUAGE_MODE, AppLanguageMode.FOLLOW_SYSTEM.persistedValue)
        )
    }

    fun setLanguageMode(context: Context, mode: AppLanguageMode) {
        preferences(context).edit().putString(KEY_LANGUAGE_MODE, mode.persistedValue).apply()
        applyLanguageMode(mode)
    }

    fun applySavedLanguage(context: Context) {
        applyLanguageMode(getSavedMode(context))
    }

    fun wrapContext(base: Context): Context {
        val mode = getSavedMode(base)
        val tag = mode.languageTags ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    private fun applyLanguageMode(mode: AppLanguageMode) {
        val locales = mode.languageTags
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        mode.languageTags?.let {
            Locale.setDefault(Locale.forLanguageTag(it))
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "app_language_preferences"
    private const val KEY_LANGUAGE_MODE = "language_mode"
}
