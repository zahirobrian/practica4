package com.ipn.filemanager.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.ipn.filemanager.databinding.ActivitySensorsBinding
import java.util.concurrent.Executor
import kotlin.math.sqrt

/**
 * Ejercicio 1 — Sensores del dispositivo
 *
 * Implementa:
 *  - Acelerómetro: detecta shake para refrescar archivos
 *  - Sensor de luz ambiental: ajusta el tema automáticamente
 *  - Autenticación biométrica: huella dactilar para carpetas protegidas
 *
 * Gestión de batería:
 *  - onPause() desregistra listeners para no consumir batería en background
 *  - Solo activa sensores cuando la pantalla está visible (onResume)
 */
class SensorsActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySensorsBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null

    // Acelerómetro - detección de shake
    private var lastShakeTime = 0L
    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
    private val SHAKE_THRESHOLD = 800
    private val SHAKE_COOLDOWN_MS = 1000L

    // Sensores habilitados
    private var accelEnabled = true
    private var lightEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensorsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Sensores del Dispositivo"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Mostrar disponibilidad de sensores
        binding.tvAccelStatus.text = if (accelerometer != null) "✅ Disponible" else "❌ No disponible"
        binding.tvLightStatus.text = if (lightSensor  != null) "✅ Disponible" else "❌ No disponible"
        binding.tvBioStatus.text   = getBiometricStatus()

        setupSwitches()
        setupBiometric()
    }

    private fun getBiometricStatus(): String {
        val bm = BiometricManager.from(this)
        return when (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS          -> "✅ Disponible"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "❌ Sin hardware"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "⚠️ Sin huella registrada"
            else -> "❌ No disponible"
        }
    }

    private fun setupSwitches() {
        binding.switchAccel.isChecked = accelEnabled
        binding.switchLight.isChecked = lightEnabled

        binding.switchAccel.setOnCheckedChangeListener { _, checked ->
            accelEnabled = checked
            if (checked) {
                accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                binding.tvAccelData.text = "Esperando datos..."
            } else {
                accelerometer?.let { sensorManager.unregisterListener(this, it) }
                binding.tvAccelData.text = "Sensor desactivado"
            }
        }

        binding.switchLight.setOnCheckedChangeListener { _, checked ->
            lightEnabled = checked
            if (checked) {
                lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                binding.tvLightData.text = "Esperando datos..."
            } else {
                lightSensor?.let { sensorManager.unregisterListener(this, it) }
                binding.tvLightData.text = "Sensor desactivado"
            }
        }
    }

    private fun setupBiometric() {
        val executor: Executor = ContextCompat.getMainExecutor(this)

        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    binding.tvBioResult.text = "✅ Autenticación exitosa\nAcceso concedido a carpeta protegida"
                    binding.tvBioResult.setTextColor(getColor(android.R.color.holo_green_dark))
                }
                override fun onAuthenticationFailed() {
                    binding.tvBioResult.text = "❌ Huella no reconocida"
                    binding.tvBioResult.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    binding.tvBioResult.text = "⚠️ Error: $msg"
                    binding.tvBioResult.setTextColor(getColor(android.R.color.holo_orange_dark))
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Carpeta Protegida")
            .setSubtitle("Verifica tu identidad para acceder")
            .setDescription("Usa tu huella dactilar para desbloquear esta carpeta")
            .setNegativeButtonText("Cancelar")
            .build()

        binding.btnBiometric.setOnClickListener {
            binding.tvBioResult.text = "Esperando huella..."
            binding.tvBioResult.setTextColor(getColor(android.R.color.darker_gray))
            prompt.authenticate(promptInfo)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_LIGHT         -> handleLight(event)
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        binding.tvAccelData.text = "X: %.2f m/s²\nY: %.2f m/s²\nZ: %.2f m/s²".format(x, y, z)

        // Detección de shake
        val now = System.currentTimeMillis()
        if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
            val dx = x - lastX; val dy = y - lastY; val dz = z - lastZ
            val speed = sqrt(dx*dx + dy*dy + dz*dz) / ((now - lastShakeTime) / 1000f + 0.001f)
            if (speed > SHAKE_THRESHOLD) {
                lastShakeTime = now
                binding.tvShakeStatus.text = "📳 Shake detectado — Refrescando archivos..."
                Toast.makeText(this, "Shake detectado", Toast.LENGTH_SHORT).show()
            }
        }
        lastX = x; lastY = y; lastZ = z
    }

    private fun handleLight(event: SensorEvent) {
        val lux = event.values[0]
        val desc = when {
            lux < 10   -> "Oscuridad"
            lux < 100  -> "Luz baja (interior)"
            lux < 1000 -> "Luz normal"
            lux < 10000 -> "Luz intensa"
            else -> "Luz solar directa"
        }
        binding.tvLightData.text = "%.1f lux — %s".format(lux, desc)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        // Solo registrar sensores que estén habilitados
        if (accelEnabled) accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (lightEnabled) lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar todos para ahorrar batería en background
        sensorManager.unregisterListener(this)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
