package com.example.btalert

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit

class AlertsSettingsActivity : BaseActivity() {

    private val TAG = "AlertsSettingsActivity"
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnBack:         ImageView
    private lateinit var switchFlash:     SwitchCompat
    private lateinit var switchVibration: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts_settings)

        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        btnBack          = findViewById(R.id.btnBack)
        switchFlash      = findViewById(R.id.switchFlash)
        switchVibration  = findViewById(R.id.switchVibration)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { saveAndExit() }
        })

        loadSavedAlerts()

        btnBack.setOnClickListener { saveAndExit() }

        switchFlash.setOnCheckedChangeListener { _, _ ->
            Log.d(TAG, "Flash alert: ${switchFlash.isChecked}")
        }

        switchVibration.setOnCheckedChangeListener { _, _ ->
            Log.d(TAG, "Vibration alert: ${switchVibration.isChecked}")
        }
    }

    // =========================================================
    // CARGA Y GUARDADO
    // =========================================================

    private fun loadSavedAlerts() {
        switchFlash.isChecked = sharedPrefs.getBoolean(
            AppConfig.PrefsKeys.USE_FLASH_ALERT, true
        )
        switchVibration.isChecked = sharedPrefs.getBoolean(
            AppConfig.PrefsKeys.USE_VIBRATION_ALERT, true
        )
        Log.d(TAG, "Alerts loaded: flash=${switchFlash.isChecked} vibration=${switchVibration.isChecked}")
    }

    private fun saveAlerts() {
        // Guardar en normal storage
        sharedPrefs.edit(commit = true) {
            putBoolean(AppConfig.PrefsKeys.USE_FLASH_ALERT,     switchFlash.isChecked)
            putBoolean(AppConfig.PrefsKeys.USE_VIBRATION_ALERT, switchVibration.isChecked)
        }

        // Guardar en protected storage para que BluetoothMonitorService lo lea tras reboot
        try {
            applicationContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    putBoolean(AppConfig.PrefsKeys.USE_FLASH_ALERT,     switchFlash.isChecked)
                    putBoolean(AppConfig.PrefsKeys.USE_VIBRATION_ALERT, switchVibration.isChecked)
                }
            Log.d(TAG, "Alerts saved: flash=${switchFlash.isChecked} vibration=${switchVibration.isChecked}")
        } catch (e: Exception) {
            Log.w(TAG, "Protected storage no disponible", e)
        }
    }

    private fun saveAndExit() {
        saveAlerts()
        Toast.makeText(this, "✓ ${getString(R.string.settings_saved_toast)}", Toast.LENGTH_SHORT).show()
        finish()
    }
}