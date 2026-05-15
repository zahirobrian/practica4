package com.ipn.filemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room de la aplicación.
 * Singleton que garantiza una sola instancia durante el ciclo de vida de la app.
 */
@Database(
    entities = [FavoriteFile::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "filemanager_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
