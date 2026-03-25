package com.example.btalert

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit

class DeviceSettingsActivity : BaseActivity() {

    private val TAG = "DeviceSettingsActivity"
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnBack:          ImageView
    private lateinit var layoutDevice:     LinearLayout
    private lateinit var txtCurrentDevice: TextView

    private var pairedDevices:    List<BluetoothDevice>? = null
    private var selectedDeviceMac: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_settings)

        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        btnBack          = findViewById(R.id.btnBack)
        layoutDevice     = findViewById(R.id.layoutDevice)
        txtCurrentDevice = findViewById(R.id.txtCurrentDevice)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        loadSavedDevice()
        loadPairedDevices()

        btnBack.setOnClickListener      { finish() }
        layoutDevice.setOnClickListener { showDeviceSelector() }
    }

    // =========================================================
    // CARGA DE DISPOSITIVOS
    // =========================================================

    private fun loadSavedDevice() {
        selectedDeviceMac = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC, null)
    }

    private fun loadPairedDevices() {
        val btManager = getSystemService(BluetoothManager::class.java)
        if (btManager?.adapter?.isEnabled != true) {
            pairedDevices = emptyList()
            updateDeviceDisplay()
            return
        }

        pairedDevices = BluetoothHelper.getConnectedDevices(this)
        Log.d(TAG, "Dispositivos conectados: ${pairedDevices?.size}, selectedMac=$selectedDeviceMac")
        updateDeviceDisplay()
    }

    private fun getDeviceDisplayName(device: BluetoothDevice): String =
        BluetoothHelper.getDeviceName(this, device)

    // =========================================================
    // UI
    // =========================================================

    private fun updateDeviceDisplay() {
        val savedName = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, null)
        val device    = pairedDevices?.find { it.address == selectedDeviceMac }

        txtCurrentDevice.text = when {
            device != null                                  -> getDeviceDisplayName(device)
            selectedDeviceMac != null && savedName != null -> savedName
            selectedDeviceMac != null                      -> selectedDeviceMac!!
            else                                           -> getString(R.string.no_device_selected)
        }
    }

    // =========================================================
    // SELECTOR
    // =========================================================

    private fun showDeviceSelector() {
        val devices = pairedDevices
        if (devices.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.bluetooth_device)
                .setMessage(R.string.no_devices)
                .setPositiveButton(R.string.dialog_button_ok, null)
                .show()
            return
        }

        val deviceNames  = devices.map { getDeviceDisplayName(it) }.toTypedArray()
        val currentIndex = devices.indexOfFirst { it.address == selectedDeviceMac }

        AlertDialog.Builder(this)
            .setTitle(R.string.bluetooth_device)
            .setSingleChoiceItems(deviceNames, currentIndex) { dialog: DialogInterface, which: Int ->
                devices.getOrNull(which)?.let { device ->
                    selectedDeviceMac = device.address
                    val deviceName = getDeviceDisplayName(device)
                    sharedPrefs.edit(commit = true) {
                        putString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC,  selectedDeviceMac)
                        putString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, deviceName)
                    }
                    saveToProtectedStorage(deviceName)
                    updateDeviceDisplay()
                    dialog.dismiss()
                    Toast.makeText(this, "✓ ${getString(R.string.settings_saved_toast)}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun saveToProtectedStorage(deviceName: String) {
        try {
            applicationContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    putString(AppConfig.PrefsKeys.SELECTED_DEVICE_MAC,  selectedDeviceMac)
                    putString(AppConfig.PrefsKeys.SELECTED_DEVICE_NAME, deviceName)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Protected storage no disponible", e)
        }
    }
}