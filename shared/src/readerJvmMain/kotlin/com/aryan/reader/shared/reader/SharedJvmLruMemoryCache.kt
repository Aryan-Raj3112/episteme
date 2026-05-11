package com.aryan.reader.shared.reader

internal class SharedJvmLruMemoryCache<K, V>(
    private val maxEntries: Int
) {
    private val entries = LinkedHashMap<K, V>(maxEntries, 0.75f, true)

    operator fun get(key: K): V? {
        return entries[key]
    }

    operator fun set(key: K, value: V) {
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
