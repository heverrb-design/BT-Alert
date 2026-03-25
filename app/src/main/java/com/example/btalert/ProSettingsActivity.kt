package com.example.btalert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ProSettingsActivity : BaseActivity() {

    companion object {
        private const val REQ_PERMISSIONS = 501
    }

    private lateinit var prefs: SharedPreferences

    private lateinit var btnBack:           ImageView
    private lateinit var switchEmail:       SwitchCompat
    private lateinit var layoutEmailConfig: LinearLayout
    private lateinit var etSmtpHost:        EditText
    private lateinit var etSmtpPort:        EditText
    private lateinit var etSmtpUser:        EditText
    private lateinit var etSmtpPass:        EditText
    private lateinit var etEmailTo:         EditText
    private lateinit var etEmailSubject:    EditText
    private lateinit var etEmailBody:       EditText
    private lateinit var cbFrontCam:        CheckBox
    private lateinit var cbRearCam:         CheckBox
    private lateinit var cbAudio:           CheckBox
    private lateinit var cbOnPanic:         CheckBox
    private lateinit var cbOnBtAlarm:       CheckBox
    private lateinit var btnGmailHelp:      Button
    private lateinit var btnTest:           Button
    private lateinit var btnSave:           Button
    private lateinit var txtPermStatus:     TextView

    private var suppressSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pro_settings)

        prefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        btnBack        = findViewById(R.id.btnBack)
        switchEmail    = findViewById(R.id.switchEmailAlert)
        layoutEmailConfig = findViewById(R.id.layoutEmailConfig)
        etSmtpHost     = findViewById(R.id.etSmtpHost)
        etSmtpPort     = findViewById(R.id.etSmtpPort)
        etSmtpUser     = findViewById(R.id.etSmtpUser)
        etSmtpPass     = findViewById(R.id.etSmtpPass)
        etEmailTo      = findViewById(R.id.etEmailTo)
        etEmailSubject = findViewById(R.id.etEmailSubject)
        etEmailBody    = findViewById(R.id.etEmailBody)
        cbFrontCam     = findViewById(R.id.cbFrontCam)
        cbRearCam      = findViewById(R.id.cbRearCam)
        cbAudio        = findViewById(R.id.cbAudio)
        cbOnPanic      = findViewById(R.id.cbOnPanic)
        cbOnBtAlarm    = findViewById(R.id.cbOnBtAlarm)
        btnGmailHelp   = findViewById(R.id.btnGmailHelp)
        btnTest        = findViewById(R.id.btnTestEmail)
        btnSave        = findViewById(R.id.btnSaveEmail)
        txtPermStatus  = findViewById(R.id.txtPermStatus)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        switchEmail.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitch) return@setOnCheckedChangeListener
            if (isChecked && !hasRequiredPermissions()) {
                requestRequiredPermissions()
                suppressSwitch = true
                switchEmail.isChecked = false
                suppressSwitch = false
                return@setOnCheckedChangeListener
            }
            setFieldsVisible(isChecked)
            prefs.edit().putBoolean(AppConfig.PrefsKeys.PRO_EMAIL_ENABLED, isChecked).apply()
        }

        btnBack.setOnClickListener { finish() }

        btnGmailHelp.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://myaccount.google.com/apppasswords")))
        }

        btnTest.setOnClickListener { sendTestEmail() }
        btnSave.setOnClickListener { saveSettings() }

        loadCurrentSettings()
        updatePermissionStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            updatePermissionStatus()
            if (hasRequiredPermissions()) {
                switchEmail.isChecked = true
                setFieldsVisible(true)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            REQ_PERMISSIONS
        )
    }

    private fun updatePermissionStatus() {
        val camOk   = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        txtPermStatus.text = buildString {
            append(if (camOk) "✅" else "⚠️").append(" ${getString(R.string.pro_perm_camera)}\n")
            append(if (audioOk) "✅" else "⚠️").append(" ${getString(R.string.pro_perm_audio)}")
        }
    }

    private fun setFieldsVisible(visible: Boolean) {
        layoutEmailConfig.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun loadCurrentSettings() {
        val enabled = prefs.getBoolean(AppConfig.PrefsKeys.PRO_EMAIL_ENABLED, false)
        suppressSwitch = true
        switchEmail.isChecked = enabled
        suppressSwitch = false
        setFieldsVisible(enabled)

        etSmtpHost.setText(prefs.getString(AppConfig.PrefsKeys.PRO_SMTP_HOST, "smtp.gmail.com"))
        etSmtpPort.setText(prefs.getString(AppConfig.PrefsKeys.PRO_SMTP_PORT, "587"))
        etSmtpUser.setText(prefs.getString(AppConfig.PrefsKeys.PRO_SMTP_USER, ""))
        etSmtpPass.setText(prefs.getString(AppConfig.PrefsKeys.PRO_SMTP_PASS, ""))
        etEmailTo.setText(prefs.getString(AppConfig.PrefsKeys.PRO_EMAIL_TO, ""))
        etEmailSubject.setText(prefs.getString(AppConfig.PrefsKeys.PRO_EMAIL_SUBJECT,
            "🆘 BT Alert Emergency"))
        etEmailBody.setText(prefs.getString(AppConfig.PrefsKeys.PRO_EMAIL_BODY, ""))

        cbFrontCam.isChecked  = prefs.getBoolean(AppConfig.PrefsKeys.PRO_ATTACH_FRONT_CAM, true)
        cbRearCam.isChecked   = prefs.getBoolean(AppConfig.PrefsKeys.PRO_ATTACH_REAR_CAM, true)
        cbAudio.isChecked     = prefs.getBoolean(AppConfig.PrefsKeys.PRO_ATTACH_AUDIO, true)
        cbOnPanic.isChecked   = prefs.getBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_PANIC, true)
        cbOnBtAlarm.isChecked = prefs.getBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_BT_ALARM, true)
    }

    private fun saveSettings() {
        val host = etSmtpHost.text.toString().trim()
        val user = etSmtpUser.text.toString().trim()
        val pass = etSmtpPass.text.toString().trim()
        val to   = etEmailTo.text.toString().trim()

        if (switchEmail.isChecked && (host.isEmpty() || user.isEmpty() || pass.isEmpty() || to.isEmpty())) {
            Toast.makeText(this, R.string.pro_fields_required, Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().apply {
            putBoolean(AppConfig.PrefsKeys.PRO_EMAIL_ENABLED, switchEmail.isChecked)
            putString(AppConfig.PrefsKeys.PRO_SMTP_HOST, host)
            putString(AppConfig.PrefsKeys.PRO_SMTP_PORT, etSmtpPort.text.toString().trim())
            putString(AppConfig.PrefsKeys.PRO_SMTP_USER, user)
            putString(AppConfig.PrefsKeys.PRO_SMTP_PASS, pass)
            putString(AppConfig.PrefsKeys.PRO_EMAIL_TO, to)
            putString(AppConfig.PrefsKeys.PRO_EMAIL_SUBJECT, etEmailSubject.text.toString().trim())
            putString(AppConfig.PrefsKeys.PRO_EMAIL_BODY, etEmailBody.text.toString().trim())
            putBoolean(AppConfig.PrefsKeys.PRO_ATTACH_FRONT_CAM, cbFrontCam.isChecked)
            putBoolean(AppConfig.PrefsKeys.PRO_ATTACH_REAR_CAM, cbRearCam.isChecked)
            putBoolean(AppConfig.PrefsKeys.PRO_ATTACH_AUDIO, cbAudio.isChecked)
            putBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_PANIC, cbOnPanic.isChecked)
            putBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_BT_ALARM, cbOnBtAlarm.isChecked)
            apply()
        }

        Toast.makeText(this, R.string.pro_saved, Toast.LENGTH_SHORT).show()
    }

    private fun sendTestEmail() {
        saveSettings()
        val intent = EmailAlertService.buildIntent(this, "test")
        startService(intent)
        Toast.makeText(this, R.string.pro_test_sent, Toast.LENGTH_LONG).show()
    }
}
