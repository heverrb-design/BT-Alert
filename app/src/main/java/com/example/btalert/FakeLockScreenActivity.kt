package com.example.btalert

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla falsa de bloqueo que se muestra encima del lockscreen real
 * cuando el monitoreo está activo.
 *
 * - Muestra hora y fecha como el lockscreen del sistema
 * - Oculta completamente la barra de estado y navegación
 * - El teclado PIN aparece al tocar la pantalla 5 veces seguidas
 * - Solo el PIN correcto la cierra; un PIN incorrecto la oculta de nuevo
 * - Se registra como singleInstance para no poder sacarla con el gestor de apps
 */
class FakeLockScreenActivity : BaseActivity() {

    companion object {
        private const val TAG             = "FakeLockScreen"
        private const val TAPS_TO_SHOW_PIN = 5
        private const val PIN_MAX_LENGTH   = 6
        private const val TAP_RESET_MS     = 3000L
    }

    private lateinit var txtTime:      TextView
    private lateinit var txtDate:      TextView
    private lateinit var pinContainer: LinearLayout
    private lateinit var txtPinPrompt: TextView
    private lateinit var pinDots:      LinearLayout

    private val handler     = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    private var tapCount    = 0
    private var pinEntered  = StringBuilder()
    private var correctPin  = ""

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrar sobre la pantalla de bloqueo real y encender pantalla
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_fake_lock_screen)

        txtTime      = findViewById(R.id.txtTime)
        txtDate      = findViewById(R.id.txtDate)
        pinContainer = findViewById(R.id.pinContainer)
        txtPinPrompt = findViewById(R.id.txtPinPrompt)
        pinDots      = findViewById(R.id.pinDots)

        // Leer PIN guardado
        correctPin = AppConfig.getPrefs(this)
            .getString(AppConfig.PrefsKeys.PIN_CODE, "") ?: ""

        txtPinPrompt.text = getString(R.string.fake_lock_pin_prompt)

        setupFullscreen()
        setupBackPress()
        setupTapToRevealPin()
        setupPinButtons()
        handler.post(clockRunnable)
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        correctPin = AppConfig.getPrefs(this)
            .getString(AppConfig.PrefsKeys.PIN_CODE, "") ?: ""
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        } else {
            // Perdió el foco — relanzarse si el monitoreo sigue activo
            val prefs = AppConfig.getPrefs(this)
            val isMonitoringActive = prefs.getBoolean(
                AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false
            )
            if (isMonitoringActive) {
                handler.postDelayed({
                    if (!isFinishing) {
                        try {
                            startActivity(
                                Intent(this, FakeLockScreenActivity::class.java).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    )
                                }
                            )
                        } catch (_: Exception) {}
                    }
                }, 200L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // =========================================================
    // FULLSCREEN — sin barra de estado ni navegación
    // =========================================================

    private fun setupFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { ctrl ->
                    ctrl.hide(
                        WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                    )
                    ctrl.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } catch (_: Exception) {}
    }

    // =========================================================
    // BACK PRESS — bloqueado
    // =========================================================

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // No hacer nada — la pantalla falsa no se puede cerrar con Back
            }
        })
    }

    // =========================================================
    // RELOJ
    // =========================================================

    private fun updateClock() {
        val now = Date()
        txtTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        txtDate.text = SimpleDateFormat(
            getString(R.string.fake_lock_date_format),
            Locale.getDefault()
        ).format(now)
    }

    // =========================================================
    // TAP × 5 PARA MOSTRAR PIN
    // =========================================================

    private val tapResetRunnable = Runnable {
        tapCount = 0
    }

    private fun setupTapToRevealPin() {
        // Toca la pantalla 5 veces en menos de 3 segundos para mostrar el teclado
        val root = findViewById<View>(R.id.fakeLockRoot)
        root?.setOnClickListener {
            if (pinContainer.visibility == View.VISIBLE) return@setOnClickListener

            tapCount++
            handler.removeCallbacks(tapResetRunnable)
            handler.postDelayed(tapResetRunnable, TAP_RESET_MS)

            if (tapCount >= TAPS_TO_SHOW_PIN) {
                tapCount = 0
                showPinPad()
            }
        }
    }

    // =========================================================
    // TECLADO PIN
    // =========================================================

    private fun showPinPad() {
        pinEntered.clear()
        updatePinDots()
        pinContainer.visibility = View.VISIBLE
    }

    private fun hidePinPad() {
        pinContainer.visibility = View.GONE
        pinEntered.clear()
        updatePinDots()
    }

    private fun setupPinButtons() {
        val numButtons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        numButtons.forEach { (id, digit) ->
            findViewById<Button>(id)?.setOnClickListener {
                if (pinEntered.length < PIN_MAX_LENGTH) {
                    pinEntered.append(digit)
                    updatePinDots()
                    if (pinEntered.length == correctPin.length) {
                        validatePin()
                    }
                }
            }
        }

        // Borrar último dígito
        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            if (pinEntered.isNotEmpty()) {
                pinEntered.deleteCharAt(pinEntered.length - 1)
                updatePinDots()
            }
        }

        // Cancelar → ocultar teclado
        findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            hidePinPad()
        }
    }

    private fun updatePinDots() {
        pinDots.removeAllViews()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8  = (8  * resources.displayMetrics.density).toInt()
        val maxDots = maxOf(correctPin.length, PIN_MAX_LENGTH)

        for (i in 0 until maxDots) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dp12, dp12).apply {
                setMargins(dp8, 0, dp8, 0)
            }
            dot.layoutParams = params
            dot.background = if (i < pinEntered.length) {
                // Punto lleno (dígito ingresado)
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(0xFFFFFFFF.toInt())
                drawable
            } else {
                // Punto vacío
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(0x44FFFFFF.toInt())
                drawable
            }
            pinDots.addView(dot)
        }
    }

    private fun validatePin() {
        if (pinEntered.toString() == correctPin) {
            // PIN correcto → cerrar pantalla falsa y detener monitoreo
            handler.postDelayed({
                val stopIntent = android.content.Intent(this, BluetoothMonitorService::class.java)
                    .apply { action = "ACTION_STOP_SERVICE" }
                stopService(stopIntent)

                AppConfig.editPrefs(this, commit = true) {
                    putBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
                    putBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, false)
                }

                finish()
            }, 200)
        } else {
            // PIN incorrecto → vibrar, limpiar y ocultar teclado
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(android.os.VibratorManager::class.java)
                        ?.defaultVibrator
                        ?.vibrate(android.os.VibrationEffect.createOneShot(
                            300, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        ))
                } else {
                    @Suppress("DEPRECATION")
                    (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                        ?.vibrate(android.os.VibrationEffect.createOneShot(
                            300, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                        ))
                }
            } catch (_: Exception) {}

            Toast.makeText(this, R.string.fake_lock_pin_wrong, Toast.LENGTH_SHORT).show()
            handler.postDelayed({ hidePinPad() }, 500)
        }
    }
}
