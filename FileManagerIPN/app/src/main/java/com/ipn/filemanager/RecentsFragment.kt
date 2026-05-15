package com.ipn.filemanager

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ipn.filemanager.databinding.FragmentRecentsBinding
import com.ipn.filemanager.utils.FileUtils
import java.io.File

/**
 * Fragment que muestra el historial de archivos abiertos recientemente.
 * El historial se persiste en SharedPreferences (máx. 20 entradas).
 */
class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FileAdapter(
            onFileClick = { file -> (requireActivity() as MainActivity).openFile(file) },
            onCopy     = {},
            onMove     = {},
            onRename   = {},
            onDelete   = {},
            onFavorite = {},
            onDetails  = {}
        )

        binding.recyclerRecents.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Carga recientes desde SharedPreferences filtrando los que ya no existen
        val recentFiles = FileUtils.getRecents(requireContext())
            .map { File(it) }
            .filter { it.exists() && it.isFile }

        adapter.submitList(recentFiles)

        binding.emptyRecents.visibility =
            if (recentFiles.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
