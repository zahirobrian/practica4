package com.ipn.memorygame.model

/**
 * Representa el estado completo de una partida de Memory Game.
 * Este modelo se serializa a TXT, JSON o XML para guardar/cargar partidas.
 */
data class GameState(
    val level: Int,                        // 0 = Fácil (4x4), 1 = Difícil (6x6)
    val board: List<String>,               // Emojis del tablero en orden
    val flipped: List<Boolean>,            // Qué cartas están boca arriba
    val matched: List<Boolean>,            // Qué cartas ya se emparejaron
    val score: Int,                        // Puntuación actual
    val moves: Int,                        // Número de movimientos
    val timeElapsed: Long,                 // Segundos transcurridos
    val moveHistory: List<String>,         // Historial de movimientos
    val customSettings: Map<String, String>, // Configuraciones (tema, nivel)
    val tag: String = "",                  // Etiqueta del jugador
    val savedAt: Long = System.currentTimeMillis() // Timestamp de guardado
)

/**
 * Metadatos de una partida guardada para mostrar en la lista.
 */
data class SavedGameMeta(
    val fileName: String,
    val format: String,      // TXT, JSON, XML
    val tag: String,
    val score: Int,
    val level: String,
    val timeElapsed: Long,
    val savedAt: Long,
    val filePath: String
)
