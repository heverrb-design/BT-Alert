package com.example.btalert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("AlarmReceiver", "=== ALARM RECEIVER TRIGGERED ===")
        
        // Verificar horario antes de sonar
        val sharedPrefs = try {
            val ctx = context.createDeviceProtectedStorageContext()
            ctx.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        }
        
        // Verificar si el monitoreo está activo
        val isActive = sharedPrefs.getBoolean(AppConfig.PrefsKeys.IS_MONITORING_ACTIVE, false)
        Log.d("AlarmReceiver", "Monitoreo activo: $isActive")
        
        if (!isActive) {
            Log.d("AlarmReceiver", "Monitoreo inactivo, no iniciar alarma")
            return
        }
        
        // Verificar horario
        val startHour = sharedPrefs.getInt(AppConfig.PrefsKeys.START_HOUR, 0)
        val startMinute = sharedPrefs.getInt(AppConfig.PrefsKeys.START_MINUTE, 0)
        val endHour = sharedPrefs.getInt(AppConfig.PrefsKeys.END_HOUR, 23)
        val endMinute = sharedPrefs.getInt(AppConfig.PrefsKeys.END_MINUTE, 59)
        
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        
        val currentTotal = currentHour * 60 + currentMinute
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        
        val withinSchedule = if (startTotal <= endTotal) {
            currentTotal in startTotal..endTotal
        } else {
            currentTotal >= startTotal || currentTotal <= endTotal
        }
        
        Log.d("AlarmReceiver", "Hora actual: $currentHour:$currentMinute")
        Log.d("AlarmReceiver", "Horario: $startHour:$startMinute - $endHour:$endMinute")
        Log.d("AlarmReceiver", "¿Dentro del horario?: $withinSchedule")
        
        if (!withinSchedule) {
            Log.d("AlarmReceiver", ">>> FUERA DE HORARIO - NO INICIAR ALARMA <<<")
            return
        }
        
        // Iniciar el servicio con la alarma
        val serviceIntent = Intent(context, BluetoothMonitorService::class.java).apply {
            action = "ACTION_SOUND_ALARM"
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(serviceIntent)
            }
            Log.w("AlarmReceiver", "Servicio de alarma iniciado")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error iniciando servicio de alarma", e)
        }
    }
}
