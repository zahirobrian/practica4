package com.ipn.filemanager

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.ipn.filemanager.databinding.ItemFileBinding
import com.ipn.filemanager.utils.FileUtils
import java.io.File

/**
 * Adapter para el RecyclerView de archivos y carpetas.
 * Usa ListAdapter con DiffCallback para actualizaciones eficientes.
 * Muestra miniaturas para imágenes (con caché Glide) e iconos emoji para el resto.
 */
class FileAdapter(
    private val onFileClick: (File) -> Unit,
    private val onCopy: (File) -> Unit,
    private val onMove: (File) -> Unit,
    private val onRename: (File) -> Unit,
    private val onDelete: (File) -> Unit,
    private val onFavorite: (File) -> Unit,
    private val onDetails: (File) -> Unit
) : ListAdapter<File, FileAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = getItem(position)
        with(holder.binding) {

            // Nombre del archivo
            tvName.text = file.name

            // Metadatos (tamaño y fecha o cantidad de elementos si es carpeta)
            tvMeta.text = if (file.isDirectory) {
                val count = try { file.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
                "$count ${root.context.getString(R.string.elements)}"
            } else {
                "${FileUtils.formatSize(file.length())}  •  ${FileUtils.formatDate(file.lastModified())}"
            }

            // Miniatura de imagen con caché Glide o emoji como texto
            if (FileUtils.isImage(file)) {
                // Carga la miniatura con caché en disco (optimiza rendimiento)
                Glide.with(imgThumbnail.context)
                    .load(file)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.THUMBNAIL)
                    .thumbnail(0.1f)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(imgThumbnail)
            } else {
                // Cancela cualquier carga previa de Glide y muestra emoji
                Glide.with(imgThumbnail.context).clear(imgThumbnail)
                imgThumbnail.setImageDrawable(null)
                // Usamos el TextView de nombre para mostrar el emoji como prefijo
                tvName.text = "${FileUtils.getFileEmoji(file)}  ${file.name}"
            }

            // Click principal: abrir archivo o entrar a carpeta
            root.setOnClickListener { onFileClick(file) }

            // Menú contextual con opciones de operaciones
            btnMore.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    menuInflater.inflate(R.menu.menu_file_options, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_copy     -> { onCopy(file); true }
                            R.id.action_move     -> { onMove(file); true }
                            R.id.action_rename   -> { onRename(file); true }
                            R.id.action_delete   -> { onDelete(file); true }
                            R.id.action_favorite -> { onFavorite(file); true }
                            R.id.action_details  -> { onDetails(file); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.absolutePath == newItem.absolutePath

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.lastModified() == newItem.lastModified() &&
                        oldItem.length() == newItem.length()
        }
    }
}
