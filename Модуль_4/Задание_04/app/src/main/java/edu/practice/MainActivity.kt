package edu.practice

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = Color(0xFF006D85),
                        background = Color(0xFFF1FAFE),
                        surface = Color(0xFFF1FAFE),
                        surfaceVariant = Color(0xFFDEE5EC),
                    )
            ) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

@Composable
fun Center(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
fun Heading(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
fun Notice(text: String) {
    Text(text, textAlign = TextAlign.Center)
}

@Composable
fun NotificationPermission() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

fun notification(context: Context, text: String, ongoing: Boolean = false): Notification {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            "practice",
            "Практическая работа",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
    val pending =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    return Notification.Builder(context, "practice")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(text)
        .setContentText("Разработка мобильных приложений")
        .setContentIntent(pending)
        .setOngoing(ongoing)
        .setAutoCancel(!ongoing)
        .build()
}

fun notify(context: Context, id: Int, text: String) {
    if (
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    )
        context
            .getSystemService(NotificationManager::class.java)
            .notify(id, notification(context, text))
}

@Serializable
data class Post(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val avatarUrl: String,
)

@Serializable data class Comment(val postId: Int, val id: Int, val name: String, val body: String)

data class PostState(
    val post: Post,
    val loading: Boolean = true,
    val avatar: String? = null,
    val comments: List<Comment> = emptyList(),
    val error: String = "",
)

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var rows by remember { mutableStateOf(emptyList<PostState>()) }
    var error by remember { mutableStateOf("") }
    var failures by remember { mutableStateOf(false) }
    fun refresh() {
        job?.cancel()
        job =
            scope.launch {
                try {
                    val (posts, comments) =
                        withContext(Dispatchers.IO) {
                            Json.decodeFromString<List<Post>>(
                                ctx.assets.open("social_posts.json").bufferedReader().use {
                                    it.readText()
                                }
                            ) to
                                Json.decodeFromString<List<Comment>>(
                                    ctx.assets.open("comments.json").bufferedReader().use {
                                        it.readText()
                                    }
                                )
                        }
                    rows = posts.map { PostState(it) }
                    supervisorScope {
                        posts.forEach { p ->
                            launch {
                                suspend fun <T> safe(block: suspend () -> T): Result<T> =
                                    try {
                                        Result.success(block())
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                val avatar = async {
                                    safe {
                                        delay(350L + p.id * 90)
                                        if (failures && p.id % 4 == 0) error("Аватар недоступен")
                                        p.avatarUrl
                                    }
                                }
                                val discussion = async {
                                    safe {
                                        delay(600L + p.id * 130)
                                        if (failures && p.id % 3 == 0)
                                            error("Комментарии недоступны")
                                        comments.filter { it.postId == p.id }
                                    }
                                }
                                val a = avatar.await()
                                val c = discussion.await()
                                rows =
                                    rows.map {
                                        if (it.post.id == p.id)
                                            PostState(
                                                p,
                                                false,
                                                a.getOrNull(),
                                                c.getOrDefault(emptyList()),
                                                listOfNotNull(
                                                        a.exceptionOrNull()?.message,
                                                        c.exceptionOrNull()?.message,
                                                    )
                                                    .joinToString("; "),
                                            )
                                        else it
                                    }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Ошибка чтения ленты"
                }
            }
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Heading("Социальная лента")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { refresh() }) { Text("Обновить") }
            Checkbox(failures, { failures = it })
            Text("Сбои")
        }
        if (error.isNotEmpty()) Text(error)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows, key = { it.post.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp)
                                    .background(
                                        if (r.avatar == null) Color.Gray else Color(0xFF58A6B8)
                                    )
                            ) {
                                Text(
                                    if (r.avatar == null) "?" else r.post.userId.toString(),
                                    Modifier.align(Alignment.Center),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(r.post.title, fontWeight = FontWeight.Bold)
                        }
                        Text(r.post.body)
                        if (r.loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Loading")
                        } else {
                            Text(if (r.error.isEmpty()) "Ready" else "Error: ${r.error}")
                            r.comments.forEach { Text("${it.name}: ${it.body}") }
                        }
                    }
                }
            }
        }
    }
}
