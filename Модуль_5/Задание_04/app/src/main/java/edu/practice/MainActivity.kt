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
import edu.practice.domain.Task
import edu.practice.presentation.TodoViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(vm: TodoViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val colored by vm.colored.collectAsStateWithLifecycle()
    val color by vm.color.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var id by rememberSaveable { mutableStateOf(0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var completed by rememberSaveable { mutableStateOf(false) }
    var chooseColor by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todo List") },
                actions = {
                    TextButton(onClick = { chooseColor = true }) { Text("Цвет завершённых") }
                    Switch(colored, { vm.setColored(it) })
                },
            )
        },
        floatingActionButton = {
            if (!editing)
                FloatingActionButton(
                    onClick = {
                        id = 0
                        title = ""
                        description = ""
                        completed = false
                        editing = true
                    }
                ) {
                    Text("+", fontSize = 26.sp)
                }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                Heading(if (id == 0L) "Новая задача" else "Изменить задачу")
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Название задачи") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Описание (опционально)") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                Row {
                    TextButton(onClick = { editing = false }) { Text("Отмена") }
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = {
                            vm.save(Task(id, title.trim(), description, completed)) {
                                editing = false
                            }
                        },
                    ) {
                        Text("Сохранить задачу")
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        Card(
                            Modifier.fillMaxWidth().clickable {
                                id = task.id
                                title = task.title
                                description = task.description
                                completed = task.completed
                                editing = true
                            },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (colored && task.completed) Color(color)
                                        else Color(0xFFDEDEDE)
                                ),
                        ) {
                            Row(
                                Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(task.completed, { vm.save(task.copy(completed = it)) {} })
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.Bold)
                                    Text(task.description, fontSize = 12.sp)
                                }
                                TextButton(onClick = { vm.delete(task) }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
    if (chooseColor)
        AlertDialog(
            onDismissRequest = { chooseColor = false },
            title = { Text("Цвет завершённых задач") },
            text = {
                Row {
                    listOf(0xFF00EE00L, 0xFFB9E4C9L, 0xFFBBDCF4L, 0xFFFFDFAAL).forEach { c ->
                        Box(
                            Modifier.padding(5.dp).size(46.dp).background(Color(c)).clickable {
                                vm.setColor(c)
                                chooseColor = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseColor = false }) { Text("Закрыть") } },
        )
}
