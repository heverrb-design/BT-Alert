package com.example.btalert

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * JobService persistente que arranca el servicio de monitoreo como fallback
 * si el BootReceiver/AlarmManager fallan en iniciar el servicio inmediatamente.
 */
class MonitorJobService : JobService() {

    private val TAG = "MonitorJobService"

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob: starting BluetoothMonitorService as fallback")
        try {
            // Usar contexto device-protected para poder ejecutar antes del desbloqueo
            val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) createDeviceProtectedStorageContext() else this
            val intent = Intent(ctx, BluetoothMonitorService::class.java).apply {
                putExtra("check_connection_on_start", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            Log.d(TAG, "onStartJob: requested service start via JobService")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "onStartJob: failed to start service", e)
        }

        // Job completed; no trabajo en background continuado
        params?.let { jobFinished(it, false) }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // No reintentar el job (se volverá a programar en boot si es necesario)
        return false
    }
}
