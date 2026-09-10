package edu.practice.presentation

import android.app.Application
import androidx.lifecycle.*
import androidx.room.Room
import edu.practice.data.local.*
import edu.practice.data.preferences.*
import edu.practice.data.repository.*
import edu.practice.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TodoViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, TodoDatabase::class.java, "todo.db").build()
    private val repo = RoomTaskRepository(db, app)
    private val preferences = TodoPreferences(app)
    private val saveTask = SaveTask(repo)
    private val deleteTask = DeleteTask(repo)
    val tasks =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val colored =
        preferences.colored.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val color =
        preferences.color.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF00EE00L)
    val error = MutableStateFlow("")

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.initialize()
            } catch (e: Exception) {
                error.value = "Ошибка импорта задач"
            }
        }
    }

    fun save(task: Task, done: () -> Unit) {
        viewModelScope.launch {
            try {
                saveTask(task)
                error.value = ""
                done()
            } catch (e: Exception) {
                error.value = "Ошибка сохранения"
            }
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            try {
                deleteTask(task)
            } catch (e: Exception) {
                error.value = "Ошибка удаления"
            }
        }
    }

    fun setColored(value: Boolean) {
        viewModelScope.launch { preferences.setColored(value) }
    }

    fun setColor(value: Long) {
        viewModelScope.launch { preferences.setColor(value) }
    }

    override fun onCleared() {
        db.close()
    }
}
