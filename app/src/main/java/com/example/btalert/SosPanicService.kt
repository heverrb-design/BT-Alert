package com.example.btalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servicio foreground permanente y liviano para el botón de pánico silencioso SOS.
 *
 * Funciona siempre que la app esté instalada, independientemente del monitoreo BT.
 * Detecta mantener Volume DOWN por 3 segundos y envía 3 SMS con 30s de intervalo.
 * Confirma al usuario con 1 vibración corta y discreta.
 */
class SosPanicService : Service() {

    companion object {
        private const val TAG             = "SosPanicService"
        private const val NOTIF_ID        = 1002
        private const val CHANNEL_ID      = "sos_panic_channel"
        private const val SOS_HOLD_MS     = 3000L
        private const val SMS_INTERVAL_MS = 30_000L
        private const val SMS_COUNT       = 3
        private const val POLL_INTERVAL   = 200L
    }

    private lateinit var sharedPrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // Estado del detector de volumen
    private var lastPolledVolume: Int  = -1
    private var volumePressStartMs: Long = 0L
    private var sosTriggered: Boolean  = false
    private var smsSentCount: Int      = 0

    // Ubicación
    private var lastKnownLocation: Location? = null
    private var locationManager: LocationManager? = null

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SosPanicService onCreate")

        sharedPrefs = try {
            applicationContext.createDeviceProtectedStorageContext()
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            applicationContext.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        }

        startForegroundWithNotification()
        initLocationTracking()
        handler.post(volumePollRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
        Log.d(TAG, "SosPanicService onDestroy")
    }

    // =========================================================
    // NOTIFICACIÓN FOREGROUND (mínima, discreta)
    // =========================================================

    private fun startForegroundWithNotification() {
        try {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "BT Alert SOS",
                NotificationManager.IMPORTANCE_MIN  // Sin sonido, sin popup
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(chan)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.sos_service_running))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setShowWhen(false)
                .setSilent(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground", e)
        }
    }

    // =========================================================
    // POLLING DE VOLUMEN — funciona con pantalla apagada
    // =========================================================

    private val volumePollRunnable = object : Runnable {
        override fun run() {
            val sosEnabled = sharedPrefs.getBoolean(AppConfig.PrefsKeys.SOS_ENABLED, false)

            if (!sosEnabled) {
                lastPolledVolume = -1
                handler.postDelayed(this, POLL_INTERVAL)
                return
            }

            val audioMgr = getSystemService(AUDIO_SERVICE) as AudioManager
            val currentVolume = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (lastPolledVolume != -1) {
                val isVolumeDown = currentVolume < lastPolledVolume

                if (isVolumeDown) {
                    val now = System.currentTimeMillis()
                    if (volumePressStartMs == 0L) {
                        volumePressStartMs = now
                        sosTriggered = false
                        handler.postDelayed(sosTriggerRunnable, SOS_HOLD_MS)
                        Log.d(TAG, "📢 Volume DOWN — temporizador SOS iniciado")
                    }
                } else if (currentVolume > lastPolledVolume) {
                    handler.removeCallbacks(sosTriggerRunnable)
                    volumePressStartMs = 0L
                    sosTriggered = false
                }
            }

            lastPolledVolume = currentVolume
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    private val sosTriggerRunnable = Runnable {
        val now = System.currentTimeMillis()
        if (volumePressStartMs == 0L || sosTriggered) return@Runnable
        if (now - volumePressStartMs < SOS_HOLD_MS) return@Runnable

        sosTriggered = true
        volumePressStartMs = 0L
        smsSentCount = 0

        Log.w(TAG, "🆘 SOS activado — iniciando envío de $SMS_COUNT SMS")

        // Vibración discreta de confirmación (1 pulso de 80ms)
        vibrateConfirmation()

        // Enviar correo Pro si está configurado para pánico manual
        val proEnabled  = sharedPrefs.getBoolean(AppConfig.PrefsKeys.PRO_EMAIL_ENABLED, false)
        val proOnPanic  = sharedPrefs.getBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_PANIC, false)
        if (proEnabled && proOnPanic) {
            val emailIntent = EmailAlertService.buildIntent(applicationContext, "panic").apply {
                lastKnownLocation?.let {
                    putExtra("lat", it.latitude)
                    putExtra("lng", it.longitude)
                }
            }
            startService(emailIntent)
            Log.d(TAG, "📧 EmailAlertService disparado por pánico")
        }

        // Enviar primer SMS inmediatamente, luego 2 más con 30s de intervalo
        sendSosSms()
        handler.postDelayed({ sendSosSms() }, SMS_INTERVAL_MS)
        handler.postDelayed({ sendSosSms() }, SMS_INTERVAL_MS * 2)
    }

    // =========================================================
    // VIBRACIÓN DISCRETA
    // =========================================================

    private fun vibrateConfirmation() {
        try {
            val effect = VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    // =========================================================
    // GPS
    // =========================================================

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            Log.d(TAG, "📍 SOS ubicación: ${location.latitude}, ${location.longitude}")
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
    }

    private fun initLocationTracking() {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "Sin permiso de ubicación")
                return
            }

            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .forEach { provider ->
                    try {
                        locationManager?.getLastKnownLocation(provider)?.let { loc ->
                            if (lastKnownLocation == null ||
                                loc.accuracy < (lastKnownLocation?.accuracy ?: Float.MAX_VALUE)) {
                                lastKnownLocation = loc
                            }
                        }
                    } catch (_: Exception) {}
                }

            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 60_000L, 50f, locationListener
                )
            } catch (_: Exception) {}
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 60_000L, 50f, locationListener
                )
            } catch (_: Exception) {}

            Log.d(TAG, "✅ GPS iniciado en SosPanicService")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando GPS", e)
        }
    }

    // =========================================================
    // ENVÍO SMS
    // =========================================================

    private fun sendSosSms() {
        smsSentCount++
        try {
            val phone   = sharedPrefs.getString(AppConfig.PrefsKeys.SOS_PHONE, "") ?: ""
            val message = sharedPrefs.getString(AppConfig.PrefsKeys.SOS_MESSAGE, "") ?: ""

            if (phone.isEmpty() || message.isEmpty()) {
                Log.w(TAG, "SOS: número o mensaje vacío")
                return
            }

            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
            ).format(Date())

            val locationText = lastKnownLocation?.let { loc ->
                "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
            } ?: getString(R.string.sos_location_unavailable)

            val fullMessage = "$message\n[$smsSentCount/$SMS_COUNT] $timestamp\n$locationText"

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(fullMessage)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            Log.w(TAG, "🆘 SOS SMS $smsSentCount/$SMS_COUNT enviado a $phone")

            // Si fue el último SMS, resetear para permitir nueva activación
            if (smsSentCount >= SMS_COUNT) {
                handler.postDelayed({
                    sosTriggered = false
                    smsSentCount = 0
                    Log.d(TAG, "✅ SOS completado — listo para nueva activación")
                }, 5000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando SOS SMS $smsSentCount", e)
        }
    }
}
