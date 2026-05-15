package com.ipn.filemanager.utils

import android.content.Context
import com.ipn.filemanager.R

/**
 * Maneja la persistencia y aplicación del tema seleccionado (Guinda o Azul).
 * Guarda la preferencia en SharedPreferences.
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"
    const val THEME_GUINDA = "guinda"
    const val THEME_AZUL = "azul"

    /** Guarda el tema elegido en SharedPreferences */
    fun saveTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme).apply()
    }

    /** Recupera el tema actual (Guinda por defecto) */
    fun getTheme(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, THEME_GUINDA) ?: THEME_GUINDA

    /** Devuelve el resource ID del tema para aplicar con setTheme() */
    fun getThemeResId(context: Context): Int =
        if (getTheme(context) == THEME_AZUL) R.style.Theme_Azul
        else R.style.Theme_Guinda
}
