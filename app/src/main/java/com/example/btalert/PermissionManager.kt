package com.example.btalert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object PermissionManager {

    // ─── Códigos de solicitud ────────────────────────────────────────────────
    const val REQ_BLUETOOTH = 101
    const val REQ_NOTIF     = 102

    // ─── Lista de permisos runtime según API ────────────────────────────────
    fun getRuntimePermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toTypedArray()
    }

    // ─── Verifica si todos los permisos runtime están concedidos ─────────────
    fun allRuntimeGranted(context: Context): Boolean =
        getRuntimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // ─── Verifica si la app está exenta de optimización de batería ───────────
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ─── Verifica autostart (fabricantes específicos) ────────────────────────
    fun needsAutostartSetup(context: Context): Boolean =
        AutostartHelper.needsAutostartPermission(context)

    enum class MissingPermission { RUNTIME, BATTERY, AUTOSTART }
}