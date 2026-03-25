package com.example.btalert

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import java.util.Locale

/**
 * Clase Application para inicializacion global
 * CORREGIDO: Compatible con Direct Boot (credential encrypted storage)
 */
class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"

        // IDs nuevos de canales para forzar recreación con el idioma actual
        const val MONITOR_CHANNEL_ID = "monitor_channel_bt_v2"
        const val ALERT_CHANNEL_ID   = "alert_channel_bt_v2"

        @Volatile
        private var savedLanguageCode: String? = null

        fun getSavedLanguageCode(): String? = savedLanguageCode

        fun updateLanguageCode(code: String) {
            savedLanguageCode = code
        }
    }

    private lateinit var sharedPrefs: SharedPreferences

    override fun attachBaseContext(base: Context) {
        val prefsContext = try {
            base.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .also { it.all }
            base
        } catch (e: Exception) {
            Log.w(TAG, "Normal storage no disponible — usando protected storage")
            try {
                base.createDeviceProtectedStorageContext()
            } catch (e2: Exception) {
                Log.e(TAG, "Protected storage tampoco disponible", e2)
                base
            }
        }

        sharedPrefs = prefsContext.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        savedLanguageCode = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_LANGUAGE, null)

        Log.d(TAG, "attachBaseContext: savedLanguageCode = $savedLanguageCode")

        val languageCode = savedLanguageCode ?: detectSystemLanguage(base)
        val newContext = updateResourcesLegacy(base, languageCode)

        super.attachBaseContext(newContext)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                applyLanguageToActivity(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Devuelve un contexto con el locale correcto de la app.
     * Usado por servicios y receivers que no heredan el locale de Application.
     */
    fun getLocalizedContext(): Context {
        val languageCode = savedLanguageCode ?: detectSystemLanguage(this)
        return updateResourcesLegacy(this, languageCode)
    }

    private fun applyLanguageToActivity(activity: Activity) {
        val languageCode = savedLanguageCode ?: return

        try {
            val locale = Locale(languageCode)
            Locale.setDefault(locale)

            val config = Configuration(activity.resources.configuration)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }

            config.setLayoutDirection(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                activity.resources.configuration.setLocales(config.locales)
            } else {
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
            }

            Log.d(TAG, "Applied language '$languageCode' to ${activity.localClassName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying language to activity", e)
        }
    }

    fun detectSystemLanguage(context: Context): String {
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        val systemLanguage = systemLocale?.language ?: Locale.getDefault().language

        return if (AppConfig.Languages.AVAILABLE_LANGUAGES.any { it.first == systemLanguage }) {
            systemLanguage
        } else {
            AppConfig.Languages.DEFAULT
        }
    }

    fun updateResourcesLegacy(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        config.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Usar contexto localizado para que los nombres de canal
            // salgan en el idioma elegido en la app, no en el del sistema
            val ctx = getLocalizedContext()

            // No se borra ningún canal existente para evitar SecurityException con fg service

            val monitorChannel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                ctx.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                ctx.getString(R.string.notification_channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ctx.getString(R.string.notification_channel_alert_description)
                setShowBadge(true)
                enableVibration(true)
            }

            manager.createNotificationChannels(listOf(monitorChannel, alertChannel))
        }
    }
}