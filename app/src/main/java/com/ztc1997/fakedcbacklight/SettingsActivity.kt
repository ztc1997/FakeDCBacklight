package com.ztc1997.fakedcbacklight

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import android.provider.Settings
import kotlin.system.exitProcess

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    class SettingsFragment : PreferenceFragment() {
        private val enable by lazy { findPreference("pref_enable") as SwitchPreference }
        private val minScreenBright by lazy { findPreference("pref_min_screen_bright") as EditTextPreference }
        private val prefMaxDimStrength by lazy { findPreference("pref_max_dim_strength") as EditTextPreference }
        private val halBright by lazy { findPreference("pref_hal_brightness") as Preference }
        private var sharedPreferences: SharedPreferences? = null
        private val halBrightObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateHalBright()
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_settings)

            val sp: SharedPreferences?
            try {
                sp = activity.getSharedPreferences(
                    "config",
                    MODE_WORLD_READABLE
                )
            } catch (exception: SecurityException) {
                AlertDialog.Builder(activity).apply {
                    setTitle(R.string.Tips)
                    setMessage(R.string.not_support)
                    setCancelable(false)
                    setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                        exitProcess(0)
                    }
                }.show()
                return
            }
            sharedPreferences = sp

            enable.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { prf, value ->
                    value as Boolean
                    sp.edit().putBoolean(prf.key, value).apply()
                    true
                }
            minScreenBright.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { prf, value ->
                    val f = (value as String).toFloat() / 100
                    val valid = f > 0.0
                    if (valid) {
                        sp.edit().putFloat(prf.key, f).apply()
                        minScreenBright.summary =
                            "${activity.getText(R.string.summary_pref_min_screen_bright)}\nCurrent: $value%"
                    }
                    valid
                }
            prefMaxDimStrength.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { prf, value ->
                    val i = (value as String).toInt()
                    val valid = i in 0..110
                    if (valid) {
                        sp.edit().putInt(prf.key, i).apply()
                        prefMaxDimStrength.summary =
                            "${activity.getText(R.string.summary_pref_max_dim_strength)}\nCurrent: $value%"
                    }
                    valid
                }
        }

        override fun onResume() {
            super.onResume()
            val sp = sharedPreferences ?: return

            minScreenBright.summary =
                "${activity.getText(R.string.summary_pref_min_screen_bright)}\nCurrent: ${
                    (sp.getFloat(
                        "pref_min_screen_bright",
                        1.0f
                    ) * 100)
                }%"

            prefMaxDimStrength.summary =
                "${activity.getText(R.string.summary_pref_max_dim_strength)}\nCurrent: ${
                    sp.getInt(
                        "pref_max_dim_strength",
                        90
                    )
                }%"

            updateHalBright()

            activity.contentResolver.registerContentObserver(
                Settings.System.getUriFor(HAL_SCREEN_BRIGHTNESS), true,
                halBrightObserver
            )
        }

        override fun onPause() {
            super.onPause()
            activity.contentResolver.unregisterContentObserver(halBrightObserver)
        }

        private fun updateHalBright() {
            try {
                activity.contentResolver?.let {
                    val bright = Settings.System.getFloat(it, HAL_SCREEN_BRIGHTNESS)
                    halBright.summary = "${bright * 100}%"
                }
            } catch (_: Settings.SettingNotFoundException) {
            }
        }
    }
}