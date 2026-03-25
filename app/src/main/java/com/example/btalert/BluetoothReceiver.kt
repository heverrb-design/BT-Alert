package com.example.btalert

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothReceiver"

        // ✅ Lee de protected storage primero, fallback a normal
        fun getPrefs(context: Context) =
            try {
                context.createDeviceProtectedStorageContext()
                    .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            } catch (e: Exception) {
                context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            Log.d(TAG, "onReceive: action=$action")

            val device: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting device (TIRAMISU)", e); null
                    }
                } else {
                    try {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting device (legacy)", e); null
                    }
                }

            Log.d(TAG, "onReceive: device=${device?.address}, action=$action")

            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                Log.d(TAG, "onReceive: ACTION_ACL_CONNECTED detected, device=${device?.address}")
                try {
                    // ✅ Usar helper con fallback
                    val sharedPrefs  = getPrefs(context)
                    val savedDeviceMac = sharedPrefs.getString(
                        AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null
                    )
                    val deviceMac = device?.address

                    Log.d(TAG, "ACTION_ACL_CONNECTED: deviceMac=$deviceMac, savedDeviceMac=$savedDeviceMac")

                    if (deviceMac != null && deviceMac != savedDeviceMac) {
                        Log.d(TAG, "Nuevo dispositivo detectado, mostrando dialogo")
                        val startAppIntent = Intent(context, MainActivity::class.java).apply {
                            this.action = "ACTION_PROMPT_MONITORING"
                            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        context.startActivity(startAppIntent)
                    } else {
                        Log.d(TAG, "Dispositivo guardado reconectado ($deviceMac) — sin dialogo")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ACTION_ACL_CONNECTED", e)
                }

            } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                Log.d(TAG, "onReceive: ACTION_ACL_DISCONNECTED detected, device=${device?.address}")
            }

            // Reenviar al servicio
            Log.d(TAG, "onReceive: forwarding event to BluetoothMonitorService")
            val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
                this.action = action
                if (device != null) putExtra(BluetoothDevice.EXTRA_DEVICE, device)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    @Suppress("DEPRECATION")
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "onReceive: startForegroundService called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar servicio", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in onReceive", e)
        }
    }
}
