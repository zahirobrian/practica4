package com.ipn.filemanager.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * DAO (Data Access Object) para operaciones de favoritos en Room.
 */
@Dao
interface FavoriteDao {

    /** Obtiene todos los favoritos ordenados por fecha descendente */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): LiveData<List<FavoriteFile>>

    /** Inserta o reemplaza un favorito */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FavoriteFile)

    /** Elimina un favorito */
    @Delete
    suspend fun delete(file: FavoriteFile)

    /** Verifica si una ruta ya es favorita */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    /** Elimina un favorito por su ruta */
    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
