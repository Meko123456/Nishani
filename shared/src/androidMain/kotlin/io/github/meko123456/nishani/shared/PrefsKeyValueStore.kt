package io.github.meko123456.nishani.shared

import android.content.Context

/** Android-native [KeyValueStore] backed by SharedPreferences. */
class PrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("nishani", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
