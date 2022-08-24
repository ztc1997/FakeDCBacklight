package com.ztc1997.fakedcbacklight

import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

const val HAL_SCREEN_BRIGHTNESS = "COM_ZTC1997_FAKEDCBACKLIGHT_HAL_SCREEN_BRIGHTNESS"
const val REDUCE_BRIGHT_LEVEL = "COM_ZTC1997_FAKEDCBACKLIGHT_REDUCE_BRIGHT_LEVEL"

class Hook : IXposedHookLoadPackage {
    private val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "config")
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
                            "reduce_bright_colors_level",
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

                override fun afterHookedMethod(param: MethodHookParam) {
                    val localDisplayAdapter = XposedHelpers.getSurroundingThis(param.thisObject)
                    val ctx =
                        XposedHelpers.callMethod(
                            localDisplayAdapter,
                            "getOverlayContext"
                        ) as Context
                    val targetBright = param.args[1] as Float
                    Settings.System.putFloat(
                        ctx.contentResolver,
                        HAL_SCREEN_BRIGHTNESS,
                        targetBright
                    )
                    val level = Settings.Secure.getInt(
                        ctx.contentResolver,
                        "reduce_bright_colors_level",
                        0
                    )
                    Settings.System.putInt(
                        ctx.contentResolver,
                        REDUCE_BRIGHT_LEVEL,
                        level
                    )
                }
            })

        val displayPowerController = XposedHelpers.findClass(
            "com.android.server.display.DisplayPowerController",
            lpparam.classLoader
        )
        XposedHelpers.findAndHookMethod(
            displayPowerController,
            "applyReduceBrightColorsSplineAdjustment",
            Boolean::class.java,
            Boolean::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val enable = getBoolean("pref_enable", true)
                    if (enable)
                        param.result = null
                }
            })
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (prefs.hasFileChanged()) {
            prefs.reload()
        }
        return prefs.getBoolean(key, defValue)
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