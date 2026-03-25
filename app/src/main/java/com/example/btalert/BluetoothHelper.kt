package com.example.btalert

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

object BluetoothHelper {

    private const val TAG = "BluetoothHelper"

    /**
     * Devuelve la lista de dispositivos Bluetooth actualmente conectados.
     * Combina dos métodos para máxima compatibilidad entre fabricantes:
     *   1. Dispositivos conectados por perfil activo (HEADSET, A2DP, etc.)
     *   2. bondedDevices filtrados por isConnected() vía reflexión
     */
    fun getConnectedDevices(context: Context): List<BluetoothDevice> {
        val btManager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val btAdapter = btManager.adapter ?: return emptyList()

        if (!btAdapter.isEnabled) return emptyList()

        if (!hasBluetoothPermission(context)) {
            Log.w(TAG, "getConnectedDevices: sin permiso en API ${Build.VERSION.SDK_INT}")
            return emptyList()
        }

        return try {
            // Método 1: dispositivos conectados por perfil activo
            val connectedByProfile = mutableSetOf<BluetoothDevice>()
            for (profileId in intArrayOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 7)) {
                try {
                    btManager.getConnectedDevices(profileId)?.let { connectedByProfile.addAll(it) }
                } catch (_: Exception) {}
            }

            // Método 2: bondedDevices filtrados por isConnected() vía reflexión
            val connectedByReflection = mutableSetOf<BluetoothDevice>()
            try {
                btAdapter.bondedDevices?.forEach { device ->
                    try {
                        val method = device.javaClass.getMethod("isConnected")
                        val isConn = method.invoke(device) as? Boolean ?: false
                        if (isConn) connectedByReflection.add(device)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Unión de ambos métodos para máxima cobertura entre fabricantes
            val result = (connectedByProfile + connectedByReflection).toList()
            Log.d(TAG, "getConnectedDevices: ${result.size} conectados. API=${Build.VERSION.SDK_INT}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getConnectedDevices error: $e")
            emptyList()
        }
    }

    /**
     * Devuelve el nombre visible de un dispositivo BT,
     * o su dirección MAC si no hay permiso o el nombre está vacío.
     */
    fun getDeviceName(context: Context, device: BluetoothDevice): String {
        if (!hasBluetoothPermission(context)) return device.address
        return try {
            device.name?.takeIf { it.isNotBlank() } ?: device.address
        } catch (_: Exception) { device.address }
    }

    /**
     * Verifica si el permiso Bluetooth necesario está concedido
     * según la versión de Android del dispositivo.
     */
    fun hasBluetoothPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
}
