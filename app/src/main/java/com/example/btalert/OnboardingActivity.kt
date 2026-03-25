package com.example.btalert

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import android.content.pm.PackageManager
import android.view.View

class OnboardingActivity : BaseActivity() {

    companion object {
        private const val PERM_REQUEST_CODE = 300
        private const val TOTAL_STEPS       = 6
    }

    private var currentStep = 1
    private lateinit var sharedPrefs: SharedPreferences

    // Views
    private lateinit var imgIllustration: ImageView
    private lateinit var txtTitle:        TextView
    private lateinit var txtBody:         TextView
    private lateinit var txtWarning:      TextView
    private lateinit var btnPrimary:      Button
    private lateinit var btnSkip:         Button
    private lateinit var layoutDots:      LinearLayout
    private lateinit var txtStepCounter:  TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        setContentView(R.layout.activity_onboarding)

        imgIllustration = findViewById(R.id.imgIllustration)
        txtTitle        = findViewById(R.id.txtTitle)
        txtBody         = findViewById(R.id.txtBody)
        txtWarning      = findViewById(R.id.txtWarning)
        btnPrimary      = findViewById(R.id.btnPrimary)
        btnSkip         = findViewById(R.id.btnSkip)
        layoutDots      = findViewById(R.id.layoutDots)
        txtStepCounter  = findViewById(R.id.txtStepCounter)

