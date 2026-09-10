package edu.practice.domain

import kotlinx.coroutines.flow.Flow

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
)

interface TaskRepository {
    fun observe(): Flow<List<Task>>

    suspend fun initialize()

    suspend fun save(task: Task)

    suspend fun delete(task: Task)
}

class SaveTask(private val repo: TaskRepository) {
    suspend operator fun invoke(task: Task) {
        require(task.title.isNotBlank())
        repo.save(task)
    }
}

class DeleteTask(private val repo: TaskRepository) {
    suspend operator fun invoke(task: Task) = repo.delete(task)
}
