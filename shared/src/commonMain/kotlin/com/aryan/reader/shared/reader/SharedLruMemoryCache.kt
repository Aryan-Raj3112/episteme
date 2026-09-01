package com.aryan.reader.shared.reader

internal class SharedLruMemoryCache<K, V>(
    private val maxEntries: Int
) {
    private val entries = LinkedHashMap<K, V>()

    operator fun get(key: K): V? {
        val value = entries[key] ?: return null
        if (maxEntries > 1) {
            entries.remove(key)
            entries[key] = value
        }
        return value
    }

    operator fun set(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        trimToMaxEntries()
    }

    fun clear() {
        entries.clear()
    }

    private fun trimToMaxEntries() {
        while (entries.size > maxEntries) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}
