package com.ipn.filemanager

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ipn.filemanager.databinding.FragmentFileListBinding
import com.ipn.filemanager.utils.FileUtils
import com.ipn.filemanager.viewmodel.FileViewModel
import java.io.File

/**
 * Fragment principal que muestra el explorador de archivos.
 * Maneja: permisos, lista de archivos, operaciones y navegación.
 */
class FileListFragment : Fragment() {

    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FileViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter

    // Launcher para pedir permiso de almacenamiento
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) viewModel.refresh()
        else showPermissionDeniedDialog()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestPermissions()
        setupRecyclerView()
        observeViewModel()

        // Pull-to-refresh recarga el directorio actual
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    // ── Permisos ──────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: permisos granulares
            val permissions = arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            if (permissions.any {
                    ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
                }) {
                permissionLauncher.launch(permissions)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(perm))
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permiso requerido")
            .setMessage(getString(R.string.permission_rationale))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            onFileClick = { file -> handleFileClick(file) },
            onCopy      = { file -> showDestinationDialog(file, move = false) },
            onMove      = { file -> showDestinationDialog(file, move = true) },
            onRename    = { file -> showRenameDialog(file) },
            onDelete    = { file -> confirmDelete(file) },
            onFavorite  = { file -> toggleFavorite(file) },
            onDetails   = { file -> showDetailsDialog(file) }
        )
        binding.recyclerView.apply {
            adapter = this@FileListFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    // ── Observadores ──────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            adapter.submitList(files)
            binding.swipeRefresh.isRefreshing = false
            // Muestra estado vacío si no hay archivos
            binding.emptyState.visibility =
                if (files.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ── Manejo de clics ───────────────────────────────────────────────────

    private fun handleFileClick(file: File) {
        if (file.isDirectory) {
            viewModel.navigateTo(file)
        } else {
            // Guarda en historial de recientes
            FileUtils.saveRecent(requireContext(), file.absolutePath)
            (requireActivity() as MainActivity).openFile(file)
        }
    }

    // ── Operaciones de archivo ────────────────────────────────────────────

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirm))
            .setMessage(file.name)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                if (FileUtils.deleteFile(file)) {
                    viewModel.refresh()
                    toast(getString(R.string.success_delete))
                } else {
                    toast(getString(R.string.error_operation))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(requireContext()).apply {
            setText(file.name)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.rename))
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    if (FileUtils.renameFile(file, newName)) {
                        viewModel.refresh()
                        toast(getString(R.string.success_rename))
                    } else {
                        toast(getString(R.string.error_operation))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Diálogo para seleccionar carpeta destino al copiar/mover.
     * Muestra una lista de carpetas en el mismo directorio actual.
     */
    private fun showDestinationDialog(file: File, move: Boolean) {
        val currentDir = viewModel.currentDir.value ?: return
        val dirs = currentDir.listFiles()?.filter { it.isDirectory && it != file } ?: emptyList()

        if (dirs.isEmpty()) {
            toast("No hay carpetas destino disponibles en este directorio")
            return
        }

        val names = dirs.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_destination))
            .setItems(names) { _, index ->
                val destDir = dirs[index]
                val success = if (move) {
                    FileUtils.moveFile(file, destDir)
                } else {
                    FileUtils.copyFile(file, destDir)
                }
                if (success) {
                    viewModel.refresh()
                    toast(if (move) getString(R.string.success_move) else getString(R.string.success_copy))
                } else {
                    toast(getString(R.string.error_operation))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleFavorite(file: File) {
        viewModel.toggleFavorite(file)
        toast(getString(R.string.added_favorite))
    }

    private fun showDetailsDialog(file: File) {
        val msg = buildString {
            appendLine("${getString(R.string.size)}: ${FileUtils.formatSize(file.length())}")
            appendLine("${getString(R.string.date_modified)}: ${FileUtils.formatDate(file.lastModified())}")
            appendLine("${getString(R.string.mime_type)}: ${FileUtils.getMimeType(file)}")
            appendLine("${getString(R.string.path)}: ${file.absolutePath}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.file_details))
            .setMessage(msg.trim())
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
