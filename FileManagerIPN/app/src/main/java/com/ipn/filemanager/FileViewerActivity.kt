package com.ipn.filemanager

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.chrisbanes.photoview.PhotoView
import com.bumptech.glide.Glide
import com.ipn.filemanager.databinding.ActivityFileViewerBinding
import com.ipn.filemanager.utils.FileUtils
import com.ipn.filemanager.utils.ThemeManager
import java.io.File
import java.util.regex.Pattern

/**
 * Activity para visualizar el contenido de archivos.
 * Soporta: imágenes (con zoom/rotación), texto plano, JSON y XML (con resaltado de sintaxis).
 * Para tipos no soportados, lanza el chooser de otras apps.
 */
class FileViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getThemeResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        val file = File(path)

        if (!file.exists()) {
            Toast.makeText(this, "Archivo no encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.title = file.name

        // Metadatos en la barra inferior
        binding.tvMetaSize.text = FileUtils.formatSize(file.length())
        binding.tvMetaDate.text = FileUtils.formatDate(file.lastModified())
        binding.tvMetaMime.text = FileUtils.getMimeType(file)

        displayFile(file)
    }

    /** Decide cómo mostrar el archivo según su tipo */
    private fun displayFile(file: File) {
        binding.viewerContainer.removeAllViews()
        when {
            FileUtils.isImage(file) -> showImage(file)
            FileUtils.isJson(file)  -> showText(file, syntax = "json")
            FileUtils.isXml(file)   -> showText(file, syntax = "xml")
            FileUtils.isText(file)  -> showText(file, syntax = "plain")
            else                    -> showUnsupportedOptions(file)
        }
    }

    // ── Visualizadores ─────────────────────────────────────────────────────

    /**
     * Muestra imágenes con PhotoView (soporte para zoom con pinch y rotación con doble tap).
     */
    private fun showImage(file: File) {
        val photoView = PhotoView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        Glide.with(this).load(file).into(photoView)
        binding.viewerContainer.addView(photoView)
    }

    /**
     * Muestra archivos de texto con resaltado de sintaxis opcional para JSON y XML.
     */
    private fun showText(file: File, syntax: String) {
        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            getString(R.string.error_reading) + ": ${e.message}"
        }

        val scroll = ScrollView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tv = TextView(this).apply {
            setPadding(24, 24, 24, 24)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            text = when (syntax) {
                "json" -> colorizeJson(content)
                "xml"  -> colorizeXml(content)
                else   -> content
            }
        }

        scroll.addView(tv)
        binding.viewerContainer.addView(scroll)
    }

    // ── Resaltado de sintaxis ──────────────────────────────────────────────

    /** Aplica colores al contenido JSON */
    private fun colorizeJson(json: String): SpannableString {
        val span = SpannableString(json)
        // Claves (strings antes de :)
        highlight(span, json, """"([\w\s]+)"\s*:""", Color.parseColor("#FF6B6B"))
        // Valores string
        highlight(span, json, """:\s*"([^"]*)"""", Color.parseColor("#A8E6CF"))
        // Valores numéricos
        highlight(span, json, """:\s*(-?\d+\.?\d*)""", Color.parseColor("#FFD93D"))
        // true/false/null
        highlight(span, json, """\b(true|false|null)\b""", Color.parseColor("#C3A6FF"))
        return span
    }

    /** Aplica colores al contenido XML */
    private fun colorizeXml(xml: String): SpannableString {
        val span = SpannableString(xml)
        // Tags de apertura/cierre
        highlight(span, xml, """</?\w[\w:.-]*""", Color.parseColor("#FF6B6B"))
        // Atributos
        highlight(span, xml, """\s[\w:.-]+=(?=")""", Color.parseColor("#FFD93D"))
        // Valores de atributos
        highlight(span, xml, """"[^"]*"""", Color.parseColor("#A8E6CF"))
        // Comentarios
        highlight(span, xml, """<!--.*?-->""", Color.parseColor("#6B8E6B"))
        return span
    }

    /** Aplica un color a todas las coincidencias de un regex en el SpannableString */
    private fun highlight(span: SpannableString, text: String, regex: String, color: Int) {
        try {
            val matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(text)
            while (matcher.find()) {
                span.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(), matcher.end(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) { /* ignora errores de regex */ }
    }

    // ── Archivo no soportado ───────────────────────────────────────────────

    /**
     * Para archivos que no se pueden visualizar internamente,
     * muestra un diálogo inteligente para abrirlos con otras apps.
     */
    private fun showUnsupportedOptions(file: File) {
        val tv = TextView(this).apply {
            text = "${getString(R.string.unsupported_file)}\n\n${file.name}"
            setPadding(48, 48, 48, 48)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
        }
        binding.viewerContainer.addView(tv)

        // Ofrece abrir con otra app inmediatamente
        openWithExternalApp(file)
    }

    /** Lanza el chooser de apps para abrir el archivo */
    private fun openWithExternalApp(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
