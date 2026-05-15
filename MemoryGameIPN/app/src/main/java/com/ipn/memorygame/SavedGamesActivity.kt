package com.ipn.memorygame

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ipn.memorygame.databinding.ActivitySavedGamesBinding
import com.ipn.memorygame.databinding.ItemSavedGameBinding
import com.ipn.memorygame.model.SavedGameMeta
import com.ipn.memorygame.utils.GameFileManager
import com.ipn.memorygame.utils.ThemeManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Muestra la lista de partidas guardadas con sus metadatos.
 * Permite: cargar, ver el contenido del archivo y eliminar partidas.
 */
class SavedGamesActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySavedGamesBinding
    private lateinit var adapter: SavedGamesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivitySavedGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.saved_games)
        binding.toolbar.setTitleTextColor(0xFFFFFFFF.toInt())

        adapter = SavedGamesAdapter(
            onLoad   = { meta -> loadGame(meta) },
            onView   = { meta -> viewFile(meta) },
            onDelete = { meta -> confirmDelete(meta) }
        )

        binding.recyclerSaves.layoutManager = LinearLayoutManager(this)
        binding.recyclerSaves.adapter = adapter

        loadSaves()
    }

    override fun onResume() {
        super.onResume()
        loadSaves()
    }

    private fun loadSaves() {
        val saves = GameFileManager.listSaves(this)
        adapter.submitList(saves)
        binding.emptyState.visibility = if (saves.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadGame(meta: SavedGameMeta) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra("load_path", meta.filePath)
        })
    }

    private fun viewFile(meta: SavedGameMeta) {
        startActivity(Intent(this, FileViewActivity::class.java).apply {
            putExtra("file_path", meta.filePath)
            putExtra("title", meta.fileName)
        })
    }

    private fun confirmDelete(meta: SavedGameMeta) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar partida")
            .setMessage("¿Eliminar '${meta.tag}'?")
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (GameFileManager.delete(meta.filePath)) {
                    Toast.makeText(this, "Partida eliminada", Toast.LENGTH_SHORT).show()
                    loadSaves()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ── Adapter ───────────────────────────────────────────────────────────────

class SavedGamesAdapter(
    private val onLoad: (SavedGameMeta) -> Unit,
    private val onView: (SavedGameMeta) -> Unit,
    private val onDelete: (SavedGameMeta) -> Unit
) : RecyclerView.Adapter<SavedGamesAdapter.VH>() {

    private var items = listOf<SavedGameMeta>()

    fun submitList(list: List<SavedGameMeta>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemSavedGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSavedGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val meta = items[position]
        with(holder.binding) {
            tvFormat.text = meta.format
            tvTag.text    = meta.tag.ifBlank { meta.fileName }
            tvScore.text  = "${meta.score} pts"

            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                .format(Date(meta.savedAt))
            val m = meta.timeElapsed / 60
            val s = meta.timeElapsed % 60
            tvMeta.text = "${meta.level}  •  %d:%02d  •  $date".format(m, s)

            btnLoad.setOnClickListener   { onLoad(meta) }
            btnView.setOnClickListener   { onView(meta) }
            btnDelete.setOnClickListener { onDelete(meta) }
        }
    }
}
