package com.ipn.filemanager.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ipn.filemanager.databinding.ActivityBluetoothBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Ejercicio 2 — Compartir archivos vía Bluetooth
 *
 * Implementa:
 * - Descubrimiento de dispositivos cercanos
 * - Conexión segura servidor/cliente
 * - Envío y recepción de archivos con progreso en tiempo real
 * - Historial de transferencias
 * - Cancelación de transferencias en curso
 * - Verificación de integridad (tamaño de archivo)
 *
 * Arquitectura:
 * - Servidor: AcceptThread (espera conexiones entrantes via RFCOMM)
 * - Cliente: ConnectThread (inicia conexión con dispositivo seleccionado)
 * - Transferencia: ConnectedThread (maneja envío/recepción una vez conectado)
 */
class BluetoothActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBluetoothBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val deviceListAdapter by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) }
    private val devices = mutableListOf<BluetoothDevice>()
    private val transferHistory = mutableListOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    private var selectedFileUri: Uri? = null
    private var selectedFileName = ""
    private var isCancelled = false

    companion object {
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val APP_NAME = "FileManagerIPN"
        const val TAG = "BT_FileManager"
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFileUri = it
            val cursor = contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                selectedFileName = c.getString(nameIdx)
            }
            binding.tvSelectedFile.text = "Archivo: $selectedFileName"
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { }

    // Receiver para descubrimiento de dispositivos
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!devices.contains(it)) {
                            devices.add(it)
                            val name = if (checkBtPermission()) it.name ?: "Desconocido" else "Desconocido"
                            deviceListAdapter.add("$name\n${it.address}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    binding.btnScan.text = "🔍 Buscar Dispositivos"
                    binding.btnScan.isEnabled = true
                    if (devices.isEmpty()) updateStatus("No se encontraron dispositivos")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Bluetooth — Compartir Archivos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Este dispositivo no tiene Bluetooth", Toast.LENGTH_LONG).show()
            finish(); return
        }

        binding.listDevices.adapter = deviceListAdapter
        setupButtons()
        registerReceivers()
        checkAndEnableBluetooth()
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener { startDiscovery() }

        binding.btnPickFile.setOnClickListener { filePicker.launch("*/*") }

        binding.btnSend.setOnClickListener {
            val pos = binding.listDevices.checkedItemPosition
            if (pos < 0) { Toast.makeText(this, "Selecciona un dispositivo", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (selectedFileUri == null) { Toast.makeText(this, "Selecciona un archivo", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            connectToDevice(devices[pos])
        }

        binding.btnReceive.setOnClickListener {
            updateStatus("📥 Esperando conexión entrante...")
            acceptThread?.cancel()
            acceptThread = AcceptThread()
            acceptThread?.start()
        }

        binding.btnCancel.setOnClickListener {
            isCancelled = true
            connectedThread?.cancel()
            updateStatus("❌ Transferencia cancelada")
            binding.progressBar.progress = 0
            binding.tvProgress.text = "0%"
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(discoveryReceiver, filter)
    }

    private fun checkAndEnableBluetooth() {
        if (bluetoothAdapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun startDiscovery() {
        if (!checkBtPermission()) { requestBtPermissions(); return }
        devices.clear()
        deviceListAdapter.clear()
        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()
        binding.btnScan.text = "🔄 Buscando..."
        binding.btnScan.isEnabled = false
        updateStatus("Buscando dispositivos Bluetooth...")
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothAdapter?.cancelDiscovery()
        isCancelled = false
        updateStatus("📡 Conectando con ${if(checkBtPermission()) device.name else device.address}...")
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, isServer: Boolean) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket, isServer)
        connectedThread?.start()
    }

    private fun updateStatus(msg: String) {
        handler.post { binding.tvStatus.text = msg }
    }

    private fun updateProgress(percent: Int, detail: String = "") {
        handler.post {
            binding.progressBar.progress = percent
            binding.tvProgress.text = if (detail.isNotEmpty()) "$percent% — $detail" else "$percent%"
        }
    }

    private fun addToHistory(entry: String) {
        transferHistory.add(0, entry)
        handler.post {
            binding.tvHistory.text = transferHistory.take(10).joinToString("\n")
        }
    }

    private fun checkBtPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else true

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 100)
        }
    }

    // ─── Threads ─────────────────────────────────────────────────────────────

    inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        init {
            try {
                if (checkBtPermission())
                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (e: IOException) { Log.e(TAG, "AcceptThread error", e) }
        }
        override fun run() {
            try {
                val socket = serverSocket?.accept()
                serverSocket?.close()
                socket?.let { manageConnectedSocket(it, true) }
            } catch (e: IOException) { Log.e(TAG, "Accept failed", e) }
        }
        fun cancel() { try { serverSocket?.close() } catch (e: IOException) { } }
    }

    inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null
        init {
            try {
                if (checkBtPermission())
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) { Log.e(TAG, "ConnectThread init error", e) }
        }
        override fun run() {
            try {
                socket?.connect()
                socket?.let { manageConnectedSocket(it, false) }
            } catch (e: IOException) {
                updateStatus("❌ No se pudo conectar")
                try { socket?.close() } catch (e2: IOException) { }
            }
        }
        fun cancel() { try { socket?.close() } catch (e: IOException) { } }
    }

    inner class ConnectedThread(private val socket: BluetoothSocket, private val isServer: Boolean) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        override fun run() {
            if (isServer) receiveFile() else sendFile()
        }

        private fun sendFile() {
            val uri = selectedFileUri ?: return
            try {
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
                val totalSize = bytes.size.toLong()
                updateStatus("📤 Enviando $selectedFileName (${totalSize/1024}KB)...")

                // Header: nombre|tamaño
                val header = "$selectedFileName|$totalSize\n"
                outStream.write(header.toByteArray())
                outStream.flush()

                // Enviar en chunks para mostrar progreso
                val chunkSize = 4096
                var sent = 0
                var i = 0
                while (i < bytes.size && !isCancelled) {
                    val end = minOf(i + chunkSize, bytes.size)
                    outStream.write(bytes, i, end - i)
                    sent += (end - i)
                    i = end
                    val pct = (sent * 100L / totalSize).toInt()
                    updateProgress(pct, "${sent/1024}KB / ${totalSize/1024}KB")
                }
                outStream.flush()

                if (!isCancelled) {
                    updateStatus("✅ Enviado: $selectedFileName")
                    addToHistory("📤 ENVIADO: $selectedFileName (${totalSize/1024}KB)")
                    handler.post { binding.progressBar.progress = 100; binding.tvProgress.text = "100% — Completado" }
                }
            } catch (e: IOException) {
                updateStatus("❌ Error al enviar: ${e.message}")
            } finally {
                try { socket.close() } catch (e: IOException) { }
            }
        }

        private fun receiveFile() {
            try {
                updateStatus("📥 Conectado — recibiendo archivo...")
                // Leer header
                val headerBuf = StringBuilder()
                var b: Int
                while (inStream.read().also { b = it } != -1 && b.toChar() != '\n') {
                    headerBuf.append(b.toChar())
                }
                val parts = headerBuf.toString().split("|")
                if (parts.size < 2) { updateStatus("❌ Header inválido"); return }
                val fileName = parts[0]
                val totalSize = parts[1].toLong()
                updateStatus("📥 Recibiendo $fileName (${totalSize/1024}KB)...")

                val buffer = ByteArray(4096)
                val received = mutableListOf<Byte>()
                var totalRead = 0L
                while (totalRead < totalSize && !isCancelled) {
                    val toRead = minOf(buffer.size.toLong(), totalSize - totalRead).toInt()
                    val n = inStream.read(buffer, 0, toRead)
                    if (n == -1) break
                    for (i in 0 until n) received.add(buffer[i])
                    totalRead += n
                    val pct = (totalRead * 100L / totalSize).toInt()
                    updateProgress(pct, "${totalRead/1024}KB / ${totalSize/1024}KB")
                }

                if (!isCancelled) {
                    // Guardar en Downloads
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS)
                    val outFile = java.io.File(downloadsDir, fileName)
                    outFile.writeBytes(received.toByteArray())

                    // Verificar integridad por tamaño
                    val ok = outFile.length() == totalSize
                    val status = if (ok) "✅ Recibido OK: $fileName" else "⚠️ Tamaño incorrecto (posible corrupción)"
                    updateStatus(status)
                    addToHistory("📥 RECIBIDO: $fileName (${totalSize/1024}KB) — ${if(ok) "OK" else "ERROR"}")
                    handler.post { binding.progressBar.progress = 100; binding.tvProgress.text = "100% — Completado" }
                }
            } catch (e: IOException) {
                updateStatus("❌ Error al recibir: ${e.message}")
            } finally {
                try { socket.close() } catch (e: IOException) { }
            }
        }

        fun cancel() { try { socket.close() } catch (e: IOException) { } }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothAdapter?.cancelDiscovery()
        try { unregisterReceiver(discoveryReceiver) } catch (e: Exception) { }
        acceptThread?.cancel()
        connectThread?.cancel()
        connectedThread?.cancel()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
