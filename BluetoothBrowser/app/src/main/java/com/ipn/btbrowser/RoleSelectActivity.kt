package com.ipn.btbrowser

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.ipn.btbrowser.client.BrowserActivity
import com.ipn.btbrowser.databinding.ActivityRoleSelectBinding
import com.ipn.btbrowser.server.ServerActivity

/**
 * Pantalla de selección de rol.
 * Ejercicio 3: Dispositivo A = Servidor (tiene Internet)
 *              Dispositivo B = Cliente (navega via BT)
 *
 * Incluye botones para cambiar el tema manualmente (Guinda, Azul, Oscuro)
 * independientemente de la configuración del sistema.
 */
class RoleSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "BT Browser IPN"

        binding.btnServer.setOnClickListener {
            startActivity(Intent(this, ServerActivity::class.java))
        }
        binding.btnClient.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }

        // Cambio manual de tema — independiente del sistema
        binding.btnThemeGuinda.setOnClickListener {
            App.prefs.edit().putString("theme", "guinda").apply()
            recreate()
        }
        binding.btnThemeAzul.setOnClickListener {
            App.prefs.edit().putString("theme", "azul").apply()
            recreate()
        }
        binding.btnThemeDark.setOnClickListener {
            val mode = if (App.prefs.getString("dark","no") == "yes") {
                App.prefs.edit().putString("dark","no").apply()
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                App.prefs.edit().putString("dark","yes").apply()
                AppCompatDelegate.MODE_NIGHT_YES
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}