package io.github.nmanuelides.mtgreforged.platform

import java.io.File
import java.util.Properties

/**
 * Android's SharedPreferences, as much of it as the shared code uses: one small properties
 * file per name, read once and written back on every apply/commit.
 */
class Prefs internal constructor(private val file: File) {
    private val values = Properties().apply { if (file.isFile) runCatching { file.inputStream().use(::load) } }

    @Synchronized fun contains(key: String) = values.containsKey(key)
    @Synchronized fun getString(key: String, default: String?): String? = values.getProperty(key) ?: default
    @Synchronized fun getInt(key: String, default: Int) = values.getProperty(key)?.toIntOrNull() ?: default
    @Synchronized fun getLong(key: String, default: Long) = values.getProperty(key)?.toLongOrNull() ?: default
    @Synchronized fun getBoolean(key: String, default: Boolean) = values.getProperty(key)?.toBooleanStrictOrNull() ?: default

    @Synchronized
    fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        values.getProperty(key)?.let { raw -> if (raw.isEmpty()) emptySet() else raw.split(SEPARATOR).toSet() } ?: default

    fun edit() = Editor()

    inner class Editor internal constructor() {
        private val changes = LinkedHashMap<String, String?>()
        private var clearAll = false

        fun putString(key: String, value: String?): Editor { changes[key] = value; return this }
        fun putInt(key: String, value: Int): Editor { changes[key] = value.toString(); return this }
        fun putLong(key: String, value: Long): Editor { changes[key] = value.toString(); return this }
        fun putBoolean(key: String, value: Boolean): Editor { changes[key] = value.toString(); return this }
        fun putStringSet(key: String, value: Set<String>?): Editor { changes[key] = value?.joinToString(SEPARATOR); return this }
        fun remove(key: String): Editor { changes[key] = null; return this }
        fun clear(): Editor { clearAll = true; return this }

        fun commit(): Boolean = synchronized(this@Prefs) {
            if (clearAll) values.clear()
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values.setProperty(k, v) }
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.outputStream().use { values.store(it, null) }
                file.delete()
                tmp.renameTo(file)
            }.isSuccess
        }

        fun apply() {
            commit()
        }
    }

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
