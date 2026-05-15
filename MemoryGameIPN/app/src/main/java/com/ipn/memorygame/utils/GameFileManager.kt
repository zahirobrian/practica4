package com.ipn.memorygame.utils

import android.content.Context
import com.ipn.memorygame.model.GameState
import com.ipn.memorygame.model.SavedGameMeta
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestiona el guardado y carga de partidas en tres formatos:
 * - Texto plano (.txt)
 * - JSON (.json)
 * - XML (.xml)
 *
 * Los archivos se guardan en el almacenamiento interno de la app (filesDir/saves/).
 */
object GameFileManager {

    private fun savesDir(context: Context): File {
        val dir = File(context.filesDir, "saves")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    // ── GUARDAR ──────────────────────────────────────────────────────────

    /**
     * Guarda la partida en el formato indicado.
     * @param format "TXT", "JSON" o "XML"
     * @return true si se guardó correctamente
     */
    fun save(context: Context, state: GameState, format: String): Boolean {
        return try {
            val ext = format.lowercase()
            val ts = System.currentTimeMillis()
            val tag = state.tag.ifBlank { "partida" }.replace(" ", "_")
            val file = File(savesDir(context), "${tag}_$ts.$ext")
            val content = when (format) {
                "TXT"  -> toTxt(state)
                "JSON" -> toJson(state)
                "XML"  -> toXml(state)
                else   -> toJson(state)
            }
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) { false }
    }

    /** Serializa a texto plano */
    private fun toTxt(s: GameState): String = buildString {
        appendLine("=== Memory Game IPN - Partida Guardada ===")
        appendLine("Etiqueta: ${s.tag}")
        appendLine("Nivel: ${if (s.level == 0) "Fácil (4x4)" else "Difícil (6x6)"}")
        appendLine("Puntuación: ${s.score}")
        appendLine("Movimientos: ${s.moves}")
        appendLine("Tiempo: ${formatTime(s.timeElapsed)}")
        appendLine("Guardado: ${formatDate(s.savedAt)}")
        appendLine()
        appendLine("=== Tablero ===")
        appendLine("board=${s.board.joinToString(",")}")
        appendLine("flipped=${s.flipped.joinToString(",")}")
        appendLine("matched=${s.matched.joinToString(",")}")
        appendLine()
        appendLine("=== Configuración ===")
        s.customSettings.forEach { (k, v) -> appendLine("$k=$v") }
        appendLine()
        appendLine("=== Historial de Movimientos ===")
        s.moveHistory.forEachIndexed { i, m -> appendLine("${i + 1}. $m") }
    }

    /** Serializa a JSON */
    private fun toJson(s: GameState): String {
        val obj = JSONObject().apply {
            put("tag", s.tag)
            put("level", s.level)
            put("score", s.score)
            put("moves", s.moves)
            put("timeElapsed", s.timeElapsed)
            put("savedAt", s.savedAt)
            put("board", JSONArray(s.board))
            put("flipped", JSONArray(s.flipped))
            put("matched", JSONArray(s.matched))
            val settings = JSONObject()
            s.customSettings.forEach { (k, v) -> settings.put(k, v) }
            put("customSettings", settings)
            put("moveHistory", JSONArray(s.moveHistory))
        }
        return obj.toString(2)
    }

    /** Serializa a XML */
    private fun toXml(s: GameState): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("<gameState>")
        appendLine("  <tag>${s.tag}</tag>")
        appendLine("  <level>${s.level}</level>")
        appendLine("  <score>${s.score}</score>")
        appendLine("  <moves>${s.moves}</moves>")
        appendLine("  <timeElapsed>${s.timeElapsed}</timeElapsed>")
        appendLine("  <savedAt>${s.savedAt}</savedAt>")
        appendLine("  <board>")
        s.board.forEach { appendLine("    <card>${it}</card>") }
        appendLine("  </board>")
        appendLine("  <flipped>")
        s.flipped.forEach { appendLine("    <v>${it}</v>") }
        appendLine("  </flipped>")
        appendLine("  <matched>")
        s.matched.forEach { appendLine("    <v>${it}</v>") }
        appendLine("  </matched>")
        appendLine("  <customSettings>")
        s.customSettings.forEach { (k, v) -> appendLine("    <$k>$v</$k>") }
        appendLine("  </customSettings>")
        appendLine("  <moveHistory>")
        s.moveHistory.forEach { appendLine("    <move>${it}</move>") }
        appendLine("  </moveHistory>")
        appendLine("</gameState>")
    }

    // ── CARGAR ────────────────────────────────────────────────────────────

