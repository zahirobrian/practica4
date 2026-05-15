package com.ipn.filemanager

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ipn.filemanager.databinding.ActivityMainBinding
import com.ipn.filemanager.utils.FileUtils
import com.ipn.filemanager.utils.ThemeManager
import com.ipn.filemanager.viewmodel.FileViewModel
import java.io.File

/**
 * Activity principal que aloja los fragments de navegación.
 * Gestiona: toolbar, breadcrumb, BottomNav, cambio de tema y navegación atrás.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: FileViewModel
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica el tema ANTES de inflar la vista
        setTheme(ThemeManager.getThemeResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this)[FileViewModel::class.java]

        setupNavigation()
        observeBreadcrumb()
        setupBackPressed()
    }

    // ── Navegación ────────────────────────────────────────────────────────

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        // Conecta el BottomNav con el Navigation Component
        binding.bottomNav.setupWithNavController(navController)

        // Muestra/oculta el breadcrumb según el fragment activo
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.breadcrumbScroll.visibility =
                if (destination.id == R.id.fileListFragment) android.view.View.VISIBLE
                else android.view.View.GONE
        }
    }

    /** Navega a la pestaña de archivos desde otros fragments */
    fun navigateToFiles() {
        binding.bottomNav.selectedItemId = R.id.nav_files
    }

    // ── Breadcrumb ────────────────────────────────────────────────────────

    private fun observeBreadcrumb() {
        viewModel.currentDir.observe(this) { dir ->
            updateBreadcrumb(dir)
        }
    }

    /**
     * Construye el breadcrumb dinámicamente a partir de la ruta actual.
     * Cada segmento es un TextView clickeable que navega a ese directorio.
     */
    private fun updateBreadcrumb(dir: File) {
        binding.breadcrumbContainer.removeAllViews()

        val parts = dir.absolutePath.split("/").filter { it.isNotEmpty() }
        var currentPath = ""

        parts.forEachIndexed { index, part ->
            currentPath += "/$part"
            val pathSnapshot = currentPath  // captura para el lambda

            val tv = TextView(this).apply {
                text = if (index < parts.size - 1) "$part  ›" else part
                setPadding(8, 0, 8, 0)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                alpha = if (index == parts.size - 1) 1.0f else 0.75f
                isClickable = true
                setOnClickListener {
                    viewModel.navigateTo(File(pathSnapshot))
                    navigateToFiles()
                }
            }
            binding.breadcrumbContainer.addView(tv)
        }

        // Auto-scroll al final del breadcrumb
        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(android.view.View.FOCUS_RIGHT)
        }
    }

    // ── Botón atrás ───────────────────────────────────────────────────────

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Si está en el explorador de archivos, navega al directorio anterior
                if (navController.currentDestination?.id == R.id.fileListFragment) {
                    if (!viewModel.navigateUp()) finish()
                } else {
                    // En otras pestañas, vuelve a archivos
                    navigateToFiles()
                }
            }
        })
    }

    // ── Menú toolbar ──────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_theme_guinda -> {
                ThemeManager.saveTheme(this, ThemeManager.THEME_GUINDA)
                recreate()  // reinicia la Activity con el nuevo tema
                true
            }
            R.id.action_theme_azul -> {
                ThemeManager.saveTheme(this, ThemeManager.THEME_AZUL)
                recreate()
                true
            }
            R.id.action_internal -> {
                viewModel.navigateTo(filesDir)
                navigateToFiles()
                true
            }
            R.id.action_external -> {
                val ext = android.os.Environment.getExternalStorageDirectory()
                viewModel.navigateTo(ext)
                navigateToFiles()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Abrir archivos ────────────────────────────────────────────────────

    /**
     * Decide cómo abrir un archivo:
     * - Tipos soportados (texto, JSON, XML, imágenes): FileViewerActivity
     * - Otros: chooser con apps instaladas del dispositivo
     */
    fun openFile(file: File) {
        when {
            FileUtils.isText(file) || FileUtils.isJson(file) ||
            FileUtils.isXml(file) || FileUtils.isImage(file) -> {
                startActivity(Intent(this, FileViewerActivity::class.java).apply {
                    putExtra("file_path", file.absolutePath)
                })
            }
            else -> openWithExternalApp(file)
        }
    }

    /** Lanza el diálogo inteligente para abrir con otras apps instaladas */
    private fun openWithExternalApp(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, FileUtils.getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this, getString(R.string.no_app_found), android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
