package com.example.btalert

import android.content.Context
import android.content.SharedPreferences

object AppConfig {

    const val DEBUG        = true
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1

    const val PREFS_NAME = "MisPreferencias"

    // =========================================================
    // SHAREDPREFERENCES — Helpers duales
    // =========================================================

    fun getPrefs(context: Context): SharedPreferences =
        try {
            context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    fun editPrefs(
        context: Context,
        commit: Boolean = false,
        block: SharedPreferences.Editor.() -> Unit
    ) {
        val normal    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor1   = normal.edit().apply(block)
        if (commit) editor1.commit() else editor1.apply()

        try {
            val protected = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor2 = protected.edit().apply(block)
            if (commit) editor2.commit() else editor2.apply()
        } catch (_: Exception) {}
    }

    // =========================================================
    // CLAVES DE SHAREDPREFERENCES
    // =========================================================

    object PrefsKeys {
        const val SELECTED_DEVICE_MAC   = "selected_device_mac"
        const val SELECTED_DEVICE_NAME  = "selected_device_name"
        const val IS_MONITORING_ACTIVE  = "is_monitoring_active"
        const val USE_FLASH_ALERT       = "use_flash_alert"
        const val USE_VIBRATION_ALERT = "use_vibration_alert"
        const val START_HOUR            = "start_hour"
        const val START_MINUTE          = "start_minute"
        const val END_HOUR              = "end_hour"
        const val END_MINUTE            = "end_minute"
        const val SELECTED_LANGUAGE     = "selected_language"
        const val ALARM_PLAYING         = "alarm_playing"
        const val ONBOARDING_COMPLETED  = "onboarding_completed"
        const val LOW_BATTERY_WARNED    = "low_battery_warned"
        // ✅ NUEVO: controla que la notificación de inicio automático se muestre solo una vez
        const val AUTOSTART_NOTIF_SHOWN = "autostart_notif_shown"
        const val PIN_CODE              = "pin_code"
        const val FAKE_LOCK_ENABLED     = "fake_lock_enabled"
        const val SOS_ENABLED           = "sos_enabled"
        const val SOS_PHONE             = "sos_phone"
        const val SOS_MESSAGE           = "sos_message"
        // Pro — Email de emergencia
        const val PRO_EMAIL_ENABLED     = "pro_email_enabled"
        const val PRO_SMTP_HOST         = "pro_smtp_host"
        const val PRO_SMTP_PORT         = "pro_smtp_port"
        const val PRO_SMTP_USER         = "pro_smtp_user"
        const val PRO_SMTP_PASS         = "pro_smtp_pass"
        const val PRO_EMAIL_TO          = "pro_email_to"
        const val PRO_EMAIL_SUBJECT     = "pro_email_subject"
        const val PRO_EMAIL_BODY        = "pro_email_body"
        const val PRO_ATTACH_FRONT_CAM  = "pro_attach_front_cam"
        const val PRO_ATTACH_REAR_CAM   = "pro_attach_rear_cam"
        const val PRO_ATTACH_AUDIO      = "pro_attach_audio"
        const val PRO_SEND_ON_PANIC     = "pro_send_on_panic"   // Volume DOWN
        const val PRO_SEND_ON_BT_ALARM  = "pro_send_on_bt_alarm" // Alarma BT
    }

    // =========================================================
    // IDIOMAS
    // =========================================================

    object Languages {
        const val DEFAULT    = "en"
        const val ENGLISH    = "en"
        const val SPANISH    = "es"
        const val PORTUGUESE = "pt"
        const val GERMAN     = "de"
        const val FRENCH     = "fr"

        val AVAILABLE_LANGUAGES = listOf(
            Pair(ENGLISH,    "🇬🇧"),
            Pair(SPANISH,    "🇪🇸"),
            Pair(PORTUGUESE, "🇧🇷"),
            Pair(GERMAN,     "🇩🇪"),
            Pair(FRENCH,     "🇫🇷")
        )

        fun getCodeFromIndex(index: Int): String =
            AVAILABLE_LANGUAGES.getOrNull(index)?.first ?: DEFAULT

        fun getIndexFromCode(code: String): Int =
            AVAILABLE_LANGUAGES.indexOfFirst { it.first == code }.takeIf { it >= 0 } ?: 0

        fun getFlagFromCode(code: String): String =
            AVAILABLE_LANGUAGES.find { it.first == code }?.second ?: "🌐"
    }
}