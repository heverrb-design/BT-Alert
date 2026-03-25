package com.example.btalert

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import java.util.Calendar

class ScheduleSettingsActivity : BaseActivity() {

    private val TAG = "ScheduleSettingsActivity"
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnBack:        ImageView
    private lateinit var layoutStartTime: LinearLayout
    private lateinit var layoutEndTime:   LinearLayout
    private lateinit var txtStartTime:    TextView
    private lateinit var txtEndTime:      TextView

    private var startHour:   Int = 0
    private var startMinute: Int = 0
    private var endHour:     Int = 22
    private var endMinute:   Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_settings)

        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        btnBack         = findViewById(R.id.btnBack)
        layoutStartTime = findViewById(R.id.layoutStartTime)
        layoutEndTime   = findViewById(R.id.layoutEndTime)
        txtStartTime    = findViewById(R.id.txtStartTime)
        txtEndTime      = findViewById(R.id.txtEndTime)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { saveAndExit() }
        })

        loadSavedSchedule()

        btnBack.setOnClickListener          { saveAndExit() }
        layoutStartTime.setOnClickListener  { showStartTimePicker() }
        layoutEndTime.setOnClickListener    { showEndTimePicker() }
    }

    // =========================================================
    // CARGA Y GUARDADO
    // =========================================================

    private fun loadSavedSchedule() {
        val calendar = Calendar.getInstance()

        startHour   = sharedPrefs.getInt(AppConfig.PrefsKeys.START_HOUR,   calendar.get(Calendar.HOUR_OF_DAY))
        startMinute = sharedPrefs.getInt(AppConfig.PrefsKeys.START_MINUTE, calendar.get(Calendar.MINUTE))
        endHour     = sharedPrefs.getInt(AppConfig.PrefsKeys.END_HOUR,     22)
        endMinute   = sharedPrefs.getInt(AppConfig.PrefsKeys.END_MINUTE,   0)

        updateTimeDisplay()
        Log.d(TAG, "Schedule loaded: $startHour:$startMinute → $endHour:$endMinute")
    }

    private fun saveSchedule() {
        sharedPrefs.edit(commit = true) {
            putInt(AppConfig.PrefsKeys.START_HOUR,   startHour)
            putInt(AppConfig.PrefsKeys.START_MINUTE, startMinute)
            putInt(AppConfig.PrefsKeys.END_HOUR,     endHour)
            putInt(AppConfig.PrefsKeys.END_MINUTE,   endMinute)
        }
        try {
            applicationContext
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    putInt(AppConfig.PrefsKeys.START_HOUR,   startHour)
                    putInt(AppConfig.PrefsKeys.START_MINUTE, startMinute)
                    putInt(AppConfig.PrefsKeys.END_HOUR,     endHour)
                    putInt(AppConfig.PrefsKeys.END_MINUTE,   endMinute)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Protected storage no disponible", e)
        }
    }

    private fun saveAndExit() {
        saveSchedule()
        Toast.makeText(this, "✓ ${getString(R.string.settings_saved_toast)}", Toast.LENGTH_SHORT).show()
        finish()
    }

    // =========================================================
    // UI
    // =========================================================

    private fun updateTimeDisplay() {
        txtStartTime.text = String.format("%02d:%02d", startHour,   startMinute)
        txtEndTime.text   = String.format("%02d:%02d", endHour,     endMinute)
    }

    // =========================================================
    // TIME PICKERS
    // =========================================================

    private fun showStartTimePicker() {
        TimePickerDialog(this, { _, hourOfDay, minute ->
            startHour   = hourOfDay
            startMinute = minute
            updateTimeDisplay()
            Log.d(TAG, "Start time set: $startHour:$startMinute")
        }, startHour, startMinute, true).apply {
            setTitle(R.string.start_time_label)
            show()
        }
    }

    private fun showEndTimePicker() {
        TimePickerDialog(this, { _, hourOfDay, minute ->
            endHour   = hourOfDay
            endMinute = minute
            updateTimeDisplay()
            Log.d(TAG, "End time set: $endHour:$endMinute")
        }, endHour, endMinute, true).apply {
            setTitle(R.string.end_time_label)
            show()
        }
    }
}