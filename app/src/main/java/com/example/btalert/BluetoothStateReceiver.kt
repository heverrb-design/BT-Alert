package com.example.btalert

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BluetoothStateReceiver - Detecta cuando el Bluetooth se apaga
 * 
 * Este receiver esta registrado ESTATICAMENTE en el AndroidManifest,
 * lo que permite recibir eventos incluso con la pantalla apagada.
 * 
 * REGISTRADO ESTATICAMENTE = FUNCIONA SIEMPRE
 */
class BluetoothStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BluetoothStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        Log.d(TAG, "Bluetooth state changed: $state")

        when (state) {
            BluetoothAdapter.STATE_OFF -> {
                Log.w(TAG, "BLUETOOTH APAGADO - Verificando si debe activar alarma")
                handleBluetoothTurnedOff(context)
            }
            BluetoothAdapter.STATE_ON -> {
                Log.d(TAG, "Bluetooth encendido")
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                Log.d(TAG, "Bluetooth apagandose...")
            }
            BluetoothAdapter.STATE_TURNING_ON -> {
                Log.d(TAG, "Bluetooth encendiendo...")
            }
        }
    }

    private fun handleBluetoothTurnedOff(context: Context) {
        try {
            // Usar almacenamiento protegido para acceder a preferencias antes del desbloqueo
            val prefContext = try {
                context.createDeviceProtectedStorageContext()
            } catch (e: Exception) {
                Log.w(TAG, "Could not create device protected context", e)
                context
            }

            val sharedPrefs = prefContext.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val isMonitoringActive = sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
            val selectedDeviceMac = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)

            Log.d(TAG, "isMonitoringActive=$isMonitoringActive, selectedDeviceMac=$selectedDeviceMac")

            if (isMonitoringActive && selectedDeviceMac != null) {
                Log.w(TAG, "ALERTA: Bluetooth apagado mientras monitoreo activo!")
                
                // Iniciar el servicio de alarma directamente
                val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
                    action = "ACTION_SOUND_ALARM"
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        @Suppress("DEPRECATION")
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "Servicio de alarma iniciado por apagado de Bluetooth")
                } catch (e: Exception) {
                    Log.e(TAG, "Error al iniciar servicio de alarma", e)
                    
                    // Intentar como respaldo enviar broadcast al servicio
                    try {
                        val alarmIntent = Intent("com.example.btalert.ACTION_TRIGGER_ALARM")
                        alarmIntent.setPackage(context.packageName)
                        context.sendBroadcast(alarmIntent)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error en broadcast de respaldo", ex)
                    }
                }
            } else {
                Log.d(TAG, "Monitoreo inactivo o sin dispositivo seleccionado, no se activa alarma")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en handleBluetoothTurnedOff", e)
        }
    }
}
