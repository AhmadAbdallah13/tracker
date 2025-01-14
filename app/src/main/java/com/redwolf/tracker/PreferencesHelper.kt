package com.redwolf.tracker

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    fun saveToggledApps(toggledApps: Set<String>) {
        sharedPreferences.edit().putStringSet("ToggledApps", toggledApps).apply()
    }

    fun getToggledApps(): Set<String> {
        return sharedPreferences.getStringSet("ToggledApps", emptySet()) ?: emptySet()
    }
}