package io.github.meko123456.nishani.shared

import platform.Foundation.NSUserDefaults

/** iOS-native [KeyValueStore] backed by NSUserDefaults. */
class UserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
