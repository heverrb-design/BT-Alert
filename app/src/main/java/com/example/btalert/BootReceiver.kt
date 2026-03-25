package com.example.btalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") return

        Log.d(TAG, "Boot received: $action")

        try {
            // ✅ Leer SIEMPRE del protected storage en boot
            // El normal storage NO está disponible hasta que el usuario desbloquea
            val sharedPrefs = try {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.w(TAG, "Protected storage no disponible — fallback a normal", e)
                context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            }

            val isActive = sharedPrefs.getBoolean(
                AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false
            )
            val mac = sharedPrefs.getString(
                AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null
            )

            Log.d(TAG, "Boot: IS_MONITORING_ACTIVE=$isActive, MAC=$mac")

            if (!isActive || mac == null) {
                Log.d(TAG, "Monitoreo inactivo o sin MAC — nada que hacer")
                return
            }

            Log.w(TAG, "🚨 Monitoreo ACTIVO en boot — iniciando servicio con alarma inmediata")

            val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
                putExtra("from_boot", true)
                putExtra("immediate_alert", true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver", e)
        }
    }
}
