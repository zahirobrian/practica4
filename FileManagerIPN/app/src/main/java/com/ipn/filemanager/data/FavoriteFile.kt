package com.ipn.filemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad Room que representa un archivo o carpeta marcado como favorito.
 * @param path Ruta absoluta del archivo (clave primaria)
 * @param name Nombre del archivo para mostrarlo sin re-leer el disco
 * @param isDirectory Indica si es carpeta
 * @param addedAt Timestamp de cuando se marcó como favorito
 */
@Entity(tableName = "favorites")
data class FavoriteFile(
    @PrimaryKey val path: String,
    val name: String,
    val isDirectory: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)