        renderStep(currentStep)
    }

    override fun onResume() {
        super.onResume()
        // Al volver de ajustes del sistema, re-renderizar para actualizar estado del permiso
        renderStep(currentStep)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            renderStep(currentStep)
        }
    }

    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    private fun goToNextStep() {
        if (currentStep < TOTAL_STEPS) {
            currentStep++
            renderStep(currentStep)
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        sharedPrefs.edit {
            putBoolean(AppConfig.PrefsKeys.ONBOARDING_COMPLETED, true)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // =========================================================
    // RENDERIZADO POR PASO
    // =========================================================

    private fun renderStep(step: Int) {
        updateDots(step)
        txtStepCounter.text = "$step / $TOTAL_STEPS"

        when (step) {
            1 -> renderStepWelcome()
            2 -> renderStepOverlay()
            3 -> renderStepBluetooth()
            4 -> renderStepAlarm()
            5 -> renderStepBattery()
            6 -> renderStepDnd()
        }
    }

    // ── Paso 1: Bienvenida ────────────────────────────────────

    private fun renderStepWelcome() {
        imgIllustration.setImageResource(R.drawable.ill_welcome)
        txtTitle.text  = getString(R.string.onboarding_step1_title)
        txtBody.text   = getString(R.string.onboarding_step1_body)
        txtWarning.visibility = View.GONE

        btnPrimary.text = getString(R.string.onboarding_btn_start)
        btnPrimary.setOnClickListener { goToNextStep() }

        btnSkip.text = getString(R.string.onboarding_btn_skip_all)
        btnSkip.setOnClickListener {
            showSkipAllWarning()
        }
    }

    // ── Paso 2: Overlay ───────────────────────────────────────

    private fun renderStepOverlay() {
        imgIllustration.setImageResource(R.drawable.ill_overlay)
        txtTitle.text  = getString(R.string.onboarding_step2_title)
        txtBody.text   = getString(R.string.onboarding_step2_body)

        val granted = Settings.canDrawOverlays(this)
        if (granted) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_permission_granted)
            txtWarning.setTextColor(getColor(R.color.green_check))
            btnPrimary.text = getString(R.string.onboarding_btn_next)
            btnPrimary.setOnClickListener { goToNextStep() }
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_step2_warning)
            txtWarning.setTextColor(getColor(R.color.warning_orange))
            btnPrimary.text = getString(R.string.onboarding_btn_open_settings)
            btnPrimary.setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        btnSkip.text = getString(R.string.onboarding_btn_skip)
        btnSkip.setOnClickListener {
            Toast.makeText(this, R.string.onboarding_step2_skip_toast, Toast.LENGTH_LONG).show()
            goToNextStep()
        }
    }

    // ── Paso 3: Bluetooth + Notificaciones ───────────────────

    private fun renderStepBluetooth() {
        imgIllustration.setImageResource(R.drawable.ill_bluetooth)
        txtTitle.text  = getString(R.string.onboarding_step3_title)
        txtBody.text   = getString(R.string.onboarding_step3_body)

        val allGranted = checkBluetoothPermissions()
        if (allGranted) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_permission_granted)
            txtWarning.setTextColor(getColor(R.color.green_check))
            btnPrimary.text = getString(R.string.onboarding_btn_next)
            btnPrimary.setOnClickListener { goToNextStep() }
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_step3_warning)
            txtWarning.setTextColor(getColor(R.color.warning_orange))
            btnPrimary.text = getString(R.string.onboarding_btn_grant)
            btnPrimary.setOnClickListener { requestBluetoothPermissions() }
        }

        btnSkip.text = getString(R.string.onboarding_btn_skip)
        btnSkip.setOnClickListener {
            Toast.makeText(this, R.string.onboarding_step3_skip_toast, Toast.LENGTH_LONG).show()
            goToNextStep()
        }
    }

    private fun checkBluetoothPermissions(): Boolean =
        PermissionManager.allRuntimeGranted(this)

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            PermissionManager.getRuntimePermissions(),
            PERM_REQUEST_CODE
        )
    }

    // ── Paso 4: Alarma exacta ─────────────────────────────────

    private fun renderStepAlarm() {
        imgIllustration.setImageResource(R.drawable.ill_alarm)
        txtTitle.text  = getString(R.string.onboarding_step4_title)
        txtBody.text   = getString(R.string.onboarding_step4_body)

        val granted = isExactAlarmGranted()
        if (granted) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_permission_granted)
            txtWarning.setTextColor(getColor(R.color.green_check))
            btnPrimary.text = getString(R.string.onboarding_btn_next)
            btnPrimary.setOnClickListener { goToNextStep() }
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_step4_warning)
            txtWarning.setTextColor(getColor(R.color.warning_orange))
            btnPrimary.text = getString(R.string.onboarding_btn_open_settings)
            btnPrimary.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
        }

        btnSkip.text = getString(R.string.onboarding_btn_skip)
        btnSkip.setOnClickListener {
            Toast.makeText(this, R.string.onboarding_step4_skip_toast, Toast.LENGTH_LONG).show()
            goToNextStep()
        }
    }

    private fun isExactAlarmGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return am.canScheduleExactAlarms()
        }
        return true
    }

    // ── Paso 5: Batería / autostart ───────────────────────────

    private fun renderStepBattery() {
        imgIllustration.setImageResource(R.drawable.ill_battery)
        txtTitle.text  = getString(R.string.onboarding_step5_title)
        txtBody.text   = getBatteryMessageForManufacturer()

        val granted = isBatteryOptimizationIgnored()
        if (granted) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_permission_granted)
            txtWarning.setTextColor(getColor(R.color.green_check))
            btnPrimary.text = getString(R.string.onboarding_btn_next)
            btnPrimary.setOnClickListener { goToNextStep() }
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_step5_warning)
            txtWarning.setTextColor(getColor(R.color.warning_orange))
            btnPrimary.text = getString(R.string.onboarding_btn_open_settings)
            btnPrimary.setOnClickListener { openBatterySettings() }
        }

        btnSkip.text = getString(R.string.onboarding_btn_skip)
        btnSkip.setOnClickListener {
            Toast.makeText(this, R.string.onboarding_step5_skip_toast, Toast.LENGTH_LONG).show()
            goToNextStep()
        }
    }

    // ── Paso 6: No Molestar ───────────────────────────────────

    private fun renderStepDnd() {
        imgIllustration.setImageResource(R.drawable.ill_alarm)
        txtTitle.text = getString(R.string.onboarding_step6_title)
        txtBody.text  = getString(R.string.onboarding_step6_body)

        val granted = isDndAccessGranted()
        if (granted) {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_permission_granted)
            txtWarning.setTextColor(getColor(R.color.green_check))
            btnPrimary.text = getString(R.string.onboarding_btn_finish)
            btnPrimary.setOnClickListener { finishOnboarding() }
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = getString(R.string.onboarding_step6_warning)
            txtWarning.setTextColor(getColor(R.color.warning_orange))
            btnPrimary.text = getString(R.string.onboarding_btn_open_settings)
            btnPrimary.setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }

        btnSkip.text = getString(R.string.onboarding_btn_skip)
        btnSkip.setOnClickListener {
            Toast.makeText(this, R.string.onboarding_step6_skip_toast, Toast.LENGTH_LONG).show()
            finishOnboarding()
        }
    }

    private fun isDndAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            return nm.isNotificationPolicyAccessGranted
        }
        return true
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {}
        }
    }

    private fun getBatteryMessageForManufacturer(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") ->
                getString(R.string.onboarding_step5_body_xiaomi, getString(R.string.app_name))
            m.contains("huawei") || m.contains("honor") ->
                getString(R.string.onboarding_step5_body_huawei, getString(R.string.app_name))
            m.contains("samsung") ->
                getString(R.string.onboarding_step5_body_samsung, getString(R.string.app_name))
            m.contains("oppo") || m.contains("realme") ->
                getString(R.string.onboarding_step5_body_oppo, getString(R.string.app_name))
            m.contains("vivo") ->
                getString(R.string.onboarding_step5_body_vivo, getString(R.string.app_name))
            else ->
                getString(R.string.onboarding_step5_body_generic, getString(R.string.app_name))
        }
    }

    // =========================================================
    // OMITIR TODO
    // =========================================================

    private fun showSkipAllWarning() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.onboarding_skip_all_title)
            .setMessage(R.string.onboarding_skip_all_message)
            .setPositiveButton(R.string.onboarding_skip_all_confirm) { _, _ ->
                finishOnboarding()
            }
            .setNegativeButton(R.string.onboarding_skip_all_cancel, null)
            .show()
    }

    // =========================================================
    // DOTS INDICADORES
    // =========================================================

    private fun updateDots(activeStep: Int) {
        layoutDots.removeAllViews()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        for (i in 1..TOTAL_STEPS) {
            val dot = View(this)
            val size = if (i == activeStep) dp8 else dp6
            val params = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp6, 0, dp6, 0)
            }
            dot.layoutParams = params
            dot.background = ContextCompat.getDrawable(
                this,
                if (i == activeStep) R.drawable.dot_active else R.drawable.dot_inactive
            )
            layoutDots.addView(dot)
        }
    }
}
