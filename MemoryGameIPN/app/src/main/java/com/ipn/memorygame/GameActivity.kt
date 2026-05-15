package com.ipn.memorygame

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout
import com.ipn.memorygame.databinding.ActivityGameBinding
import com.ipn.memorygame.model.GameState
import com.ipn.memorygame.utils.GameFileManager
import com.ipn.memorygame.utils.SoundManager
import com.ipn.memorygame.utils.ThemeManager

/**
 * Activity principal del juego de memoria.
 *
 * Mecánica: el jugador voltea cartas de a dos. Si hacen par, quedan visibles.
 * Si no, se voltean de nuevo. El juego termina cuando se encuentran todos los pares.
 *
 * Niveles:
 * - Fácil: tablero 4×4 (8 pares de emojis)
 * - Difícil: tablero 6×6 (18 pares de emojis)
 *
 * Puntuación: pares encontrados × 100, penalización de 5 pts por movimiento fallido.
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding

    // Estado del juego
    private var level = 0
    private var gridSize = 4
    private var board = mutableListOf<String>()
    private var flipped = mutableListOf<Boolean>()
    private var matched = mutableListOf<Boolean>()
    private var firstCard = -1
    private var moves = 0
    private var score = 0
    private var timeElapsed = 0L
    private var pairsFound = 0
    private var totalPairs = 0
    private var isChecking = false
    private val moveHistory = mutableListOf<String>()

    // Cronómetro
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private val timerRunnable = object : Runnable {
        override fun run() {
            timeElapsed++
            updateTimerDisplay()
            handler.postDelayed(this, 1000)
        }
    }

    // Emojis para los pares
    private val emojisAll = listOf(
        "🍎","🍊","🍋","🍇","🍓","🍑","🍒","🥝",
        "🐶","🐱","🐭","🐹","🐸","🦊","🐼","🐨",
        "⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.getResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setTitleTextColor(0xFFFFFFFF.toInt())

        SoundManager.init(this)

        level = intent.getIntExtra("level", 0)

        // Verificar si hay partida para cargar
        val loadPath = intent.getStringExtra("load_path")
        if (loadPath != null) {
            loadFromFile(loadPath)
        } else {
            startNewGame()
        }

        binding.btnSave.setOnClickListener { showSaveDialog() }
        binding.btnNewGame.setOnClickListener { startNewGame() }
    }

    // ── Nuevo juego ───────────────────────────────────────────────────────

    private fun startNewGame() {
        stopTimer()
        gridSize = if (level == 0) 4 else 6
        totalPairs = (gridSize * gridSize) / 2
        pairsFound = 0
        moves = 0
        score = 0
        timeElapsed = 0L
        firstCard = -1
        isChecking = false
        moveHistory.clear()

        supportActionBar?.title = if (level == 0) "Memory — Fácil" else "Memory — Difícil"
        binding.tvLevel.text = "Nivel: ${if (level == 0) "Fácil (4×4)" else "Difícil (6×6)"}"

        // Genera el tablero mezclado
        val emojis = emojisAll.take(totalPairs)
        board = (emojis + emojis).shuffled().toMutableList()
        flipped = MutableList(board.size) { false }
        matched = MutableList(board.size) { false }

        buildGrid()
        updateStats()
        startTimer()
    }

    // ── Construcción del grid ─────────────────────────────────────────────

    private fun buildGrid() {
        val grid = binding.gridGame
        grid.removeAllViews()
        grid.columnCount = gridSize
        grid.rowCount = gridSize

        val cardSize = calculateCardSize()

        board.indices.forEach { i ->
            val cardView = layoutInflater.inflate(R.layout.item_card, grid, false)
            val params = GridLayout.LayoutParams().apply {
                width  = cardSize
                height = cardSize
                columnSpec = GridLayout.spec(i % gridSize, 1f)
                rowSpec    = GridLayout.spec(i / gridSize, 1f)
                setMargins(4, 4, 4, 4)
            }
            cardView.layoutParams = params

            val tvBack  = cardView.findViewById<TextView>(R.id.tvBack)
            val tvFront = cardView.findViewById<TextView>(R.id.tvFront)
            tvFront.text = board[i]

            cardView.setOnClickListener { onCardClick(i, tvBack, tvFront) }
            grid.addView(cardView)
        }
    }

    private fun calculateCardSize(): Int {
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val padding = (16 * dm.density).toInt()
        val margin  = (8  * dm.density).toInt()
        return (screenWidth - padding * 2 - margin * gridSize * 2) / gridSize
    }

    // ── Lógica de click en carta ──────────────────────────────────────────

    private fun onCardClick(index: Int, tvBack: TextView, tvFront: TextView) {
        if (isChecking) return
        if (matched[index]) return
        if (flipped[index]) return

        // Voltear carta
        SoundManager.playFlip()
        flipCard(index, tvBack, tvFront, faceUp = true)
        flipped[index] = true

        if (firstCard == -1) {
            firstCard = index
        } else {
            // Segunda carta — verificar par
            moves++
            isChecking = true

            if (board[firstCard] == board[index]) {
                // ¡Par encontrado!
                SoundManager.playMatch()
                matched[firstCard] = true
                matched[index] = true
                pairsFound++
                score += 100
                moveHistory.add("Par encontrado: ${board[index]} (pos $firstCard y $index)")
                firstCard = -1
                isChecking = false
                updateStats()

                if (pairsFound == totalPairs) {
                    stopTimer()
                    SoundManager.playWin()
                    handler.postDelayed({ showWinDialog() }, 600)
                }
            } else {
                // No es par — voltear de nuevo
                SoundManager.playFail()
                score = maxOf(0, score - 5)
                moveHistory.add("Fallido: ${board[firstCard]} vs ${board[index]}")
                val firstIndex = firstCard
                val firstBack  = binding.gridGame.getChildAt(firstIndex)?.findViewById<TextView>(R.id.tvBack)
                val firstFront = binding.gridGame.getChildAt(firstIndex)?.findViewById<TextView>(R.id.tvFront)

                handler.postDelayed({
                    flipCard(firstIndex, firstBack, firstFront, faceUp = false)
                    flipCard(index, tvBack, tvFront, faceUp = false)
                    flipped[firstIndex] = false
                    flipped[index] = false
                    firstCard = -1
                    isChecking = false
                    updateStats()
                }, 900)
            }
            updateStats()
        }
    }

    /** Muestra u oculta la cara de la carta con animación */
    private fun flipCard(index: Int, tvBack: TextView?, tvFront: TextView?, faceUp: Boolean) {
        val back  = tvBack  ?: binding.gridGame.getChildAt(index)?.findViewById(R.id.tvBack)
        val front = tvFront ?: binding.gridGame.getChildAt(index)?.findViewById(R.id.tvFront)
        back  ?: return
        front ?: return

        val duration = 150L
        val pivot = back.width / 2f

        back.pivotX  = pivot
        front.pivotX = pivot

        if (faceUp) {
            back.animate().rotationY(90f).setDuration(duration).withEndAction {
                back.visibility  = View.GONE
                front.visibility = View.VISIBLE
                front.rotationY  = -90f
                front.animate().rotationY(0f).setDuration(duration).start()
            }.start()
        } else {
            front.animate().rotationY(90f).setDuration(duration).withEndAction {
                front.visibility = View.GONE
                back.visibility  = View.VISIBLE
                back.rotationY   = -90f
                back.animate().rotationY(0f).setDuration(duration).start()
            }.start()
        }

        // Color verde si ya fue emparejada
        if (matched[index]) {
            front.setBackgroundColor(0xFF4CAF50.toInt())
        }
    }

    // ── Stats y cronómetro ────────────────────────────────────────────────

    private fun updateStats() {
        binding.tvScore.text = "Puntuación: $score"
        binding.tvMoves.text = "Movimientos: $moves"
    }

    private fun updateTimerDisplay() {
        val m = timeElapsed / 60
        val s = timeElapsed % 60
        binding.tvTime.text = "Tiempo: %d:%02d".format(m, s)
    }

    private fun startTimer() {
        if (timerRunning) return
        timerRunning = true
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    // ── Guardar partida ───────────────────────────────────────────────────

    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.activity_list_item, null)
        val container  = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val etTag = EditText(this).apply {
            hint = getString(R.string.tag_hint)
            setText(if (moveHistory.size > 0) "intento${moves}" else "partida1")
        }

        val tvFormat = TextView(this).apply {
            text = "Formato de guardado:"
            setPadding(0, 16, 0, 8)
        }

        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("TXT", "JSON", "XML").forEach { fmt ->
            rg.addView(RadioButton(this).apply {
                text = fmt
                isChecked = fmt == "JSON"
                setPadding(0, 0, 24, 0)
            })
        }

        container.addView(etTag)
        container.addView(tvFormat)
        container.addView(rg)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_game))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val tag = etTag.text.toString().trim().ifBlank { "partida" }
                val fmt = (rg.findViewById<RadioButton>(rg.checkedRadioButtonId))?.text?.toString() ?: "JSON"
                saveGame(tag, fmt)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveGame(tag: String, format: String) {
        val state = buildGameState(tag, format)
        val ok = GameFileManager.save(this, state, format)
        Toast.makeText(
            this,
            if (ok) getString(R.string.game_saved) else getString(R.string.error_save),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildGameState(tag: String = "", format: String = "JSON"): GameState {
        return GameState(
            level = level,
            board = board.toList(),
            flipped = flipped.toList(),
            matched = matched.toList(),
            score = score,
            moves = moves,
            timeElapsed = timeElapsed,
            moveHistory = moveHistory.toList(),
            customSettings = mapOf(
                "theme" to ThemeManager.get(this),
                "level" to if (level == 0) "easy" else "hard",
                "format" to format,
                "gridSize" to gridSize.toString()
            ),
            tag = tag,
            savedAt = System.currentTimeMillis()
        )
    }

    // ── Cargar partida ────────────────────────────────────────────────────

    private fun loadFromFile(path: String) {
        val state = GameFileManager.load(java.io.File(path))
        if (state == null) {
            Toast.makeText(this, getString(R.string.error_load), Toast.LENGTH_SHORT).show()
            startNewGame()
            return
        }

        stopTimer()
        level     = state.level
        gridSize  = if (level == 0) 4 else 6
        board     = state.board.toMutableList()
        flipped   = state.flipped.toMutableList()
        matched   = state.matched.toMutableList()
        score     = state.score
        moves     = state.moves
        timeElapsed = state.timeElapsed
        totalPairs  = (gridSize * gridSize) / 2
        pairsFound  = matched.count { it } / 2
        moveHistory.addAll(state.moveHistory)
        firstCard   = -1
        isChecking  = false

        supportActionBar?.title = if (level == 0) "Memory — Fácil" else "Memory — Difícil"
        binding.tvLevel.text = "Nivel: ${if (level == 0) "Fácil (4×4)" else "Difícil (6×6)"}"

        buildGrid()

        // Restaura el estado visual de las cartas
        board.indices.forEach { i ->
            if (matched[i] || flipped[i]) {
                val tvBack  = binding.gridGame.getChildAt(i)?.findViewById<TextView>(R.id.tvBack)
                val tvFront = binding.gridGame.getChildAt(i)?.findViewById<TextView>(R.id.tvFront)
                tvBack?.visibility  = View.GONE
                tvFront?.visibility = View.VISIBLE
                if (matched[i]) tvFront?.setBackgroundColor(0xFF4CAF50.toInt())
            }
        }

        updateStats()
        updateTimerDisplay()
        if (pairsFound < totalPairs) startTimer()

        Toast.makeText(this, getString(R.string.game_loaded), Toast.LENGTH_SHORT).show()
    }

    // ── Diálogo de victoria ───────────────────────────────────────────────

    private fun showWinDialog() {
        val m = timeElapsed / 60
        val s = timeElapsed % 60
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.you_win))
            .setMessage(
                "⭐ Puntuación: $score\n" +
                "🕐 Tiempo: %d:%02d\n".format(m, s) +
                "👆 Movimientos: $moves\n\n" +
                "¿Qué deseas hacer?"
            )
            .setPositiveButton("Guardar partida") { _, _ -> showSaveDialog() }
            .setNeutralButton("Nueva partida") { _, _ -> startNewGame() }
            .setNegativeButton("Salir") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        stopTimer()
        super.onDestroy()
    }
}
