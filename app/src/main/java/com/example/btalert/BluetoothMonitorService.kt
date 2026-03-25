package com.example.btalert

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class BluetoothMonitorService : Service() {

    companion object {
        private const val NOTIF_ID           = 1001
        private const val TAG                = "BluetoothMonitorService"
        private const val ALARM_REQUEST_CODE = 123
    }

    // Contexto con el locale correcto de la app, usado en todos los getString
    private val localizedCtx: Context
        get() = (application as MyApplication).getLocalizedContext()

    private lateinit var sharedPrefs:  SharedPreferences
    private lateinit var audioManager: AudioManager
    private var cameraManager:  CameraManager?         = null
    private var cameraId:       String?                = null
    private var vibrator:       Vibrator?              = null
    private var mediaPlayer:    MediaPlayer?           = null
    private var wakeLock:       PowerManager.WakeLock? = null

    private var cpuAlarmWakeLock:    PowerManager.WakeLock? = null
    private var screenAlarmWakeLock: PowerManager.WakeLock? = null

    private var audioFocusRequest: AudioFocusRequest? = null

    private var previousDndFilter: Int = -1  // guarda el filtro DND antes de la alarma

    // ── SOS — mantener Volume UP 3 segundos ─────────────────

    private val handler        = Handler(Looper.getMainLooper())
    private var isAlarmPlaying = false
    private var isFlashOn      = false
    private var isVolumeLocked = false

    private var powerButtonReceiver:      BroadcastReceiver? = null
    private var dynamicBluetoothReceiver: BroadcastReceiver? = null
    private var stopAlarmReceiver:        BroadcastReceiver? = null

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: service created")

        sharedPrefs = try {
            applicationContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "onCreate: protected storage falló", e)
            try {
                applicationContext.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e2: Exception) {
                Log.e(TAG, "onCreate: ambos storages fallaron — abortando", e2)
                stopSelf()
                return
            }
        }

        // Sincronización normal → protected (Direct Boot)
        try {
            val normalPrefs  = applicationContext
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val macNormal    = normalPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)
            val macProtected = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)

            if (macNormal != null && macNormal != macProtected) {
                Log.w(TAG, "⚠️ Sincronizando normal → protected storage")
                sharedPrefs.edit().apply {
                    putString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC,
                        macNormal)
                    putString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME,
                        normalPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, null))
                    putBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE,
                        normalPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false))
                    putInt(AppConfig.PrefsKeys.START_HOUR,
                        normalPrefs.getInt(AppConfig.PrefsKeys.START_HOUR, 0))
                    putInt(AppConfig.PrefsKeys.START_MINUTE,
                        normalPrefs.getInt(AppConfig.PrefsKeys.START_MINUTE, 0))
                    putInt(AppConfig.PrefsKeys.END_HOUR,
                        normalPrefs.getInt(AppConfig.PrefsKeys.END_HOUR, 23))
                    putInt(AppConfig.PrefsKeys.END_MINUTE,
                        normalPrefs.getInt(AppConfig.PrefsKeys.END_MINUTE, 59))
                    putBoolean(AppConfig.PrefsKeys.USE_FLASH_ALERT,
                        normalPrefs.getBoolean(AppConfig.PrefsKeys.USE_FLASH_ALERT, true))
                    putBoolean(AppConfig.PrefsKeys.USE_VIBRATION_ALERT,
                        normalPrefs.getBoolean(AppConfig.PrefsKeys.USE_VIBRATION_ALERT, true))
                    commit()
                }
                Log.d(TAG, "✅ Prefs sincronizadas")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sincronización de prefs omitida (Direct Boot aún activo)", e)
        }

        // Notificación de foreground con localizedCtx
        try {
            val chan = NotificationChannel(
                MyApplication.MONITOR_CHANNEL_ID,
                localizedCtx.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)

            val notification = NotificationCompat.Builder(this, MyApplication.MONITOR_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(localizedCtx.getString(R.string.app_name))
                .setContentText(localizedCtx.getString(R.string.notification_starting))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error crítico notificación", e)
        }

        audioManager  = getSystemService(AudioManager::class.java)
        vibrator      = getSystemService(Vibrator::class.java)
        cameraManager = getSystemService(CameraManager::class.java)
        try {
            cameraManager?.cameraIdList?.let { if (it.isNotEmpty()) cameraId = it[0] }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cameraId", e)
        }

        acquireWakeLock()
        registerDynamicBluetoothReceiver()
        registerPowerButtonReceiver()
        registerStopAlarmReceiver()

        updateNotification(sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        try {
            val immediateAlert = intent?.getBooleanExtra("immediate_alert", false) ?: false
            if (immediateAlert) {
                Log.w(TAG, "🚨 IMMEDIATE ALERT from boot!")
                handler.postDelayed({ startAlarmSequence() }, 1000)
            }

            val isActive = sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
            updateNotification(isActive)

            intent?.action?.let { action ->
                when (action) {
                    "ACTION_SOUND_ALARM" -> {
                        Log.w(TAG, "⚠️ ACTION_SOUND_ALARM received")
                        startAlarmSequence()
                    }
                    "ACTION_STOP_ALARM" -> {
                        Log.d(TAG, "🛑 ACTION_STOP_ALARM received")
                        stopAlarm()
                        setMonitoringState(false)
                    }
                    "ACTION_STOP_SERVICE" -> {
                        Log.d(TAG, "⏹️ ACTION_STOP_SERVICE received")
                        stopAlarm()
                        setMonitoringState(false)
                        stopSelf()
                    }
                    "ACTION_TEST_ALARM" -> startAlarmSequence()

                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(
                                    BluetoothDevice.EXTRA_DEVICE,
                                    BluetoothDevice::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        handleBluetoothEvent(action, device)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: error", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        // stopAlarm ya fue llamado por el receiver — solo ejecutar si aún está activo
        if (isAlarmPlaying) stopAlarm()
        releaseAudioFocus()
        unregisterPowerButtonReceiver()
        releaseWakeLock()
        releaseAlarmWakeLocks()
        try {
            stopAlarmReceiver?.let { unregisterReceiver(it) }
            stopAlarmReceiver = null
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering stopAlarmReceiver", e)
        }
        try {
            dynamicBluetoothReceiver?.let { unregisterReceiver(it) }
            dynamicBluetoothReceiver = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    // =========================================================
    // WAKELOCK — Servicio base
    // =========================================================

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BTAlert::MonitorWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    // =========================================================
    // WAKELOCK — Alarma (dos niveles)
    // =========================================================

    @Suppress("DEPRECATION")
    private fun acquireAlarmWakeLocks() {
        releaseAlarmWakeLocks()
        val pm = getSystemService(POWER_SERVICE) as PowerManager

        cpuAlarmWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BTAlert::CpuAlarmLock"
        ).also {
            it.setReferenceCounted(false)
            it.acquire(10 * 60 * 1000L)
            Log.d(TAG, "✅ CpuAlarmWakeLock adquirido")
        }

        try {
            screenAlarmWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "BTAlert::ScreenAlarmLock"
            ).also {
                it.setReferenceCounted(false)
                it.acquire(30 * 1000L)
                Log.d(TAG, "✅ ScreenAlarmWakeLock adquirido (30s)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ScreenAlarmWakeLock no disponible: ${e.message}")
        }
    }

    private fun releaseAlarmWakeLocks() {
        try { cpuAlarmWakeLock?.let    { if (it.isHeld) it.release() } } catch (_: Exception) {}
        try { screenAlarmWakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        cpuAlarmWakeLock    = null
        screenAlarmWakeLock = null
    }

    // =========================================================
    // WAKELOCK — Renovación periódica durante alarma
    // =========================================================

    private val wakeLockRenewalRunnable = object : Runnable {
        override fun run() {
            if (!isAlarmPlaying) return
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                cpuAlarmWakeLock?.let { if (it.isHeld) it.release() }
                cpuAlarmWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BTAlert::CpuAlarmLock"
                ).also { lock ->
                    lock.setReferenceCounted(false)
                    lock.acquire(10 * 60 * 1000L)
                    Log.d(TAG, "🔄 CpuAlarmWakeLock renovado")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error renovando WakeLock", e)
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    // =========================================================
    // AUDIOFOCUS
    // =========================================================

    private fun requestAudioFocus(): Boolean {
        return try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "AudioFocus cambió: $focusChange")
                    if (isAlarmPlaying &&
                        (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                                focusChange == AudioManager.AUDIOFOCUS_LOSS)
                    ) {
                        Log.w(TAG, "⚠️ AudioFocus perdido durante alarma — reintentando")
                        handler.postDelayed({
                            requestAudioFocus()
                            playAlarmAudioRobust()
                        }, 500)
                    }
                }
                .build()

            val result  = audioManager.requestAudioFocus(audioFocusRequest!!)
            val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "AudioFocus: ${if (granted) "✅ GRANTED" else "❌ DENIED — continuando"}")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando AudioFocus", e)
            true
        }
    }

    private fun releaseAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
            Log.d(TAG, "AudioFocus liberado")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando AudioFocus", e)
        }
    }

    // =========================================================
    // BLUETOOTH EVENTS
    // =========================================================

    private fun handleBluetoothEvent(action: String?, device: BluetoothDevice?) {
        Log.d(TAG, "handleBluetoothEvent: action=$action, device=${device?.address}")

        val isActive = sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
        if (!isActive) { Log.d(TAG, "monitoreo inactivo — ignorando"); return }

        val targetMac = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)
        if (targetMac == null) { Log.d(TAG, "sin MAC configurada — ignorando"); return }

        if (device?.address != targetMac) {
            Log.d(TAG, "MAC no coincide (${device?.address} vs $targetMac)")
            return
        }

        if (!isWithinMonitoringSchedule()) {
            Log.d(TAG, "⚠️ Fuera del horario — ignorando")
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent  = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.d(TAG, "Dispositivo conectado — cancelando alarma")
                alarmManager.cancel(alarmIntent)
                stopAlarm()
                updateNotification(true)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.w(TAG, "⚠️ Dispositivo desconectado — iniciando alarma")
                // Marcar ALARM_PLAYING inmediatamente para que MainActivity
                // deje de luchar con FakeLockScreen durante el delay de 3s
                sharedPrefs.edit().putBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, true).commit()
                handler.postDelayed({ startAlarmSequence() }, 3000)

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                System.currentTimeMillis() + 4000,
                                alarmIntent
                            )
                        } else {
                            Log.w(TAG, "Exact alarms no permitidas — usando inexacta")
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                System.currentTimeMillis() + 4000,
                                alarmIntent
                            )
                        }
                    } else {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 4000,
                            alarmIntent
                        )
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException scheduling alarm — usando inexacta", e)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 4000,
                        alarmIntent
                    )
                }

                updateNotification(true, isAlert = true)
            }
        }
    }

    // =========================================================
    // HORARIO
    // =========================================================

    private fun isWithinMonitoringSchedule(): Boolean {
        val startHour   = sharedPrefs.getInt(AppConfig.PrefsKeys.START_HOUR,   0)
        val startMinute = sharedPrefs.getInt(AppConfig.PrefsKeys.START_MINUTE, 0)
        val endHour     = sharedPrefs.getInt(AppConfig.PrefsKeys.END_HOUR,     23)
        val endMinute   = sharedPrefs.getInt(AppConfig.PrefsKeys.END_MINUTE,   59)

        val cal     = Calendar.getInstance()
        val current = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start   = startHour * 60 + startMinute
        val end     = endHour   * 60 + endMinute

        Log.d(TAG, "Horario: $startHour:$startMinute–$endHour:$endMinute | " +
                "Actual: ${cal.get(Calendar.HOUR_OF_DAY)}:${cal.get(Calendar.MINUTE)}")

        return if (start <= end) current in start..end
        else current >= start || current <= end
    }

    // =========================================================
    // ALARMA
    // =========================================================

    private fun startAlarmSequence() {
        if (!isWithinMonitoringSchedule()) {
            Log.d(TAG, "⚠️ Fuera del horario — NO iniciando alarma")
            return
        }
        if (isAlarmPlaying) {
            Log.d(TAG, "Alarm already playing — skipping")
            return
        }
        if (!sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)) {
            Log.d(TAG, "Monitoreo inactivo — NO iniciando alarma")
            return
        }

        Log.w(TAG, "🚨 STARTING ALARM SEQUENCE")
        isAlarmPlaying = true
        sharedPrefs.edit().putBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, true).commit()

        acquireAlarmWakeLocks()
        handler.postDelayed(wakeLockRenewalRunnable, 30_000L)

        // Mantener FakeLockScreen siempre en primer plano mientras suena la alarma
        handler.post(fakeLockPersistRunnable)

        // Enviar correo Pro si está configurado para alarma BT
        val proEnabled   = sharedPrefs.getBoolean(AppConfig.PrefsKeys.PRO_EMAIL_ENABLED, false)
        val proOnBtAlarm = sharedPrefs.getBoolean(AppConfig.PrefsKeys.PRO_SEND_ON_BT_ALARM, false)
        if (proEnabled && proOnBtAlarm) {
            startService(EmailAlertService.buildIntent(applicationContext, "bt_alarm"))
            Log.d(TAG, "📧 EmailAlertService disparado por alarma BT")
        }

        try {
            requestAudioFocus()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    // Guardar el filtro actual para restaurarlo cuando pare la alarma
                    previousDndFilter = nm.currentInterruptionFilter
                    // INTERRUPTION_FILTER_ALL desactiva el modo No Molestar
                    // permitiendo que la alarma suene al máximo volumen sin restricciones
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    Log.d(TAG, "✅ DND desactivado — INTERRUPTION_FILTER_ALL (anterior: $previousDndFilter)")
                } else {
                    Log.w(TAG, "⚠️ Sin permiso ACCESS_NOTIFICATION_POLICY — DND no modificado")
                }
            }
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                maxVol,
                AudioManager.FLAG_SHOW_UI
            )
            isVolumeLocked = true
            handler.post(volumeLockRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando audio", e)
        }

        if (sharedPrefs.getBoolean(AppConfig.PrefsKeys.USE_FLASH_ALERT, true)) {
            handler.post(flashRunnable)
        }
        if (sharedPrefs.getBoolean(AppConfig.PrefsKeys.USE_VIBRATION_ALERT, true)) {
            handler.post(vibrationRunnable)
        }
        handler.postDelayed({ playAlarmAudioRobust() }, 300)
        updateNotification(true, isAlert = true)

        Log.d(TAG, "startAlarmSequence: completado")
    }

    private val fakeLockPersistRunnable = object : Runnable {
        override fun run() {
            if (!isAlarmPlaying) return

            val fakeLockEnabled = sharedPrefs.getBoolean(
                AppConfig.PrefsKeys.FAKE_LOCK_ENABLED, false
            )
            val pinSet = sharedPrefs.getString(
                AppConfig.PrefsKeys.PIN_CODE, ""
            )?.isNotEmpty() == true

            if (fakeLockEnabled && pinSet) {
                try {
                    applicationContext.startActivity(
                        Intent(applicationContext, FakeLockScreenActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
                        }
                    )
                } catch (_: Exception) {}
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private val volumeLockRunnable = object : Runnable {
        override fun run() {
            if (!isVolumeLocked || !isAlarmPlaying) return
            try {
                val max     = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                if (current < max) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        max,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in volumeLockRunnable", e)
            }
            handler.postDelayed(this, 500)
        }
    }

    private val flashRunnable = object : Runnable {
        override fun run() {
            if (!isAlarmPlaying || cameraId == null || cameraManager == null) return
            try {
                isFlashOn = !isFlashOn
                cameraManager?.setTorchMode(cameraId!!, isFlashOn)
            } catch (e: Exception) {
                Log.e(TAG, "Error in flashRunnable", e)
            }
            handler.postDelayed(this, 300)
        }
    }

    private val vibrationRunnable = object : Runnable {
        override fun run() {
            if (!isAlarmPlaying) return
            try {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        1000,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in vibrationRunnable", e)
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun playAlarmAudioRobust() {
        try { mediaPlayer?.release() } catch (e: Exception) {
            Log.w(TAG, "Error releasing mp", e)
        }
        mediaPlayer = null

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            val afd = resources.openRawResourceFd(R.raw.alerta_fuerte)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                prepare()
                start()
            }
            afd.close()
            Log.d(TAG, "Audio iniciado desde recurso propio")
        } catch (e: Exception) {
            Log.e(TAG, "Error con archivo propio — usando respaldo", e)
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(this@BluetoothMonitorService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Audio iniciado desde URI del sistema")
            } catch (ex: Exception) {
                Log.e(TAG, "playAlarmAudioRobust: Critical error", ex)
            }
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm: deteniendo...")
        isAlarmPlaying = false
        isVolumeLocked = false
        sharedPrefs.edit().putBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, false).commit()

        // Restaurar el filtro DND al estado que tenía antes de la alarma
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted && previousDndFilter != -1) {
                    nm.setInterruptionFilter(previousDndFilter)
                    Log.d(TAG, "✅ DND restaurado al estado anterior: $previousDndFilter")
                    previousDndFilter = -1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restaurando filtro DND", e)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent  = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE,
            Intent(this, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(alarmIntent)
        handler.removeCallbacksAndMessages(null)

        try {
            cameraId?.let { cameraManager?.setTorchMode(it, false) }
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAlarm", e)
        }

        vibrator?.cancel()
        releaseAudioFocus()
        releaseAlarmWakeLocks()
        Log.d(TAG, "stopAlarm: completado")
    }

    // =========================================================
    // ESTADO Y NOTIFICACIÓN
    // =========================================================

    private fun setMonitoringState(isActive: Boolean) {
        sharedPrefs.edit().putBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, isActive).apply()
        updateNotification(isActive)
        sendBroadcast(
            Intent("com.example.btalert.ACTION_MONITORING_STATE_CHANGED")
                .putExtra("is_active", isActive)
        )
    }

    private fun updateNotification(
        isMonitoring: Boolean,
        isAlert:      Boolean = false,
        forceShow:    Boolean = false
    ) {
        try {
            if (!isMonitoring && !isAlert && !forceShow) return

            val ctx        = localizedCtx
            val manager    = getSystemService(NotificationManager::class.java)
            val importance = if (isAlert) NotificationManager.IMPORTANCE_HIGH
            else         NotificationManager.IMPORTANCE_LOW

            val channelId = if (isAlert) {
                MyApplication.ALERT_CHANNEL_ID
            } else {
                MyApplication.MONITOR_CHANNEL_ID
            }

            val chan = NotificationChannel(
                channelId,
                ctx.getString(R.string.notification_channel_name),
                importance
            ).apply {
                // VISIBILITY_SECRET: la notificación de alerta NO aparece en lockscreen.
                // Esto impide que el ladrón detenga la alarma desde la pantalla bloqueada.
                // La notificación de monitoreo normal sí se muestra (VISIBILITY_PUBLIC).
                lockscreenVisibility = if (isAlert) Notification.VISIBILITY_SECRET
                                       else         Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(chan)

            val contentIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val stopPendingIntent = PendingIntent.getService(
                this, 1,
                Intent(this, BluetoothMonitorService::class.java).apply {
                    action = "ACTION_STOP_ALARM"
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val titleText = when {
                isAlert      -> ctx.getString(R.string.notification_alert_title)
                isMonitoring -> ctx.getString(R.string.notification_monitoring_active)
                else         -> ctx.getString(R.string.notification_service_inactive)
            }

            val priority = if (isAlert) NotificationCompat.PRIORITY_MAX
            else         NotificationCompat.PRIORITY_LOW

            val category = if (isAlert) NotificationCompat.CATEGORY_ALARM
            else         NotificationCompat.CATEGORY_SERVICE

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(titleText)
                .setPriority(priority)
                .setCategory(category)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)

            if (isAlert) {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    ctx.getString(R.string.notification_action_stop_alarm),
                    stopPendingIntent
                )
            }

            val notification = builder.build()

            if (isMonitoring || forceShow) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIF_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIF_ID, notification)
                    }
                } catch (e: Exception) {
                    manager?.notify(NOTIF_ID, notification)
                }
            } else {
                manager?.notify(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateNotification", e)
        }
    }

    // =========================================================
    // RECEPTORES
    // =========================================================

    private fun registerStopAlarmReceiver() {
        if (stopAlarmReceiver != null) return
        stopAlarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "🛑 ACTION_STOP_ALARM_LOCAL recibido")
                stopAlarm()
                setMonitoringState(false)
            }
        }
        try {
            ContextCompat.registerReceiver(
                this,
                stopAlarmReceiver,
                IntentFilter("com.example.btalert.ACTION_STOP_ALARM_LOCAL"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerStopAlarmReceiver", e)
        }
    }

    private fun registerDynamicBluetoothReceiver() {
        if (dynamicBluetoothReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        dynamicBluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val device: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                handleBluetoothEvent(action, device)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicBluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(dynamicBluetoothReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerDynamicBluetoothReceiver", e)
        }
    }

    private fun registerPowerButtonReceiver() {
        if (powerButtonReceiver != null) return
        powerButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val isActive = sharedPrefs.getBoolean(
                    AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false
                )
                val fakeLockEnabled = sharedPrefs.getBoolean(
                    AppConfig.PrefsKeys.FAKE_LOCK_ENABLED, false
                )
                val pinSet = sharedPrefs.getString(
                    AppConfig.PrefsKeys.PIN_CODE, ""
                )?.isNotEmpty() == true

                when (action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF — monitoreo=$isActive fakeLock=$fakeLockEnabled")
                        // Al apagar la pantalla, lanzar la pantalla falsa si corresponde
                        if (isActive && fakeLockEnabled && pinSet) {
                            val fakeLockIntent = Intent(
                                applicationContext,
                                FakeLockScreenActivity::class.java
                            ).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                            try {
                                applicationContext.startActivity(fakeLockIntent)
                                Log.d(TAG, "✅ FakeLockScreen lanzada por SCREEN_OFF")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error lanzando FakeLockScreen", e)
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen ON — monitoreo=$isActive fakeLock=$fakeLockEnabled")
                        // Al encender la pantalla también relanzar para ser persistente
                        if (isActive && fakeLockEnabled && pinSet) {
                            handler.postDelayed({
                                val fakeLockIntent = Intent(
                                    applicationContext,
                                    FakeLockScreenActivity::class.java
                                ).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                                try {
                                    applicationContext.startActivity(fakeLockIntent)
                                    Log.d(TAG, "✅ FakeLockScreen relanzada por SCREEN_ON")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error relanzando FakeLockScreen", e)
                                }
                            }, 300L)
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(
                powerButtonReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerPowerButtonReceiver", e)
            powerButtonReceiver = null
        }
    }

    private fun unregisterPowerButtonReceiver() {
        try {
            powerButtonReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in unregisterPowerButtonReceiver", e)
        }
        powerButtonReceiver = null
    }
}