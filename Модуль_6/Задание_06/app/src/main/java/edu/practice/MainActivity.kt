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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.practice.presentation.NobelViewModel
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

@Composable
fun Screen(vm: NobelViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var year by rememberSaveable { mutableStateOf("2023") }
    var category by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<edu.practice.domain.Prize?>(null) }
    var detail by remember { mutableStateOf<edu.practice.domain.Laureate?>(null) }
    val scope = rememberCoroutineScope()
    var detailLoading by remember { mutableStateOf(false) }
    val authenticated by vm.authenticated.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf("student") }
    var password by rememberSaveable { mutableStateOf("Student123!") }
    var base by rememberSaveable { mutableStateOf("http://10.0.2.2:8080") }
    if (!authenticated) {
        Center {
            Heading("Вход в Nobel Prize API")
            OutlinedTextField(base, { base = it }, label = { Text("Адрес сервера") })
            OutlinedTextField(login, { login = it }, label = { Text("Логин") })
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(onClick = { vm.login(base, login, password) }) { Text("Войти") }
            Text(state.error)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected != null) {
            val p = selected!!
            TextButton(
                onClick = {
                    selected = null
                    detail = null
                }
            ) {
                Text("← Детали премии")
            }
            Text("Год: ${p.year}\nКатегория: ${p.category}")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(p.laureates, key = { it.id }) { l ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                detailLoading = true
                                try {
                                    detail = vm.detail(l)
                                } finally {
                                    detailLoading = false
                                }
                            }
                        }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(l.fullName, fontWeight = FontWeight.Bold)
                            Text("Доля: ${l.portion}")
                            Text(l.motivation)
                            Text("Подробнее о лауреате →", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (detailLoading) CircularProgressIndicator()
            detail?.let { l ->
                AlertDialog(
                    onDismissRequest = { detail = null },
                    title = { Text(l.fullName) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(l.motivation)
                            Text("Страна рождения: ${l.birthCountry.ifBlank{"Нет данных"}}")
                            if (l.portraitUrl.isNotBlank())
                                coil.compose.AsyncImage(
                                    l.portraitUrl,
                                    contentDescription = l.fullName,
                                )
                        }
                    },
                    confirmButton = { TextButton(onClick = { detail = null }) { Text("Закрыть") } },
                )
            }
        } else {
            Heading("Нобелевские премии")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    year,
                    { year = it },
                    label = { Text("Год") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(category.ifBlank { "Все категории" })
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf(
                                "",
                                "physics",
                                "chemistry",
                                "literature",
                                "peace",
                                "medicine",
                                "economics",
                            )
                            .forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.ifBlank { "Все категории" }) },
                                    onClick = {
                                        category = c
                                        expanded = false
                                    },
                                )
                            }
                    }
                }
            }
            Button(onClick = { vm.load(year, category) }, enabled = !state.loading) {
                Text("Фильтр")
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error.isNotEmpty()) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.load(year, category) }) { Text("Повторить") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.prizes, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().clickable { selected = p }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${p.year} — ${p.category.uppercase()}",
                                fontWeight = FontWeight.Bold,
                            )
                            p.laureates.forEach {
                                Text(it.fullName)
                                Text(it.motivation.take(100), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
