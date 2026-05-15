package com.ipn.filemanager

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ipn.filemanager.databinding.FragmentFavoritesBinding
import com.ipn.filemanager.viewmodel.FileViewModel
import java.io.File

/**
 * Fragment que muestra la lista de archivos y carpetas marcados como favoritos.
 * Los datos vienen de Room a través del ViewModel como LiveData.
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FileViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FileAdapter(
            onFileClick = { file ->
                if (file.isDirectory) {
                    viewModel.navigateTo(file)
                    // Navegar de vuelta a la pestaña de archivos
                    (requireActivity() as MainActivity).navigateToFiles()
                } else {
                    (requireActivity() as MainActivity).openFile(file)
                }
            },
            onCopy     = {},
            onMove     = {},
            onRename   = {},
            onDelete   = {},
            onFavorite = { file -> viewModel.removeFavorite(file) },
            onDetails  = {}
        )

        binding.recyclerFavorites.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewModel.favorites.observe(viewLifecycleOwner) { favs ->
            val files = favs.map { File(it.path) }.filter { it.exists() }
            adapter.submitList(files)
            binding.emptyFavorites.visibility =
                if (files.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
