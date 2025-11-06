package com.example.xcpro.tasks

import android.content.SharedPreferences
import com.example.xcpro.tasks.core.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCoordinatorPersistenceTest {

    private val logLines = mutableListOf<String>()
    private val log: (String) -> Unit = { logLines += it }

    @Test
    fun `saveTaskType stores value in preferences`() {
        val prefs = FakeSharedPreferences()
        val persistence = TaskCoordinatorPersistence(
            prefs = prefs,
            loadRacingTask = { true },
            loadAATTask = { true },
            log = log
        )

        persistence.saveTaskType(TaskType.AAT)

        assertEquals(TaskType.AAT.name, prefs.data["current_task_type"])
        assertTrue(logLines.any { it.contains("Saved task type: AAT") })
    }

    @Test
    fun `loadTaskType returns stored value`() {
        val prefs = FakeSharedPreferences().apply {
            data["current_task_type"] = TaskType.AAT.name
        }
        val persistence = TaskCoordinatorPersistence(
            prefs = prefs,
            loadRacingTask = { true },
            loadAATTask = { false },
            log = log
        )

        val loaded = persistence.loadTaskType(TaskType.RACING)

        assertEquals(TaskType.AAT, loaded)
        assertTrue(logLines.any { it.contains("Loaded task type: AAT") })
    }

    @Test
    fun `loadTaskType falls back to default when stored value invalid`() {
        val prefs = FakeSharedPreferences().apply {
            data["current_task_type"] = "UNKNOWN_VALUE"
        }
        val persistence = TaskCoordinatorPersistence(
            prefs = prefs,
            loadRacingTask = { true },
            loadAATTask = { true },
            log = log
        )

        val loaded = persistence.loadTaskType(TaskType.RACING)

        assertEquals(TaskType.RACING, loaded)
    }

    @Test
    fun `loadSavedTasks returns combined status`() {
        val persistence = TaskCoordinatorPersistence(
            prefs = null,
            loadRacingTask = { true },
            loadAATTask = { false },
            log = log
        )

        val result = persistence.loadSavedTasks()

        assertTrue(result.racingLoaded)
        assertEquals(false, result.aatLoaded)
        assertTrue(logLines.any { it.contains("Finished loading saved tasks") })
    }

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, String?>()

        override fun getString(key: String?, defValue: String?): String? =
            data[key] ?: defValue

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) data[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) data.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                data.clear()
                return this
            }

            override fun commit(): Boolean = true

            override fun apply() = Unit

            override fun putLong(key: String?, value: Long) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putBoolean(key: String?, value: Boolean) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = this
        }

        override fun getAll(): MutableMap<String, *> = data
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?
        ): MutableSet<String>? = null

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
