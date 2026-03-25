package com.example.btalert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SosSettingsActivity : BaseActivity() {

    companion object {
        private const val REQ_SOS_PERMISSIONS = 401
    }

    private lateinit var btnBack:       ImageView
    private lateinit var switchSos:     SwitchCompat
    private lateinit var labelPhone:    TextView
    private lateinit var etPhone:       EditText
    private lateinit var labelMessage:  TextView
    private lateinit var etMessage:     EditText
    private lateinit var btnSave:       Button
    private lateinit var txtStatus:     TextView

    private var suppressSwitchListener = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_settings)

        btnBack      = findViewById(R.id.btnBack)
        switchSos    = findViewById(R.id.switchSos)
        labelPhone   = findViewById(R.id.labelPhone)
        etPhone      = findViewById(R.id.etPhone)
        labelMessage = findViewById(R.id.labelMessage)
        etMessage    = findViewById(R.id.etMessage)
        btnSave      = findViewById(R.id.btnSaveSos)
        txtStatus    = findViewById(R.id.txtSosStatus)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        switchSos.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchListener) return@setOnCheckedChangeListener
            if (isChecked && !hasSmsPermission()) {
                suppressSwitchListener = true
                switchSos.isChecked = false
                suppressSwitchListener = false
                requestRequiredPermissions()
                return@setOnCheckedChangeListener
            }
            setSosFieldsVisible(isChecked)
            AppConfig.editPrefs(this) {
                putBoolean(AppConfig.PrefsKeys.SOS_ENABLED, isChecked)
            }
        }

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { saveSettings() }

        // Si SMS ya está concedido pero falta ubicación, pedirla
        if (hasSmsPermission() && !hasLocationPermission()) {
            requestLocationPermission()
        }

        loadCurrentSettings()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SOS_PERMISSIONS) {
            val smsIdx = permissions.indexOf(Manifest.permission.SEND_SMS)
            val smsGranted = smsIdx >= 0 &&
                    grantResults.getOrNull(smsIdx) == PackageManager.PERMISSION_GRANTED

            if (smsGranted) {
                switchSos.isChecked = true
                setSosFieldsVisible(true)
                if (!hasLocationPermission()) {
                    Toast.makeText(this, R.string.sos_location_optional, Toast.LENGTH_LONG).show()
                }
            } else if (smsIdx >= 0) {
                // Solo mostrar error si se pidió SMS y fue denegado
                Toast.makeText(this, R.string.sos_permission_denied, Toast.LENGTH_LONG).show()
            }
            // Si solo se pidió ubicación (SMS ya tenía), actualizar estado
            if (smsIdx < 0) loadCurrentSettings()
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(Manifest.permission.SEND_SMS)
        if (!hasLocationPermission()) perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_SOS_PERMISSIONS)
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQ_SOS_PERMISSIONS
        )
    }

    private fun setSosFieldsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        labelPhone.visibility   = v
        etPhone.visibility      = v
        labelMessage.visibility = v
        etMessage.visibility    = v
        btnSave.visibility      = v
    }

    private fun loadCurrentSettings() {
        val prefs   = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(AppConfig.PrefsKeys.SOS_ENABLED, false)
        val phone   = prefs.getString(AppConfig.PrefsKeys.SOS_PHONE, "") ?: ""
        val message = prefs.getString(AppConfig.PrefsKeys.SOS_MESSAGE, "") ?: ""

        suppressSwitchListener = true
        switchSos.isChecked = enabled
        suppressSwitchListener = false

        setSosFieldsVisible(enabled)

        if (phone.isNotEmpty()) etPhone.setText(phone)
        if (message.isNotEmpty()) etMessage.setText(message)

        val locationStatus = if (hasLocationPermission()) "✅" else "⚠️"
        txtStatus.text = if (enabled && phone.isNotEmpty())
            "$locationStatus " + getString(R.string.sos_status_active, phone)
        else
            getString(R.string.sos_status_inactive)
    }

    private fun saveSettings() {
        val phone   = etPhone.text.toString().trim()
        val message = etMessage.text.toString().trim()

        if (phone.isEmpty()) {
            Toast.makeText(this, R.string.sos_phone_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (message.isEmpty()) {
            Toast.makeText(this, R.string.sos_message_required, Toast.LENGTH_SHORT).show()
            return
        }

        AppConfig.editPrefs(this, commit = true) {
            putBoolean(AppConfig.PrefsKeys.SOS_ENABLED, true)
            putString(AppConfig.PrefsKeys.SOS_PHONE, phone)
            putString(AppConfig.PrefsKeys.SOS_MESSAGE, message)
        }

        switchSos.isChecked = true
        loadCurrentSettings()
        Toast.makeText(this, R.string.sos_saved, Toast.LENGTH_SHORT).show()
    }
}
