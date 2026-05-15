package com.ipn.memorygame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ipn.memorygame.databinding.ActivityMenuBinding
import com.ipn.memorygame.utils.ThemeManager

/**
 * Pantalla principal del menú.
 * Permite: seleccionar nivel, iniciar partida, ver partidas guardadas y cambiar tema.
 */
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPlay.setOnClickListener {
            val level = if (binding.rbHard.isChecked) 1 else 0
            startActivity(Intent(this, GameActivity::class.java).apply {
                putExtra("level", level)
            })
        }

        binding.btnSavedGames.setOnClickListener {
            startActivity(Intent(this, SavedGamesActivity::class.java))
        }

        binding.btnTheme.setOnClickListener { showThemeDialog() }
    }

    private fun showThemeDialog() {
        val options = arrayOf(getString(R.string.theme_guinda), getString(R.string.theme_azul))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_theme))
            .setItems(options) { _, which ->
                val theme = if (which == 0) ThemeManager.GUINDA else ThemeManager.AZUL
                ThemeManager.save(this, theme)
                recreate()
            }
            .show()
    }
}
