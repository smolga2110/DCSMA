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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.Locale
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

data class Entry(val file: String, val title: String, val text: String, val date: Long)

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val error = MutableStateFlow("")
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val dir = File(app.filesDir, "diary").apply { mkdirs() }

    init {
        viewModelScope.launch {
            mutex.lock()
            try {
                entries.value =
                    withContext(Dispatchers.IO) {
                        dir.listFiles()
                            ?.filter { it.extension == "txt" }
                            ?.map { read(it) }
                            ?.sortedByDescending { it.date } ?: emptyList()
                    }
            } catch (e: Exception) {
                error.value = "Не удалось прочитать записи"
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun read(f: File): Entry {
        val text = f.readText()
        return Entry(
            f.name,
            text.substringBefore('\n'),
            text.substringAfter('\n', ""),
            f.name.substringBefore('_').toLongOrNull() ?: f.lastModified(),
        )
    }

    fun save(old: String?, title: String, text: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            mutex.lock()
            try {
                val item =
                    withContext(Dispatchers.IO) {
                        val safe = title.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(40)
                        var stamp = System.currentTimeMillis()
                        while (File(dir, "${stamp}_${safe}.txt").exists()) stamp++
                        val file = File(dir, old ?: "${stamp}_${safe}.txt")
                        val atomic = android.util.AtomicFile(file)
                        val stream = atomic.startWrite()
                        try {
                            stream.write((title + "\n" + text).toByteArray())
                            atomic.finishWrite(stream)
                        } catch (e: Exception) {
                            atomic.failWrite(stream)
                            throw e
                        }
                        read(file)
                    }
                entries.value =
                    if (old == null) listOf(item) + entries.value
                    else entries.value.map { if (it.file == old) item else it }
                error.value = ""
                onSaved()
            } catch (e: Exception) {
                error.value = "Не удалось сохранить запись"
            } finally {
                mutex.unlock()
            }
        }
    }

    fun delete(item: Entry) {
        viewModelScope.launch {
            mutex.lock()
            try {
                withContext(Dispatchers.IO) {
                    if (!File(dir, item.file).delete()) error("Файл не удалён")
                }
                entries.value = entries.value.filterNot { it.file == item.file }
            } catch (e: Exception) {
                error.value = "Не удалось удалить запись"
            } finally {
                mutex.unlock()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Screen(vm: DiaryViewModel = viewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var edit by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (edit) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { edit = false }) { Text("← Назад") }
                Heading(if (current == null) "Новая запись" else "Редактировать")
            }
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Заголовок (опционально)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                body,
                { body = it },
                label = { Text("Ваша запись…") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Button(
                enabled = body.isNotBlank(),
                onClick = { vm.save(current, title, body) { edit = false } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить запись")
            }
        } else {
            Heading("Личный дневник")
            if (entries.isEmpty())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        "У вас пока нет записей\nНажмите +, чтобы создать первую",
                        Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center,
                    )
                }
            else
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.file }) { item ->
                        var menu by remember { mutableStateOf(false) }
                        Card(
                            Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        current = item.file
                                        title = item.title
                                        body = item.text
                                        edit = true
                                    },
                                    onLongClick = { menu = true },
                                )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    item.title.ifBlank { "Без заголовка" },
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(item.text.take(40))
                                Text(
                                    java.text
                                        .SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                        .format(java.util.Date(item.date)),
                                    fontSize = 12.sp,
                                )
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        onClick = {
                                            menu = false
                                            vm.delete(item)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            FloatingActionButton(
                onClick = {
                    current = null
                    title = ""
                    body = ""
                    edit = true
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("+", fontSize = 26.sp)
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}
