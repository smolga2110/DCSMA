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
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.practice.presentation.PhotoViewModel
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
fun Screen(vm: PhotoViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.photos.find { it.id == selectedId }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
            uri ->
            val photo = selected
            if (uri != null && photo != null)
                scope.launch {
                    saving = true
                    try {
                        withContext(Dispatchers.IO) {
                            val connection =
                                java.net.URI(photo.downloadUrl).toURL().openConnection().apply {
                                    connectTimeout = 15000
                                    readTimeout = 30000
                                }
                            connection.getInputStream().use { input ->
                                ctx.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
                            }
                        }
                        message = "Фото сохранено"
                    } catch (e: Exception) {
                        message = "Ошибка сохранения фото"
                    } finally {
                        saving = false
                    }
                }
        }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected != null) {
            TextButton(onClick = { selectedId = null }) { Text("← Фото") }
            coil.compose.AsyncImage(
                selected.downloadUrl,
                contentDescription = "Фото ${selected.author}",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Text(
                "Автор: ${selected.author}\nРазмер: ${selected.width} × ${selected.height}\n${selected.url}"
            )
            Button(enabled = !saving, onClick = { save.launch("photo-${selected.id}.jpg") }) {
                Text(if (saving) "Сохранение…" else "Скачать фото")
            }
            Text("Выберите Downloads в системном диалоге", fontSize = 12.sp)
            Text(message)
        } else {
            Heading("Photos")
            if (state.loading) CircularProgressIndicator()
            if (state.error.isNotEmpty()) {
                Notice(state.error)
                Button(onClick = { vm.load() }) { Text("Повторить") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.photos, key = { it.id }) { p ->
                    Card(Modifier.clickable { selectedId = p.id }) {
                        coil.compose.AsyncImage(
                            "https://picsum.photos/id/${p.id}/400/300",
                            contentDescription = p.author,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.3f),
                        )
                        Text(p.author, Modifier.padding(horizontal = 8.dp))
                        Text("${p.width} × ${p.height}", Modifier.padding(8.dp), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
