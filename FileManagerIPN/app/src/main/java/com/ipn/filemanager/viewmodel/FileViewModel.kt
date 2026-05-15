package com.ipn.filemanager.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.*
import com.ipn.filemanager.App
import com.ipn.filemanager.data.FavoriteFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel que gestiona el estado de navegación de archivos.
 * Mantiene: directorio actual, lista de archivos y pila de navegación.
 * Sobrevive a rotaciones de pantalla.
 */
class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as App).database.favoriteDao()

    // Directorio que se está mostrando actualmente
    private val _currentDir = MutableLiveData<File>()
    val currentDir: LiveData<File> = _currentDir

    // Lista de archivos/carpetas del directorio actual
    private val _files = MutableLiveData<List<File>>()
    val files: LiveData<List<File>> = _files

    // Pila de navegación para el botón atrás
    private val dirStack = ArrayDeque<File>()

    // Favoritos observables desde Room
    val favorites: LiveData<List<FavoriteFile>> = dao.getAll()

    init {
        // Inicia en el almacenamiento externo si existe, si no en interno
        val startDir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            Environment.getExternalStorageDirectory()
        } else {
            application.filesDir
        }
        navigateTo(startDir)
    }

    /** Navega a un directorio, guardando el anterior en la pila */
    fun navigateTo(dir: File) {
        _currentDir.value?.let { dirStack.addLast(it) }
        _currentDir.value = dir
        loadFiles(dir)
    }

    /** Navega al directorio anterior. Devuelve true si hubo navegación */
    fun navigateUp(): Boolean {
        return if (dirStack.isNotEmpty()) {
            val parent = dirStack.removeLast()
            _currentDir.value = parent
            loadFiles(parent)
            true
        } else false
    }

    /** Navega al directorio padre del actual */
    fun navigateToParent(): Boolean {
        val parent = _currentDir.value?.parentFile ?: return false
        navigateTo(parent)
        return true
    }

    /** Recarga el directorio actual */
    fun refresh() { _currentDir.value?.let { loadFiles(it) } }

    /** Carga la lista de archivos en background y la publica como LiveData */
    private fun loadFiles(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = try {
                dir.listFiles()
                    ?.sortedWith(
                        compareByDescending<File> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    ) ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            _files.postValue(list)
        }
    }

    // ── Favoritos ─────────────────────────────────────────────────────────

    fun addFavorite(file: File) = viewModelScope.launch {
        dao.insert(FavoriteFile(file.absolutePath, file.name, file.isDirectory))
    }

    fun removeFavorite(file: File) = viewModelScope.launch {
        dao.deleteByPath(file.absolutePath)
    }

    suspend fun isFavorite(path: String): Boolean = dao.isFavorite(path)

    /** Cambia el estado de favorito (toggle) */
    fun toggleFavorite(file: File) = viewModelScope.launch {
        if (dao.isFavorite(file.absolutePath)) {
            dao.deleteByPath(file.absolutePath)
        } else {
            dao.insert(FavoriteFile(file.absolutePath, file.name, file.isDirectory))
        }
    }
}
