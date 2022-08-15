package com.ztc1997.fakedcbacklight

import android.content.Context
import android.content.Intent
import android.provider.Settings
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.*

const val ACTION_REPORT_CURR_BRIGHT = "com.ztc1997.fakedcbacklight.ACTION_REPORT_CURR_BRIGHT"

class Hook : IXposedHookLoadPackage {
    private var prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "config")
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        val localDisplayDevice = XposedHelpers.findClass(
            "com.android.server.display.LocalDisplayAdapter\$LocalDisplayDevice",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(localDisplayDevice,
            "requestDisplayStateLocked",
            Int::class.java,
            Float::class.java,
            Float::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val localDisplayAdapter = XposedHelpers.getSurroundingThis(param.thisObject)
                    val ctx =
                        XposedHelpers.callMethod(
                            localDisplayAdapter,
                            "getOverlayContext"
                        ) as Context
                    val targetBright = param.args[1] as Float

                    if (getBoolean("report_curr_bright", false)) {
                        XposedBridge.log("Fake DC Backlight: HAL Brightness: ${targetBright * 100}%")
                    }


                    val enable = getBoolean("pref_enable", true)
                    val preEnable =
                        XposedHelpers.getAdditionalInstanceField(param.thisObject, "preEnable")
                    XposedHelpers.setAdditionalInstanceField(param.thisObject, "preEnable", enable)
                    if (!enable) {
                        if (preEnable is Boolean && preEnable)
                            Settings.Secure.putInt(
                                ctx.contentResolver,
                                "reduce_bright_colors_activated",
                                0
                            )
                        return
                    }

                    val minScreenBright = getFloat("pref_min_screen_bright", 1f)
                    if (targetBright >= minScreenBright ||
                        targetBright < 0
                    ) {
                        Settings.Secure.putInt(
                            ctx.contentResolver,
                            "reduce_bright_colors_activated",
                            0
                        )
                    } else {
                        val dim = (1 - (targetBright / minScreenBright)) * getInt(
                            "pref_max_dim_strength",
                            90
                        )
                        Settings.Secure.putInt(
                            ctx.contentResolver,
                            "reduce_bright_colors_level",
                            dim.toInt()
                        )
                        Settings.Secure.putInt(
                            ctx.contentResolver,
                            "reduce_bright_colors_activated",
                            1
                        )
                        param.args[1] = minScreenBright
                    }
                }
            })
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return getBooleanHasChanged(key, defValue).first
    }

    fun getBooleanHasChanged(key: String, defValue: Boolean): Pair<Boolean, Boolean> {
        val hasFileChanged = prefs.hasFileChanged()
        if (hasFileChanged) {
            prefs.reload()
        }
        return Pair(prefs.getBoolean(key, defValue), hasFileChanged)
    }

    fun getFloat(key: String, defValue: Float): Float {
        if (prefs.hasFileChanged()) {
            prefs.reload()
        }
        return prefs.getFloat(key, defValue)
    }

    fun getInt(key: String, defValue: Int): Int {
        if (prefs.hasFileChanged()) {
            prefs.reload()
        }
        return prefs.getInt(key, defValue)
    }
}