package com.ipn.filemanager.utils

import android.content.Context
import android.content.SharedPreferences
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utilidades para manejo de archivos:
 * - Detección de tipo MIME
 * - Formato de tamaño y fecha
 * - Historial de archivos recientes
 * - Operaciones: copiar, mover, renombrar, eliminar
 */
object FileUtils {

    // ── Detección de tipo ─────────────────────────────────────────────────

    /** Obtiene el tipo MIME del archivo según su extensión */
    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    fun isImage(file: File): Boolean = getMimeType(file).startsWith("image/")

    fun isText(file: File): Boolean =
        file.extension.lowercase() in listOf(
            "txt", "md", "log", "csv", "html", "htm",
            "css", "js", "kt", "java", "py", "sh", "gradle", "xml", "json"
        )

    fun isJson(file: File): Boolean = file.extension.lowercase() == "json"
    fun isXml(file: File): Boolean  = file.extension.lowercase() == "xml"

    // ── Formato legible ───────────────────────────────────────────────────

    /** Convierte bytes a formato legible (B, KB, MB, GB) */
    fun formatSize(bytes: Long): String = when {
        bytes < 1_024            -> "$bytes B"
        bytes < 1_048_576        -> "${"%.1f".format(bytes / 1_024.0)} KB"
        bytes < 1_073_741_824    -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
        else                     -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
    }

    /** Convierte milisegundos a formato de fecha legible */
    fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    // ── Historial de recientes ────────────────────────────────────────────

    private fun recentPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("recents_prefs", Context.MODE_PRIVATE)

    /** Guarda una ruta en el historial de recientes (máx. 20 entradas) */
    fun saveRecent(context: Context, path: String) {
        val list = getRecents(context).toMutableList()
        list.remove(path)        // quita duplicados
        list.add(0, path)        // agrega al inicio
        recentPrefs(context).edit()
            .putString("paths", list.take(20).joinToString("|"))
            .apply()
    }

    /** Recupera la lista de archivos recientes */
    fun getRecents(context: Context): List<String> {
        val raw = recentPrefs(context).getString("paths", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|")
    }

    // ── Operaciones de archivo ────────────────────────────────────────────

    /** Copia un archivo al directorio destino */
    fun copyFile(src: File, destDir: File): Boolean = try {
        src.copyTo(File(destDir, src.name), overwrite = true)
        true
    } catch (e: Exception) { false }

    /** Mueve un archivo al directorio destino */
    fun moveFile(src: File, destDir: File): Boolean = try {
        val dest = File(destDir, src.name)
        src.copyTo(dest, overwrite = true)
        src.delete()
        true
    } catch (e: Exception) { false }

    /** Renombra un archivo */
    fun renameFile(file: File, newName: String): Boolean =
        file.renameTo(File(file.parent, newName))

    /** Elimina un archivo o directorio (recursivo si es carpeta) */
    fun deleteFile(file: File): Boolean =
        if (file.isDirectory) file.deleteRecursively() else file.delete()

    // ── Icono por tipo ────────────────────────────────────────────────────

    /** Devuelve un emoji representativo según el tipo de archivo */
    fun getFileEmoji(file: File): String = when {
        file.isDirectory -> "📁"
        isImage(file)    -> "🖼️"
        isJson(file)     -> "{ }"
        isXml(file)      -> "</>"
        file.extension.lowercase() in listOf("txt","md","log") -> "📄"
        file.extension.lowercase() in listOf("mp3","wav","ogg","flac") -> "🎵"
        file.extension.lowercase() in listOf("mp4","avi","mkv","mov") -> "🎬"
        file.extension.lowercase() == "pdf" -> "📕"
        file.extension.lowercase() in listOf("zip","rar","7z","tar","gz") -> "📦"
        file.extension.lowercase() in listOf("apk") -> "📱"
        else -> "📎"
    }
}
