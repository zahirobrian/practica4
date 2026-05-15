package com.ipn.filemanager

import android.app.Application
import com.ipn.filemanager.data.AppDatabase

/**
 * Clase Application personalizada.
 * Inicializa la base de datos Room de forma lazy (solo cuando se necesite).
 */
class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
