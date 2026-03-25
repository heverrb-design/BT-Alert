package com.example.btalert

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * PowerMenuBlocker - Bloquea el menú de apagado en dispositivos Android
 * 
 * Compatibilidad:
 * - Android 10 y anteriores: Usa ACTION_CLOSE_SYSTEM_DIALOGS
 * - Android 11+: Usa detección de pérdida de foco y re-lanzamiento
 * 
 * Funcionamiento:
 * 1. Detecta cuando el usuario intenta abrir el menú de apagado (pulsación larga power)
 * 2. Cierra los diálogos del sistema o re-lanza la app inmediatamente
 * 3. Mantiene la app en primer plano para evitar que se apague
 */
class PowerMenuBlocker(
    private val activity: Activity,
    private val onPowerMenuAttempt: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "PowerMenuBlocker"
        private const val RELAUNCH_DELAY_MS = 100L
        private const val GRACE_PERIOD_MS = 2000L
        
        // Constante para compatibilidad
        const val BEHAVIOR_SHOW_BRIEFLY_BY_TOUCH = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isEnabled = false
    private var activationTime = 0L
    private var isSystemDialogShowing = false
    
    // Bandera para evitar bloqueos durante autenticación
    private var authenticationInProgress = false

    /**
     * Activa el bloqueo del menú de apagado.
     * Debe llamarse cuando se active el monitoreo.
     */
    fun enable() {
        if (isEnabled) return
        isEnabled = true
        activationTime = System.currentTimeMillis()
        Log.d(TAG, "PowerMenuBlocker ENABLED at $activationTime")
    }
    
    /**
     * Alias para enable() - compatibilidad con código existente
     */
    fun startBlocking() {
        enable()
    }

    /**
     * Desactiva el bloqueo del menú de apagado.
     * Debe llamarse cuando se detenga el monitoreo.
     */
    fun disable() {
        isEnabled = false
        authenticationInProgress = false
        Log.d(TAG, "PowerMenuBlocker DISABLED")
    }
    
    /**
     * Alias para disable() - compatibilidad con código existente
     */
    fun stopBlocking() {
        disable()
    }
    
    /**
     * Limpia los recursos cuando ya no se necesita el blocker.
     */
    fun cleanup() {
        disable()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "PowerMenuBlocker CLEANUP")
    }

    /**
     * Marca que se está mostrando un diálogo de autenticación.
     * Durante este tiempo, el bloqueo debe permitir que el diálogo se muestre.
     */
    fun setAuthenticationInProgress(inProgress: Boolean) {
        authenticationInProgress = inProgress
        isSystemDialogShowing = inProgress
        Log.d(TAG, "Authentication in progress: $inProgress")
    }
    
    /**
     * Establece si se está mostrando un diálogo del sistema.
     * Usado por AlarmActivity para permitir diálogos de autenticación.
     */
    fun setSystemDialogShowing(showing: Boolean) {
        isSystemDialogShowing = showing
        authenticationInProgress = showing
        Log.d(TAG, "System dialog showing: $showing")
    }

    /**
     * Verifica si está dentro del período de gracia.
     * El período de gracia permite que moveTaskToBack() funcione sin ser bloqueado.
     */
    private fun isInGracePeriod(): Boolean {
        val elapsed = System.currentTimeMillis() - activationTime
        return elapsed < GRACE_PERIOD_MS
    }

    /**
     * Maneja el cambio de foco de la ventana.
     * Debe llamarse desde Activity.onWindowFocusChanged()
     * 
     * @param hasFocus true si la ventana tiene foco, false si lo perdió
     */
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!isEnabled) return
        if (authenticationInProgress || isSystemDialogShowing) {
            Log.d(TAG, "Ignoring focus change during system dialog/auth")
            return
        }
        
        if (isInGracePeriod()) {
            Log.d(TAG, "In grace period, ignoring focus change")
            return
        }

        if (!hasFocus) {
            // La app perdió el foco - posible intento de abrir menú de apagado
            Log.d(TAG, "Window lost focus - possible power menu attempt")
            onPowerMenuAttempt?.invoke()
            
            // Bloquear el menú de apagado
            blockPowerMenu()
        }
    }

    /**
     * Bloquea el menú de apagado según la versión de Android.
     */
    private fun blockPowerMenu() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            // Android 11 (R) y anteriores: ACTION_CLOSE_SYSTEM_DIALOGS funciona
            closeSystemDialogsLegacy()
        }
        
        // Para todas las versiones: re-lanzar la app para asegurar que permanezca visible
        relaunchActivity()
    }

    /**
     * Cierra los diálogos del sistema usando ACTION_CLOSE_SYSTEM_DIALOGS.
     * Solo funciona en Android 11 y anteriores.
     */
    private fun closeSystemDialogsLegacy() {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                val intent = android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                activity.sendBroadcast(intent)
                Log.d(TAG, "Sent ACTION_CLOSE_SYSTEM_DIALOGS broadcast")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException when closing system dialogs: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing system dialogs", e)
        }
    }

    /**
     * Re-lanza la actividad para traerla al frente.
     * Funciona en todas las versiones de Android.
     */
    private fun relaunchActivity() {
        handler.postDelayed({
            try {
                if (!isEnabled || authenticationInProgress || isSystemDialogShowing) {
                    Log.d(TAG, "Skipping relaunch - disabled or dialog in progress")
                    return@postDelayed
                }

                // Verificar si la pantalla está bloqueada
                val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isLocked = keyguardManager.isDeviceLocked

                if (isLocked) {
                    Log.d(TAG, "Device is locked, setting flags to show over lock screen")
                    // Configurar flags para mostrar sobre la pantalla de bloqueo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        activity.setShowWhenLocked(true)
                        activity.setTurnScreenOn(true)
                    } else {
                        @Suppress("DEPRECATION")
                        activity.window.addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        )
                    }
                }

                // Traer la actividad al frente
                val intent = activity.intent ?: return@postDelayed
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                activity.startActivity(intent)
                
                Log.d(TAG, "Activity relaunched successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error relaunching activity", e)
            }
        }, RELAUNCH_DELAY_MS)
    }

    /**
     * Limpia los recursos cuando ya no se necesita el blocker.
     */
    fun destroy() {
        cleanup()
    }
}
