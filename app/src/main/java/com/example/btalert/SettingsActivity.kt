package com.example.btalert

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private val TAG = "SettingsActivity"
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var btnBack:            ImageView
    private lateinit var layoutDevice:       LinearLayout
    private lateinit var layoutSchedule:     LinearLayout
    private lateinit var layoutAlerts:       LinearLayout
    private lateinit var layoutLanguage:     LinearLayout
    private lateinit var layoutCapture:      LinearLayout
    private lateinit var layoutPro:          LinearLayout
    private lateinit var layoutHelp:         LinearLayout
    private lateinit var txtCurrentLanguage: TextView

    private var selectedLanguageCode: String = AppConfig.Languages.DEFAULT

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPrefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

        btnBack            = findViewById(R.id.btnBack)
        layoutDevice       = findViewById(R.id.layoutDevice)
        layoutSchedule     = findViewById(R.id.layoutSchedule)
        layoutAlerts       = findViewById(R.id.layoutAlerts)
        layoutLanguage     = findViewById(R.id.layoutLanguage)
        layoutCapture      = findViewById(R.id.layoutCapture)
        layoutPro          = findViewById(R.id.layoutPro)
        layoutHelp         = findViewById(R.id.layoutHelp)
        txtCurrentLanguage = findViewById(R.id.txtCurrentLanguage)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        loadLanguageFromPrefs()
        updateLanguageDisplay()

        btnBack.setOnClickListener { finish() }

        layoutDevice.setOnClickListener {
            startActivity(Intent(this, DeviceSettingsActivity::class.java))
        }

        layoutSchedule.setOnClickListener {
            startActivity(Intent(this, ScheduleSettingsActivity::class.java))
        }

        layoutAlerts.setOnClickListener {
            startActivity(Intent(this, AlertsSettingsActivity::class.java))
        }

        layoutLanguage.setOnClickListener {
            showLanguageSelector()
        }

        layoutCapture.setOnClickListener {
            startActivity(Intent(this, SosSettingsActivity::class.java))
        }

        layoutPro.setOnClickListener {
            startActivity(Intent(this, PinSettingsActivity::class.java))
        }

        layoutHelp.setOnClickListener {
            startActivity(Intent(this, ProSettingsActivity::class.java))
        }
    }

    // =========================================================
    // IDIOMA
    // =========================================================

    private fun loadLanguageFromPrefs() {
        selectedLanguageCode = sharedPrefs.getString(AppConfig.PrefsKeys.SELECTED_LANGUAGE, null)
            ?: Locale.getDefault().language

        if (!AppConfig.Languages.AVAILABLE_LANGUAGES.any { it.first == selectedLanguageCode }) {
            selectedLanguageCode = AppConfig.Languages.DEFAULT
        }
    }

    private fun updateLanguageDisplay() {
        val flag = AppConfig.Languages.getFlagFromCode(selectedLanguageCode)
        val name = when (selectedLanguageCode) {
            AppConfig.Languages.ENGLISH    -> getString(R.string.language_english)
            AppConfig.Languages.SPANISH    -> getString(R.string.language_spanish)
            AppConfig.Languages.PORTUGUESE -> getString(R.string.language_portuguese)
            AppConfig.Languages.GERMAN     -> getString(R.string.language_german)
            AppConfig.Languages.FRENCH     -> getString(R.string.language_french)
            else                           -> getString(R.string.language_english)
        }
        txtCurrentLanguage.text = "$flag $name"
    }

    private fun showLanguageSelector() {
        val languages = AppConfig.Languages.AVAILABLE_LANGUAGES.map { (code, flag) ->
            val name = when (code) {
                AppConfig.Languages.ENGLISH    -> getString(R.string.language_english)
                AppConfig.Languages.SPANISH    -> getString(R.string.language_spanish)
                AppConfig.Languages.PORTUGUESE -> getString(R.string.language_portuguese)
                AppConfig.Languages.GERMAN     -> getString(R.string.language_german)
                AppConfig.Languages.FRENCH     -> getString(R.string.language_french)
                else                           -> code
            }
            "$flag $name"
        }.toTypedArray()

        val currentIndex = AppConfig.Languages.getIndexFromCode(selectedLanguageCode)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.language_section)
            .setSingleChoiceItems(languages, currentIndex) { dialog: DialogInterface, which: Int ->
                val newCode = AppConfig.Languages.getCodeFromIndex(which)
                if (newCode != selectedLanguageCode) {
                    selectedLanguageCode = newCode

                    // Guarda el idioma elegido
                    sharedPrefs.edit()
                        .putString(AppConfig.PrefsKeys.SELECTED_LANGUAGE, selectedLanguageCode)
                        .apply()

                    // Actualiza el locale global (MyApplication debe leer este valor en onCreate)
                    MyApplication.updateLanguageCode(selectedLanguageCode)

                    dialog.dismiss()
                    showRestartDialog()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun showRestartDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.language_change_title)
            .setMessage(R.string.language_change_message)
            .setPositiveButton(R.string.language_change_restart) { _: DialogInterface, _: Int ->
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            .setNegativeButton(R.string.language_change_later) { _: DialogInterface, _: Int ->
                Toast.makeText(this, R.string.language_change_later, Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
}