package io.github.meko123456.nishani.shared

/**
 * Minimal persistent string map — the one thing that genuinely differs per platform,
 * so each app supplies a native implementation: SharedPreferences on Android,
 * NSUserDefaults on iOS. Everything above this (JSON, search) stays in shared Kotlin.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** In-memory store — the default for tests and previews. */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}
