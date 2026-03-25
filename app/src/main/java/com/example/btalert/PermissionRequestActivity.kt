package com.example.btalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionRequestActivity : AppCompatActivity() {

    private val REQ = 201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ
                )
            } else {
                finishWithStartService()
            }
        } else {
            finishWithStartService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // ✅ Permiso concedido — mensaje positivo
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
                finishWithStartService()
            } else {
                // ✅ Permiso denegado — mensaje de advertencia
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_required),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun finishWithStartService() {
        try {
            val intent = Intent(this, BluetoothMonitorService::class.java).apply {
                putExtra("force_start_foreground", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finish()
    }
}
