package com.example.btalert

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import java.util.Calendar
class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var sharedPrefs:     SharedPreferences
    private lateinit var btnPanic:        ImageButton
    private lateinit var txtBtnAction:    TextView
    private lateinit var txtDeviceName:   TextView
    private lateinit var txtBatteryLevel: TextView
    private lateinit var btnConfig:       LinearLayout
    private lateinit var txtStatusHeader: TextView

    private var powerMenuBlocker:  PowerMenuBlocker?      = null
    private var pairedDevices:     List<BluetoothDevice>? = null
    private var stateReceiver:     BroadcastReceiver?     = null
    private var bluetoothReceiver: BroadcastReceiver?     = null

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, MODE_PRIVATE)

        if (!sharedPrefs.getBoolean(AppConfig.PrefsKeys.ONBOARDING_COMPLETED, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        btnPanic        = findViewById(R.id.btnPanic)
        txtBtnAction    = findViewById(R.id.txtBtnAction)
        txtDeviceName   = findViewById(R.id.txtDeviceName)
        txtBatteryLevel = findViewById(R.id.txtBatteryLevel)
        btnConfig       = findViewById(R.id.btnConfig)
        txtStatusHeader = findViewById(R.id.txtStatusHeader)

        initPowerMenuBlocker()
        initStateReceiver()
        initBluetoothReceiver()
        initPanicButton()

        btnConfig.setOnClickListener {
            if (sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings_unavailable_title)
                    .setMessage(R.string.settings_unavailable_message)
                    .setPositiveButton(R.string.dialog_button_ok, null)
                    .show()
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        loadDevicesIfPermitted()
        processIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshButtonState()
        refreshDeviceInfo()
        loadDevicesIfPermitted()
        showAutostartDialogIfNeeded()
        // Re-aplicar modo inmersivo si el monitoreo sigue activo
        if (sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)) {
            hideStatusBar()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // Si la alarma está sonando, FakeLockScreen tiene el control.
        // No interferir con PowerMenuBlocker en ese estado.
        val alarmPlaying = sharedPrefs.getBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, false)
        if (alarmPlaying) return

        powerMenuBlocker?.onWindowFocusChanged(hasFocus)
        if (hasFocus && sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)) {
            hideStatusBar()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { stateReceiver?.let { unregisterReceiver(it) } }     catch (_: Exception) {}
        try { bluetoothReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        powerMenuBlocker?.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionManager.REQ_BLUETOOTH -> {
                val allGranted = grantResults.isNotEmpty() &&
                        grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    loadPairedDevices()
                    checkPermissionsAndStart()
                } else {
                    handleDeniedPermissions(permissions)
                }
            }
            PermissionManager.REQ_NOTIF -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    checkPermissionsAndStart()
                } else {
                    handleDeniedPermissions(permissions)
                }
            }
        }
    }

    // =========================================================
    // INIT HELPERS
    // =========================================================

    private fun initPowerMenuBlocker() {
        powerMenuBlocker = PowerMenuBlocker(this) { Log.d(TAG, "Power menu attempt") }
        if (sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)) {
            powerMenuBlocker?.enable()
        }
    }

    private fun initPanicButton() {
        btnPanic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    buzzShort()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            true
        }
        btnPanic.setOnClickListener {
            val active = sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
            Log.d(TAG, "btnPanic clicked active=$active")
            if (active) stopMonitoring() else checkPermissionsAndStart()
        }
    }

    private fun initStateReceiver() {
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val active = intent?.getBooleanExtra("is_active", false) ?: false
                runOnUiThread { refreshButtonState(active) }
            }
        }
        ContextCompat.registerReceiver(
            this, stateReceiver,
            IntentFilter("com.example.btalert.ACTION_MONITORING_STATE_CHANGED"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val a = intent?.action ?: return
                if (a == BluetoothDevice.ACTION_ACL_CONNECTED ||
                    a == BluetoothDevice.ACTION_ACL_DISCONNECTED ||
                    a == BluetoothDevice.ACTION_BOND_STATE_CHANGED
                ) {
                    runOnUiThread { loadPairedDevices(); refreshDeviceInfo() }
                }
            }
        }
        ContextCompat.registerReceiver(
            this, bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // =========================================================
    // AUTOSTART DIALOG
    // =========================================================

    private fun showAutostartDialogIfNeeded() {
        val alreadyShown = sharedPrefs.getBoolean(
            AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, false
        )
        if (alreadyShown) return

        val manufacturer    = Build.MANUFACTURER.lowercase()
        val autostartIntent = getAutostartIntent(manufacturer) ?: return

        val resolves = packageManager.resolveActivity(autostartIntent, 0) != null
        if (!resolves) return

        Log.w(TAG, "⚠️ Mostrando diálogo de autostart para $manufacturer")

        sharedPrefs.edit()
            .putBoolean(AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, true)
            .apply()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.autostart_notif_title))
            .setMessage(getString(R.string.autostart_notif_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.autostart_btn_go_settings)) { _, _ ->
                try { startActivity(autostartIntent) } catch (_: Exception) {}
            }
            .setNegativeButton(getString(R.string.perm_btn_cancel), null)
            .show()
    }

    private fun getAutostartIntent(manufacturer: String): Intent? {
        return when {
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                    manufacturer.contains("poco") -> Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.FakeActivity"
                )
            }
            manufacturer.contains("vivo") -> Intent().apply {
                setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            manufacturer.contains("samsung") -> Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
            else -> null
        }
    }

    // =========================================================
    // MONITORING — punto de entrada unificado
    // =========================================================

    private fun checkPermissionsAndStart() {
        val missingRuntime = buildRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingRuntime.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_runtime_title)
                .setMessage(R.string.perm_runtime_message)
                .setCancelable(false)
                .setPositiveButton(R.string.perm_btn_grant) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        missingRuntime.toTypedArray(),
                        PermissionManager.REQ_BLUETOOTH
                    )
                }
                .setNegativeButton(R.string.perm_btn_cancel, null)
                .show()
            return
        }

        if (!checkExactAlarmPermission()) return
        if (!ensureBatteryNotRestricted()) return

        val m = Build.MANUFACTURER.lowercase()
        val skipFullScreen = m.contains("xiaomi") || m.contains("redmi") ||
                m.contains("poco") || m.contains("huawei") || m.contains("honor")
        if (!skipFullScreen && !checkFullScreenPermission()) return

        val autostartAlreadyShown = sharedPrefs.getBoolean(
            AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, false
        )
        if (!autostartAlreadyShown && AutostartHelper.needsAutostartPermission(this)) {
            sharedPrefs.edit()
                .putBoolean(AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, true)
                .apply()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.autostart_notif_title))
                .setMessage(getString(R.string.autostart_notif_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.autostart_btn_go_settings)) { _, _ ->
                    AutostartHelper.openAutostartSettings(this)
                }
                .setNegativeButton(getString(R.string.perm_btn_cancel), null)
                .show()
            return
        }

        // 🔹 NUEVO: validar rango horario antes de activar el monitoreo
        if (!isWithinSchedule()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.schedule_out_of_range_title))
                .setMessage(getString(R.string.schedule_out_of_range_message))
                .setPositiveButton(getString(R.string.schedule_out_of_range_configure)) { _, _ ->
                    startActivity(Intent(this, ScheduleSettingsActivity::class.java))
                }
                .setNegativeButton(getString(R.string.cancel_button), null)
                .show()
            return
        }

        beginMonitoringInternal()
    }

    private fun beginMonitoringInternal() {
        val mac = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)
        if (mac == null) {
            Toast.makeText(this, R.string.setup_device_toast, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val device = pairedDevices?.find { it.address == mac }

        if (!isDeviceConnectedByMac(mac, device)) {
            val displayName = device?.let { deviceName(it) }
                ?: sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, null)
                ?: mac
            AlertDialog.Builder(this)
                .setTitle(R.string.cannot_activate_dialog_title)
                .setMessage(getString(R.string.cannot_activate_dialog_message, displayName))
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show()
            return
        }

        device?.let {
            AppConfig.editPrefs(this, commit = true) {
                putString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, deviceName(it))
            }
        }

        AppConfig.editPrefs(this, commit = true) {
            putBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, true)
        }

        launchMonitorService()
        refreshButtonState(true)
        powerMenuBlocker?.enable()
        hideStatusBar()
        moveTaskToBack(true)
    }

    private fun stopMonitoring() {
        AppConfig.editPrefs(this, commit = true) {
            putBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
            putBoolean(AppConfig.PrefsKeys.ALARM_PLAYING, false)
        }

        sendBroadcast(
            Intent("com.example.btalert.ACTION_STOP_ALARM_LOCAL").apply {
                setPackage(packageName)
            }
        )
        try {
            stopService(
                Intent(this, BluetoothMonitorService::class.java).apply {
                    action = "ACTION_STOP_SERVICE"
                }
            )
        } catch (_: Exception) {}

        refreshButtonState(false)
        powerMenuBlocker?.disable()
        showStatusBar()
    }

    private fun launchMonitorService() {
        startForegroundService(Intent(this, BluetoothMonitorService::class.java))
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private fun buildRequiredPermissions(): List<String> =
        PermissionManager.getRuntimePermissions().toList()

    private fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.exact_alarm_permission_title)
                    .setMessage(R.string.exact_alarm_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton(R.string.perm_btn_cancel, null)
                    .show()
                return false
            }
        }
        return true
    }

    private fun ensureBatteryNotRestricted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm       = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)

            if (!ignoring) {
                val m        = Build.MANUFACTURER.lowercase()
                val isHuawei = m.contains("huawei") || m.contains("honor")

                val message = when {
                    m.contains("xiaomi") || m.contains("redmi") ->
                        getString(R.string.battery_opt_message_xiaomi, getString(R.string.app_name))
                    isHuawei ->
                        getString(R.string.battery_opt_message_huawei, getString(R.string.app_name))
                    m.contains("samsung") ->
                        getString(R.string.battery_opt_message_samsung, getString(R.string.app_name))
                    m.contains("oppo") || m.contains("realme") ->
                        getString(R.string.battery_opt_message_oppo, getString(R.string.app_name))
                    m.contains("vivo") ->
                        getString(R.string.battery_opt_message_vivo, getString(R.string.app_name))
                    else ->
                        getString(R.string.battery_opt_message_generic, getString(R.string.app_name))
                }

                if (isHuawei) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.battery_opt_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.open_settings) { _, _ ->
                            try {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            }
                        }
                        .setNegativeButton(R.string.continue_anyway) { _, _ ->
                            val m2 = Build.MANUFACTURER.lowercase()
                            if (!m2.contains("huawei") && !m2.contains("honor")) return@setNegativeButton
                            if (AutostartHelper.needsAutostartPermission(this)) {
                                checkPermissionsAndStart()
                            } else {
                                beginMonitoringInternal()
                            }
                        }
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.battery_opt_title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.open_settings) { _, _ ->
                            try {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            }
                        }
                        .setNegativeButton(R.string.perm_btn_cancel, null)
                        .show()
                }
                return false
            }
        }
        return true
    }

    private fun checkFullScreenPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.full_screen_intent_permission_title)
                    .setMessage(R.string.full_screen_intent_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        try {
                            startActivity(
                                Intent(
                                    "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENTS",
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } catch (_: ActivityNotFoundException) {
                            try {
                                startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                )
                            } catch (_: Exception) {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                )
                            }
                        }
                    }
                    .setNegativeButton(R.string.perm_btn_cancel) { _, _ ->
                        Toast.makeText(
                            this, R.string.full_screen_intent_warning, Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
                return false
            }
        }
        return true
    }

    private fun handleDeniedPermissions(permissions: Array<String>) {
        val anyPermanentlyDenied = permissions.any { perm ->
            !ActivityCompat.shouldShowRequestPermissionRationale(this, perm) &&
                    ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (anyPermanentlyDenied) {
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_runtime_title)
                .setMessage(R.string.perm_denied_permanent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.perm_btn_go_settings) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                .setNegativeButton(R.string.perm_btn_cancel, null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.perm_runtime_title)
                .setMessage(R.string.perm_runtime_message)
                .setCancelable(false)
                .setPositiveButton(R.string.perm_btn_grant) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        buildRequiredPermissions().toTypedArray(),
                        PermissionManager.REQ_BLUETOOTH
                    )
                }
                .setNegativeButton(R.string.perm_btn_cancel, null)
                .show()
        }
    }

    private fun loadDevicesIfPermitted() {
        val needed = buildRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) loadPairedDevices()
    }

    // =========================================================
    // BLUETOOTH DEVICES
    // =========================================================

    private fun loadPairedDevices() {
        val btManager = getSystemService(BluetoothManager::class.java) ?: return
        if (!btManager.adapter?.isEnabled!!) {
            txtDeviceName.text = "⚠️ ${getString(R.string.bluetooth_disabled)}"
            return
        }

        pairedDevices = BluetoothHelper.getConnectedDevices(this)
        Log.d(TAG, "loadPairedDevices: ${pairedDevices?.size} conectados")
        refreshDeviceInfo()
    }

    private fun isDeviceConnectedByMac(mac: String, device: BluetoothDevice?): Boolean {
        val btManager = getSystemService(BluetoothManager::class.java) ?: return false
        val btAdapter = btManager.adapter ?: return false

        if (device != null) {
            try {
                val result = device.javaClass.getMethod("isConnected").invoke(device) as? Boolean
                if (result == true) {
                    Log.d(TAG, "isConnected reflexión=true para $mac")
                    return true
                }
            } catch (_: Exception) {}
        }

        val hasLocation = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasLocation || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (profileId in listOf(1, 2, 7, 11, 21)) {
                try {
                    if (btManager.getConnectedDevices(profileId).any { it.address == mac }) {
                        Log.d(TAG, "Conectado en perfil $profileId para $mac")
                        return true
                    }
                } catch (_: Exception) {}
            }
        }

        val STATE_CONNECTED = 2
        for (profileId in intArrayOf(1, 2, 4, 5)) {
            try {
                @Suppress("DEPRECATION")
                val state = btAdapter.getProfileConnectionState(profileId)
                if (state == STATE_CONNECTED) {
                    Log.d(TAG, "getProfileConnectionState perfil $profileId = CONNECTED → asumiendo $mac")
                    return true
                }
            } catch (_: Exception) {}
        }

        Log.w(TAG, "Dispositivo $mac NO encontrado. LOCATION=$hasLocation API=${Build.VERSION.SDK_INT}")
        return false
    }

    private fun deviceName(device: BluetoothDevice): String =
        BluetoothHelper.getDeviceName(this, device)

    private fun deviceBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val level = device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int ?: -1
            Log.d(TAG, "getBatteryLevel(${device.address}) = $level")
            level
        } catch (e: Exception) {
            Log.w(TAG, "getBatteryLevel excepción: ${e.javaClass.simpleName} — ${e.message}")
            -1
        }
    }

    // =========================================================
    // UI
    // =========================================================

    private fun refreshDeviceInfo() {
        val mac    = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)
        val device = pairedDevices?.find { it.address == mac }

        Log.d(TAG, "refreshDeviceInfo: mac=$mac device=${device?.address} pairedDevices=${pairedDevices?.size}")

        if (device != null) {
            // Dispositivo conectado → nombre en VERDE
            txtDeviceName.text = deviceName(device)
            txtDeviceName.setTextColor("#4CAF50".toColorInt())
        } else {
            // No conectado → nombre guardado o placeholder en ROJO
            txtDeviceName.text = sharedPrefs.getString(
                AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, null
            ) ?: getString(R.string.no_device_selected)
            txtDeviceName.setTextColor("#D32F2F".toColorInt())
        }

        if (device == null) {
            txtBatteryLevel.text = getString(R.string.battery_level_unknown)
            txtBatteryLevel.setTextColor("#8E91B3".toColorInt())
            return
        }

        val level = deviceBatteryLevel(device)
        if (level == -1) {
            txtBatteryLevel.text = getString(R.string.battery_level_unknown)
            txtBatteryLevel.setTextColor("#8E91B3".toColorInt())
            return
        }

        txtBatteryLevel.text = "$level%"
        val warned = sharedPrefs.getBoolean(AppConfig.PrefsKeys.LOW_BATTERY_WARNED, false)
        when {
            level <= 10 -> {
                txtBatteryLevel.setTextColor(Color.RED)
                if (!warned) {
                    Toast.makeText(
                        this,
                        getString(R.string.low_battery_warning, level),
                        Toast.LENGTH_LONG
                    ).show()
                    sharedPrefs.edit {
                        putBoolean(AppConfig.PrefsKeys.LOW_BATTERY_WARNED, true)
                    }
                }
            }
            level <= 20 -> txtBatteryLevel.setTextColor("#FFA500".toColorInt())
            else -> {
                txtBatteryLevel.setTextColor("#4CAF50".toColorInt())
                if (warned && level >= 30) {
                    sharedPrefs.edit {
                        putBoolean(AppConfig.PrefsKeys.LOW_BATTERY_WARNED, false)
                    }
                }
            }
        }
    }

    private fun refreshButtonState(
        isActive: Boolean =
            sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
    ) {
        if (isActive) {
            btnPanic.setImageResource(R.drawable.btn_red)
            txtBtnAction.text    = getString(R.string.stop_monitoring_short)
            txtStatusHeader.text = getString(R.string.status_active)
            txtStatusHeader.setTextColor("#4CAF50".toColorInt())
            btnConfig.alpha      = 0.5f
            btnConfig.isEnabled  = false
        } else {
            btnPanic.setImageResource(R.drawable.btn_green)
            txtBtnAction.text    = getString(R.string.start_monitoring_short)
            txtStatusHeader.text = getString(R.string.status_inactive)
            txtStatusHeader.setTextColor("#D32F2F".toColorInt())
            btnConfig.alpha      = 1.0f
            btnConfig.isEnabled  = true
        }
    }

    // =========================================================
    // INTENT HANDLING
    // =========================================================

    private fun processIncomingIntent(intent: Intent?) {
        if (intent?.action != "ACTION_PROMPT_MONITORING") return

        val device: BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

        device?.let {
            AlertDialog.Builder(this)
                .setTitle(R.string.new_device_dialog_title)
                .setMessage(getString(R.string.new_device_dialog_message, deviceName(it)))
                .setPositiveButton(R.string.new_device_dialog_positive_button) { _, _ ->
                    AppConfig.editPrefs(this, commit = true) {
                        putString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC,  it.address)
                        putString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, deviceName(it))
                    }
                    refreshDeviceInfo()
                    checkPermissionsAndStart()
                }
                .setNegativeButton(R.string.new_device_dialog_negative_button, null)
                .show()
        }
    }

    // =========================================================
    // UTILS
    // =========================================================

    private fun buzzShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
                    ?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
                    ?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }
    // =========================================================
// SCHEDULE CHECK
// =========================================================

    private fun isWithinSchedule(): Boolean {
        val startHour   = sharedPrefs.getInt(AppConfig.PrefsKeys.START_HOUR,   0)
        val startMinute = sharedPrefs.getInt(AppConfig.PrefsKeys.START_MINUTE, 0)
        val endHour     = sharedPrefs.getInt(AppConfig.PrefsKeys.END_HOUR,     23)
        val endMinute   = sharedPrefs.getInt(AppConfig.PrefsKeys.END_MINUTE,   59)

        val cal     = Calendar.getInstance()
        val current = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start   = startHour * 60 + startMinute
        val end     = endHour   * 60 + endMinute

        Log.d(TAG, "isWithinSchedule: current=$current start=$start end=$end")

        return if (start <= end) current in start..end
        else current >= start || current <= end
    }
}