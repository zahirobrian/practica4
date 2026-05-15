package com.ipn.memorygame

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ipn.memorygame.databinding.ActivityFileViewBinding
import com.ipn.memorygame.utils.GameFileManager
import com.ipn.memorygame.utils.ThemeManager

/**
 * Muestra el contenido crudo del archivo de guardado (TXT, JSON o XML).
 * Cumple el requisito de "visualizar el contenido de los archivos de guardado".
 */
class FileViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setTitleTextColor(0xFFFFFFFF.toInt())

        val path  = intent.getStringExtra("file_path") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: "Archivo"
        supportActionBar?.title = title

        val content = GameFileManager.readRaw(path)
        binding.tvContent.text = content
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
