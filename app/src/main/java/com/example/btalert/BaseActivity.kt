package com.example.btalert

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Activity base que aplica el idioma guardado antes de mostrar la UI,
 * y provee utilidades para el modo inmersivo (ocultar barra de estado).
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val context = applySavedLanguage(newBase)
        super.attachBaseContext(context)
    }

    // =========================================================
    // MODO INMERSIVO — oculta barra de estado y navegación
    // =========================================================

    /**
     * Activa el modo inmersivo sticky.
     * Llama a esto en onResume y onWindowFocusChanged cuando el monitoreo está activo.
     * En Android 11+: usa WindowInsetsController.
     * En Android 10 y anteriores: usa flags legacy.
     *
     * Con esto activo, la barra de estado requiere un swipe deliberado para aparecer
     * y se vuelve a ocultar automáticamente al perder el foco (llamando hideStatusBar de nuevo).
     */
    protected fun hideStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } catch (e: Exception) {
            // No crítico — ignorar si falla en algún fabricante
        }
    }

    /**
     * Restaura la barra de estado al estado normal.
     * Llama a esto cuando el monitoreo se desactiva.
     */
    protected fun showStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        } catch (e: Exception) {
            // No crítico
        }
    }

    // =========================================================
    // IDIOMA
    // =========================================================

    /**
     * Aplica el idioma guardado al contexto
     */
    private fun applySavedLanguage(context: Context): Context {
        val languageCode = getSavedLanguage(context)
        
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

    /**
     * Obtiene el idioma guardado o detecta del sistema
     */
    private fun getSavedLanguage(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getString(AppConfig.PrefsKeys.SELECTED_LANGUAGE, null)
            
            if (saved != null && AppConfig.Languages.AVAILABLE_LANGUAGES.any { it.first == saved }) {
                saved
            } else {
                detectSystemLanguage(context)
            }
        } catch (e: Exception) {
            AppConfig.Languages.DEFAULT
        }
    }

    /**
     * Detecta el idioma del sistema
     */
    private fun detectSystemLanguage(context: Context): String {
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
}
