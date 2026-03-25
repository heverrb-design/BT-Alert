package com.example.btalert

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AutostartHelper {

    /**
     * Retorna true SOLO si el fabricante requiere autostart Y el usuario
     * aún no ha sido notificado (primera vez).
     * No intenta verificar el estado real — es imposible de forma confiable en HyperOS/MIUI 14+.
     */
    fun needsAutostartPermission(context: Context): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val requiresSetup = when {
            manufacturer.contains("xiaomi")  -> true
            manufacturer.contains("redmi")   -> true
            manufacturer.contains("poco")    -> true
            manufacturer.contains("huawei")  -> true
            manufacturer.contains("honor")   -> true
            manufacturer.contains("oppo")    -> true
            manufacturer.contains("realme")  -> true
            manufacturer.contains("vivo")    -> true
            manufacturer.contains("samsung") -> false
            manufacturer.contains("google")  -> false
            else                             -> false
        }

        if (!requiresSetup) return false

        // Solo mostrar una vez — después confiar en el usuario
        val alreadyShown = AppConfig.getPrefs(context)
            .getBoolean(AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, false)

        return !alreadyShown
    }

    /**
     * Marca el diálogo como ya mostrado para no repetirlo.
     * Llamar justo ANTES de abrir los ajustes o cuando el usuario pulsa "Ir a Ajustes".
     */
    fun markAsShown(context: Context) {
        AppConfig.editPrefs(context, commit = true) {
            putBoolean(AppConfig.PrefsKeys.AUTOSTART_NOTIF_SHOWN, true)
        }
    }

    fun openAutostartSettings(context: Context) {
        markAsShown(context) // ← marcar antes de abrir
        val intents = getAutostartIntents()
        for (intent in intents) {
            if (isIntentResolvable(context, intent)) {
                context.startActivity(intent)
                return
            }
        }
        val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }

    private fun getAutostartIntents(): List<Intent> = listOf(
        // Xiaomi / MIUI / HyperOS
        Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Huawei / Honor
        Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Oppo / Realme
        Intent().apply {
            component = android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Vivo
        Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )

    private fun isIntentResolvable(context: Context, intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()
}