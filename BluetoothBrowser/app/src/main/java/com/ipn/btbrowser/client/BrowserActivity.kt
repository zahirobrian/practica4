package com.ipn.btbrowser.client

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ipn.btbrowser.common.BtProtocol
import com.ipn.btbrowser.databinding.ActivityBrowserBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * Dispositivo B — Cliente / Navegador Bluetooth
 *
 * Funciones:
 * - Se conecta al servidor (Dispositivo A) via BT
 * - Envía solicitudes de URL al servidor
 * - Recibe contenido y lo renderiza como texto
 * - Historial de navegación (adelante/atrás)
 * - Marcadores/favoritos en SharedPreferences
 * - Reconexión automática si se pierde la conexión
 * - Barra de progreso durante carga
 * - Modo incógnito (no guarda historial)
 *
 * NOTA: Este dispositivo NO tiene Internet directo.
 * Toda la navegación se hace a través del servidor BT.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var btSocket: BluetoothSocket? = null
    private var readerThread: ReaderThread? = null
    private val history = ArrayDeque<String>()
    private var historyIndex = -1
    private var isIncognito = false
    private val favorites = mutableListOf<String>()
    private var isConnected = false
    private val devices = mutableListOf<BluetoothDevice>()

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val TAG = "BTClient"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Navegador BT"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        loadFavorites()
        setupUI()
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener { showDevicePicker() }

        binding.btnGo.setOnClickListener { navigateTo(binding.etUrl.text.toString().trim()) }

        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigateTo(binding.etUrl.text.toString().trim())
                true
            } else false
        }

        binding.btnBack.setOnClickListener {
            if (historyIndex > 0) {
                historyIndex--
                navigateTo(history[historyIndex], addToHistory = false)
            }
        }

        binding.btnForward.setOnClickListener {
            if (historyIndex < history.size - 1) {
                historyIndex++
                navigateTo(history[historyIndex], addToHistory = false)
            }
        }

        binding.btnRefresh.setOnClickListener {
            if (historyIndex >= 0) navigateTo(history[historyIndex], addToHistory = false)
        }
    }

    private fun showDevicePicker() {
        if (!checkBtPermission()) { requestBtPermissions(); return }
        devices.clear()
        val paired = bluetoothAdapter?.bondedDevices ?: emptySet()
        devices.addAll(paired)
        if (devices.isEmpty()) {
            Toast.makeText(this, "No hay dispositivos vinculados", Toast.LENGTH_SHORT).show()
            return
        }
        val names = devices.map {
            if (checkBtPermission()) (it.name ?: "Desconocido") + "\n" + it.address
            else it.address
        }
        AlertDialog.Builder(this)
            .setTitle("Selecciona el Servidor")
            .setItems(names.toTypedArray()) { _, i -> connectTo(devices[i]) }
            .show()
    }

    private fun connectTo(device: BluetoothDevice) {
        setStatus("Conectando...", android.R.color.holo_orange_dark)
        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                if (!checkBtPermission()) return@Thread
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                btSocket = socket
                readerThread?.interrupt()
                readerThread = ReaderThread(socket)
                readerThread?.start()
                isConnected = true
                val name = if (checkBtPermission()) device.name ?: "Servidor" else "Servidor"
                setStatus("● Conectado a $name", android.R.color.holo_green_dark)
                handler.post { binding.btnConnect.text = "Cambiar" }
            } catch (e: IOException) {
                Log.e(TAG, "Connect error", e)
                setStatus("● Error de conexión", android.R.color.holo_red_dark)
                handler.postDelayed({ if (!isConnected) connectTo(device) }, 3000) // reconexión automática
            }
        }.start()
    }

    private fun navigateTo(input: String, addToHistory: Boolean = true) {
        if (!isConnected) {
            Toast.makeText(this, "No conectado al servidor", Toast.LENGTH_SHORT).show()
            return
        }
        val url = normalizeUrl(input)
        binding.etUrl.setText(url)

        if (addToHistory && !isIncognito) {
            while (history.size > historyIndex + 1) history.removeLast()
            history.addLast(url)
            historyIndex = history.size - 1
        }

        handler.post {
            binding.progressLoad.visibility = View.VISIBLE
            binding.tvStatus.text = "Cargando $url..."
            binding.tvPageTitle.text = ""
            binding.tvPageContent.text = ""
        }

        Thread {
            try {
                val out = btSocket?.outputStream?.bufferedWriter() ?: return@Thread
                out.write(BtProtocol.request(url))
                out.flush()
            } catch (e: IOException) {
                handler.post { binding.tvStatus.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun normalizeUrl(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") -> "https://$input"
            else -> "https://www.google.com/search?q=${input.replace(" ", "+")}"
        }
    }

    private fun loadFavorites() {
        val saved = getSharedPreferences("btbrowser_prefs", Context.MODE_PRIVATE)
            .getString("favorites", "") ?: ""
        if (saved.isNotEmpty()) favorites.addAll(saved.split("|"))
    }

    private fun setStatus(msg: String, colorRes: Int) {
        handler.post {
            binding.tvBtIndicator.text = msg
            binding.tvBtIndicator.setTextColor(getColor(colorRes))
        }
    }

    // ─── Reader Thread — procesa respuestas del servidor ─────────────────────

    inner class ReaderThread(private val socket: BluetoothSocket) : Thread() {
        private val reader = BufferedReader(InputStreamReader(socket.inputStream))
        private val sb = StringBuilder()
        private var receiving = false

        override fun run() {
            try {
                while (!interrupted()) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith(BtProtocol.RESPONSE_START) -> {
                            sb.clear()
                            receiving = true
                        }
                        line.startsWith(BtProtocol.RESPONSE_CHUNK) && receiving -> {
                            sb.append(line.removePrefix(BtProtocol.RESPONSE_CHUNK))
                        }
                        line == BtProtocol.RESPONSE_END && receiving -> {
                            receiving = false
                            val content = sb.toString()
                            handler.post { renderContent(content) }
                        }
                        line.startsWith(BtProtocol.ERROR) -> {
                            handler.post {
                                binding.tvStatus.text = line.removePrefix(BtProtocol.ERROR)
                                binding.progressLoad.visibility = View.GONE
                            }
                        }
                        line == BtProtocol.PONG -> { /* heartbeat ok */ }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Reader error", e)
                isConnected = false
                setStatus("● Desconectado", android.R.color.holo_red_dark)
            }
        }
    }

    private fun renderContent(html: String) {
        // Extrae título y texto plano del HTML para mostrarlo
        val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Sin título"

        // Eliminar tags HTML básicos para texto plano
        val text = html
            .replace(Regex("<script[\s\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\s\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\s+"), " ")
            .trim()
            .take(5000) // limitar para rendimiento

        binding.tvPageTitle.text = title
        binding.tvPageContent.text = if (text.length > 50) text else "(Contenido no legible en modo texto)"
        binding.tvStatus.text = "Listo — $title"
        binding.progressLoad.visibility = View.GONE
    }

    private fun checkBtPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else true

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        readerThread?.interrupt()
        try { btSocket?.close() } catch (e: IOException) { }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}