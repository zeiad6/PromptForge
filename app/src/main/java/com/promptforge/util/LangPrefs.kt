package com.promptforge.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language preference (synchronous SharedPreferences so
 * attachBaseContext can read it before any frame is drawn).
 *
 *  - "system": follow the device language (values-ar / default values = English)
 *  - "ar" / "en": explicit override chosen from Settings.
 */
object LangPrefs {

    private const val FILE = "forge_lang"
    private const val KEY = "app_lang"
    const val SYSTEM = "system"
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    fun get(context: Context): String =
        context.getSharedPreferences(FILE, MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, value: String) {
        context.getSharedPreferences(FILE, MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    /** Wraps the base context with the chosen locale (no-op when following system). */
    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang == SYSTEM) return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** True when the effective UI language is Arabic. */
    fun isArabic(context: Context): Boolean {
        val lang = get(context)
        return when (lang) {
            ARABIC -> true
            ENGLISH -> false
            else -> Locale.getDefault().language.startsWith("ar")
        }
    }
}
