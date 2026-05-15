package com.ipn.memorygame.utils

import android.content.Context
import com.ipn.memorygame.R

object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY = "theme"
    const val GUINDA = "guinda"
    const val AZUL = "azul"

    fun save(context: Context, theme: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme).apply()

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, GUINDA) ?: GUINDA

    fun getResId(context: Context): Int =
        if (get(context) == AZUL) R.style.Theme_Azul else R.style.Theme_Guinda
}