    /**
     * Carga una partida desde el archivo indicado.
     * Detecta el formato automáticamente por extensión.
     */
    fun load(file: File): GameState? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            when (file.extension.lowercase()) {
                "json" -> fromJson(content)
                "xml"  -> fromXml(content)
                "txt"  -> fromTxt(content)
                else   -> null
            }
        } catch (e: Exception) { null }
    }

    private fun fromJson(content: String): GameState {
        val obj = JSONObject(content)
        val board    = (0 until obj.getJSONArray("board").length()).map { obj.getJSONArray("board").getString(it) }
        val flipped  = (0 until obj.getJSONArray("flipped").length()).map { obj.getJSONArray("flipped").getBoolean(it) }
        val matched  = (0 until obj.getJSONArray("matched").length()).map { obj.getJSONArray("matched").getBoolean(it) }
        val history  = (0 until obj.getJSONArray("moveHistory").length()).map { obj.getJSONArray("moveHistory").getString(it) }
        val settings = mutableMapOf<String, String>()
        val sObj = obj.optJSONObject("customSettings")
        sObj?.keys()?.forEach { k -> settings[k] = sObj.getString(k) }
        return GameState(
            level = obj.getInt("level"),
            board = board, flipped = flipped, matched = matched,
            score = obj.getInt("score"), moves = obj.getInt("moves"),
            timeElapsed = obj.getLong("timeElapsed"),
            moveHistory = history, customSettings = settings,
            tag = obj.optString("tag", ""),
            savedAt = obj.optLong("savedAt", System.currentTimeMillis())
        )
    }

    private fun fromXml(content: String): GameState {
        fun tag(name: String): String {
            val r = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            return r.find(content)?.groupValues?.get(1)?.trim() ?: ""
        }
        fun listTag(outer: String, inner: String): List<String> {
            val block = tag(outer)
            return Regex("<$inner>(.*?)</$inner>").findAll(block).map { it.groupValues[1].trim() }.toList()
        }
        val settings = mutableMapOf<String, String>()
        val settingsBlock = tag("customSettings")
        Regex("<(\\w+)>(.*?)</\\1>").findAll(settingsBlock).forEach {
            settings[it.groupValues[1]] = it.groupValues[2].trim()
        }
        return GameState(
            level = tag("level").toIntOrNull() ?: 0,
            board = listTag("board", "card"),
            flipped = listTag("flipped", "v").map { it.toBoolean() },
            matched = listTag("matched", "v").map { it.toBoolean() },
            score = tag("score").toIntOrNull() ?: 0,
            moves = tag("moves").toIntOrNull() ?: 0,
            timeElapsed = tag("timeElapsed").toLongOrNull() ?: 0L,
            moveHistory = listTag("moveHistory", "move"),
            customSettings = settings,
            tag = tag("tag"),
            savedAt = tag("savedAt").toLongOrNull() ?: System.currentTimeMillis()
        )
    }

    private fun fromTxt(content: String): GameState {
        val lines = content.lines()
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=") ?: ""
        val board   = value("board").split(",").filter { it.isNotBlank() }
        val flipped = value("flipped").split(",").map { it.trim().toBoolean() }
        val matched = value("matched").split(",").map { it.trim().toBoolean() }
        return GameState(
            level = 0, board = board, flipped = flipped, matched = matched,
            score = 0, moves = 0, timeElapsed = 0L,
            moveHistory = emptyList(), customSettings = emptyMap(),
            tag = value("Etiqueta")
        )
    }

    // ── LISTAR ────────────────────────────────────────────────────────────

    /** Devuelve los metadatos de todas las partidas guardadas ordenadas por fecha */
    fun listSaves(context: Context): List<SavedGameMeta> {
        val dir = savesDir(context)
        return dir.listFiles()
            ?.filter { it.extension in listOf("txt", "json", "xml") }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                try {
                    val state = load(file)
                    SavedGameMeta(
                        fileName = file.name,
                        format = file.extension.uppercase(),
                        tag = state?.tag ?: file.nameWithoutExtension,
                        score = state?.score ?: 0,
                        level = if ((state?.level ?: 0) == 0) "Fácil" else "Difícil",
                        timeElapsed = state?.timeElapsed ?: 0L,
                        savedAt = file.lastModified(),
                        filePath = file.absolutePath
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
    }

    /** Elimina un archivo de guardado */
    fun delete(filePath: String): Boolean = File(filePath).delete()

    /** Lee el contenido crudo del archivo */
    fun readRaw(filePath: String): String =
        try { File(filePath).readText(Charsets.UTF_8) }
        catch (e: Exception) { "Error al leer el archivo: ${e.message}" }
}
