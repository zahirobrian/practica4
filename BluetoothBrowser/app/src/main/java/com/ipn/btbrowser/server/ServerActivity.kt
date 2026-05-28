package com.ipn.btbrowser.server

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ipn.btbrowser.common.BtProtocol
import com.ipn.btbrowser.databinding.ActivityServerBinding
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

/**
 * Dispositivo A — Servidor Bluetooth
 *
 * Funciones:
 * - Acepta conexiones BT entrantes (RFCOMM)
 * - Recibe solicitudes HTTP del cliente
 * - Las ejecuta localmente (tiene Internet)
 * - Devuelve el contenido al cliente via BT
 * - Implementa caché simple en memoria para URLs repetidas
 * - Prioriza texto sobre imágenes
 *
 * Seguridad:
 * - Usa createRfcommWithServiceRecord (canal seguro)
 * - Cifrado nativo del stack BT del sistema
 */
class ServerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var acceptThread: AcceptThread? = null
    private var clientThread: ClientHandlerThread? = null
    private val urlCache = LinkedHashMap<String, String>(50, 0.75f, true)
    private val MAX_CACHE = 50
    private val logLines = ArrayDeque<String>(200)

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val SERVICE_NAME = "BTBrowserProxy"
        const val TAG = "BTServer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Servidor BT"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        checkInternet()

        binding.btnStartServer.setOnClickListener { startServer() }
        binding.btnStopServer.setOnClickListener { stopServer() }
    }

    private fun checkInternet() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        val hasNet = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        binding.tvInternetStatus.text = if (hasNet) "✅ Conexión a Internet disponible" else "❌ Sin Internet"
        binding.tvInternetStatus.setTextColor(getColor(if (hasNet) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
    }

    private fun startServer() {
        if (!checkBtPermission()) { requestBtPermissions(); return }
        binding.btnStartServer.isEnabled = false
        binding.btnStopServer.isEnabled = true
        binding.tvBtStatus.text = "● Esperando cliente..."
        binding.tvBtStatus.setTextColor(getColor(android.R.color.holo_orange_dark))

        acceptThread?.cancel()
        acceptThread = AcceptThread()
        acceptThread?.start()
        log("Servidor iniciado — esperando conexión BT")
    }

    private fun stopServer() {
        acceptThread?.cancel()
        clientThread?.cancel()
        binding.btnStartServer.isEnabled = true
        binding.btnStopServer.isEnabled = false
        binding.tvBtStatus.text = "● Detenido"
        binding.tvBtStatus.setTextColor(getColor(android.R.color.darker_gray))
        log("Servidor detenido")
    }

    private fun log(msg: String) {
        handler.post {
            logLines.addFirst("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $msg")
            if (logLines.size > 100) logLines.removeLast()
            binding.tvLog.text = logLines.take(30).joinToString("\n")
        }
    }

    private fun setConnected(deviceName: String) {
        handler.post {
            binding.tvBtStatus.text = "● Conectado"
            binding.tvBtStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.tvClientInfo.text = "Cliente: $deviceName"
        }
    }

    /** Fetch HTTP/HTTPS con caché en memoria (LRU simple vía LinkedHashMap) */
    fun fetchUrl(url: String): String {
        urlCache[url]?.let {
            log("CACHÉ HIT: $url")
            return it
        }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "BTBrowserIPN/1.0 Android")
                setRequestProperty("Accept", "text/html,text/plain,*/*")
            }
            val code = conn.responseCode
            log("HTTP $code: $url")
            if (code == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                // Guardar en caché (LRU)
                if (urlCache.size >= MAX_CACHE) urlCache.remove(urlCache.keys.first())
                urlCache[url] = body
                body
            } else "Error HTTP $code"
        } catch (e: Exception) {
            log("ERROR: ${e.message}")
            "Error: ${e.message}"
        }
    }

    // ─── Threads ─────────────────────────────────────────────────────────────

    inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        init {
            try {
                if (checkBtPermission())
                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            } catch (e: IOException) { Log.e(TAG, "AcceptThread init", e) }
        }
        override fun run() {
            try {
                val socket = serverSocket?.accept() ?: return
                serverSocket?.close()
                val name = if (checkBtPermission()) socket.remoteDevice.name ?: "Desconocido" else "Desconocido"
                setConnected(name)
                log("Cliente conectado: $name")
                clientThread?.cancel()
                clientThread = ClientHandlerThread(socket)
                clientThread?.start()
            } catch (e: IOException) { Log.e(TAG, "Accept error", e) }
        }
        fun cancel() { try { serverSocket?.close() } catch (e: IOException) { } }
    }

    inner class ClientHandlerThread(private val socket: BluetoothSocket) : Thread() {
        private val reader = BufferedReader(InputStreamReader(socket.inputStream))
        private val writer = socket.outputStream.bufferedWriter()

        override fun run() {
            try {
                while (!interrupted()) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith(BtProtocol.REQUEST) -> {
                            val url = line.removePrefix(BtProtocol.REQUEST)
                            log("Petición: $url")
                            val content = fetchUrl(url)
                            // Enviar respuesta en chunks de 512 chars
                            writer.write(BtProtocol.responseStart(content.length))
                            writer.flush()
                            content.chunked(512).forEach { chunk ->
                                writer.write(BtProtocol.responseChunk(chunk))
                                writer.flush()
                            }
                            writer.write(BtProtocol.responseEnd())
                            writer.flush()
                            log("Respuesta enviada (${content.length} chars)")
                        }
                        line == BtProtocol.PING -> {
                            writer.write("${BtProtocol.PONG}\n"); writer.flush()
                        }
                    }
                }
            } catch (e: IOException) {
                log("Cliente desconectado: ${e.message}")
                handler.post {
                    binding.tvBtStatus.text = "● Desconectado"
                    binding.tvBtStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    binding.tvClientInfo.text = "Esperando cliente..."
                }
                // Volver a escuchar
                acceptThread = AcceptThread()
                acceptThread?.start()
            } finally {
                try { socket.close() } catch (e: IOException) { }
            }
        }
        fun cancel() { interrupt(); try { socket.close() } catch (e: IOException) { } }
    }

    private fun checkBtPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else true

    private fun requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 100)
    }

    override fun onDestroy() { super.onDestroy(); stopServer() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}