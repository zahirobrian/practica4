package com.ipn.memorygame.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Maneja los efectos de sonido del juego usando SoundPool.
 * Sonidos generados con tonos sintetizados (sin archivos de audio externos).
 */
object SoundManager {

    private var soundPool: SoundPool? = null
    private var soundFlip = 0
    private var soundMatch = 0
    private var soundWin = 0
    private var soundFail = 0
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
        // Los sonidos se generan programáticamente en GameActivity
        // usando ToneGenerator para no necesitar archivos de audio
        initialized = true
    }

    fun playFlip() = playTone(800, 80)
    fun playMatch() = playTone(1000, 150)
    fun playWin()   = playTone(1200, 400)
    fun playFail()  = playTone(400, 100)

    private fun playTone(freq: Int, durationMs: Int) {
        try {
            val tg = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC, 60
            )
            val tone = when {
                freq < 500  -> android.media.ToneGenerator.TONE_PROP_BEEP
                freq < 900  -> android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE
                freq < 1100 -> android.media.ToneGenerator.TONE_PROP_ACK
                else        -> android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            }
            tg.startTone(tone, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tg.release()
            }, durationMs.toLong() + 100)
        } catch (e: Exception) { /* ignora si el dispositivo no soporta */ }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        initialized = false
    }
}
