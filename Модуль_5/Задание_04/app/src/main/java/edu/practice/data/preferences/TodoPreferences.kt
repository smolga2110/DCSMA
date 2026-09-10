package edu.practice.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.todoStore by preferencesDataStore("todo_preferences")

class TodoPreferences(private val context: Context) {
    private val enabled = booleanPreferencesKey("colored")
    private val selected = longPreferencesKey("color")
    val colored = context.todoStore.data.map { it[enabled] ?: false }
    val color = context.todoStore.data.map { it[selected] ?: 0xFF00EE00L }

    suspend fun setColored(value: Boolean) {
        context.todoStore.edit { it[enabled] = value }
    }

    suspend fun setColor(value: Long) {
        context.todoStore.edit { it[selected] = value }
    }
}
