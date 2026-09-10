package edu.practice.data.repository

import android.content.Context
import androidx.room.withTransaction
import edu.practice.data.local.*
import edu.practice.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
private data class Seed(
    val title: String,
    val description: String = "",
    val completed: Boolean = false,
)

class RoomTaskRepository(private val db: TodoDatabase, private val context: Context) :
    TaskRepository {
    override fun observe() =
        db.dao().observe().map { rows ->
            rows.map { Task(it.id, it.title, it.description, it.completed) }
        }

    override suspend fun initialize() {
        db.withTransaction {
            if (db.dao().imported() == null) {
                val seeds =
                    Json.decodeFromString<List<Seed>>(
                        context.assets.open("tasks.json").bufferedReader().use { it.readText() }
                    )
                seeds.forEach {
                    db.dao()
                        .save(
                            TaskEntity(
                                title = it.title,
                                description = it.description,
                                completed = it.completed,
                            )
                        )
                }
                db.dao().mark(Meta("imported", "true"))
            }
        }
    }

    override suspend fun save(task: Task) =
        db.dao().save(TaskEntity(task.id, task.title, task.description, task.completed))

    override suspend fun delete(task: Task) =
        db.dao().delete(TaskEntity(task.id, task.title, task.description, task.completed))
}
