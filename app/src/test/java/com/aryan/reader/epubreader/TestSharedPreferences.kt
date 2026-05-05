package com.aryan.reader.epubreader

import android.content.SharedPreferences

internal class TestSharedPreferences(vararg initial: Pair<String, Any?>) : SharedPreferences {
    private val values = initial.toMap().toMutableMap()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = values[key] as? Set<*> ?: return defValues
        return value.filterIsInstance<String>().toMutableSet()
    }
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyPut(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            applyPut(key, values?.toSet())
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyPut(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyPut(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyPut(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyPut(key, value)
        override fun remove(key: String?): SharedPreferences.Editor = applyPut(key, null)
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            flush()
            return true
        }
        override fun apply() = flush()

        private fun applyPut(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        private fun flush() {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
