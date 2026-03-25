package com.example.btalert

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat

class PinSettingsActivity : BaseActivity() {

    private lateinit var btnBack:       ImageView
    private lateinit var switchFakeLock: SwitchCompat
    private lateinit var txtCurrentPin: TextView
    private lateinit var labelNewPin:   TextView
    private lateinit var etNewPin:      EditText
    private lateinit var etConfirmPin:  EditText
    private lateinit var btnSavePin:    Button

    // Evita que el listener reaccione mientras cargamos el estado guardado
    private var suppressSwitchListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_settings)

        btnBack        = findViewById(R.id.btnBack)
        switchFakeLock = findViewById(R.id.switchFakeLock)
        txtCurrentPin  = findViewById(R.id.txtCurrentPin)
        labelNewPin    = findViewById(R.id.labelNewPin)
        etNewPin       = findViewById(R.id.etNewPin)
        etConfirmPin   = findViewById(R.id.etConfirmPin)
        btnSavePin     = findViewById(R.id.btnSavePin)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        switchFakeLock.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            setPinFieldsVisible(isChecked)
            AppConfig.editPrefs(this) {
                putBoolean(AppConfig.PrefsKeys.FAKE_LOCK_ENABLED, isChecked)
            }
        }

        btnBack.setOnClickListener { finish() }
        btnSavePin.setOnClickListener { savePin() }

        loadCurrentSettings()
    }

    private fun setPinFieldsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        labelNewPin.visibility  = v
        etNewPin.visibility     = v
        etConfirmPin.visibility = v
        btnSavePin.visibility   = v
    }

    private fun loadCurrentSettings() {
        val prefs      = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled    = prefs.getBoolean(AppConfig.PrefsKeys.FAKE_LOCK_ENABLED, false)
        val currentPin = prefs.getString(AppConfig.PrefsKeys.PIN_CODE, "") ?: ""

        suppressSwitchListener = true
        switchFakeLock.isChecked = enabled
        suppressSwitchListener = false

        setPinFieldsVisible(enabled)

        txtCurrentPin.text = if (currentPin.isNotEmpty())
            getString(R.string.pin_unlock_label) + ": " + "●".repeat(currentPin.length)
        else
            getString(R.string.pin_unlock_label) + ": —"
    }

    private fun savePin() {
        val newPin     = etNewPin.text.toString().trim()
        val confirmPin = etConfirmPin.text.toString().trim()

        if (newPin.length < 4) {
            Toast.makeText(this, R.string.pin_min_digits, Toast.LENGTH_SHORT).show()
            return
        }
        if (newPin != confirmPin) {
            Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
            etConfirmPin.text.clear()
            return
        }

        AppConfig.editPrefs(this, commit = true) {
            putString(AppConfig.PrefsKeys.PIN_CODE, newPin)
            putBoolean(AppConfig.PrefsKeys.FAKE_LOCK_ENABLED, true)
        }

        etNewPin.text.clear()
        etConfirmPin.text.clear()
        loadCurrentSettings()
        Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
    }
}
