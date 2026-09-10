package edu.practice.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val completed: Boolean,
)

@Entity(tableName = "metadata") data class Meta(@PrimaryKey val key: String, val value: String)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC") fun observe(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(task: TaskEntity)

    @Delete suspend fun delete(task: TaskEntity)

    @Query("SELECT value FROM metadata WHERE `key`='imported'") suspend fun imported(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun mark(meta: Meta)
}

@Database(entities = [TaskEntity::class, Meta::class], version = 1, exportSchema = false)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun dao(): TaskDao
}
