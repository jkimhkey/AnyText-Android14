package com.hhvvg.anytext.utils

import android.os.Build
import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * Android 14 compatibility utilities
 */
object Android14Compat {
    private const val TAG = "AnyText14Compat"
    
    /**
     * Check if running on Android 14 or above
     */
    fun isAndroid14OrAbove(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
    
    /**
     * Safe logging for Android 14
     */
    fun log(message: String) {
        try {
            if (isAndroid14OrAbove()) {
                Log.d(TAG, "Android14: $message")
            }
            XposedBridge.log("AnyText: $message")
        } catch (e: Exception) {
            // Ignore log errors
        }
    }
    
    /**
     * Get enhanced hook targets for Android 14
     */
    fun getEnhancedHookTargets(): Array<String> {
        return if (isAndroid14OrAbove()) {
            arrayOf(
                "android.widget.TextView",
                "android.widget.EditText", 
                "android.widget.Button",
                "android.widget.CheckBox",
                "android.widget.RadioButton",
                "android.widget.Switch",
                "android.widget.ToggleButton",
                "androidx.appcompat.widget.AppCompatTextView",
                "androidx.appcompat.widget.AppCompatEditText",
                "androidx.appcompat.widget.AppCompatButton",
                "androidx.appcompat.widget.AppCompatCheckBox",
                "androidx.appcompat.widget.AppCompatRadioButton",
                "androidx.appcompat.widget.AppCompatSwitch",
                "com.google.android.material.textview.MaterialTextView",
                "com.google.android.material.button.MaterialButton",
                "com.google.android.material.checkbox.MaterialCheckBox"
            )
        } else {
            arrayOf(
                "android.widget.TextView",
                "android.widget.EditText",
                "android.widget.Button"
            )
        }
    }
}