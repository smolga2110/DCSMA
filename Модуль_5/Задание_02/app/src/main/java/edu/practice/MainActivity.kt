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

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    val photos = MutableStateFlow<List<File>>(emptyList())
    val error = MutableStateFlow("")
    private val dir =
        app.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(app.filesDir, "photos")

    init {
        refresh()
    }

    fun newFile(): File {
        dir.mkdirs()
        val base =
            "IMG_" +
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        var f = File(dir, "$base.jpg")
        var n = 1
        while (f.exists()) f = File(dir, "${base}_${n++}.jpg")
        f.createNewFile()
        return f
    }

    fun refresh() {
        viewModelScope.launch {
            photos.value =
                withContext(Dispatchers.IO) {
                    dir.listFiles()
                        ?.filter { it.extension == "jpg" && it.length() > 0 }
                        ?.sortedByDescending { it.name } ?: emptyList()
                }
        }
    }
}

suspend fun exportPhoto(ctx: Context, file: File): Boolean =
    withContext(Dispatchers.IO) {
        var uri: android.net.Uri? = null
        try {
            val values =
                ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(
                            android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/MyGallery",
                        )
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
            uri =
                ctx.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@withContext false
            ctx.contentResolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= 29)
                ctx.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
            true
        } catch (e: Exception) {
            uri?.let { ctx.contentResolver.delete(it, null, null) }
            false
        }
    }

@Composable
fun Screen(vm: GalleryViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pending by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionExport by remember { mutableStateOf<File?>(null) }
    fun export(file: File) {
        scope.launch {
            snackbar.showSnackbar(
                if (exportPhoto(ctx, file)) "Фото добавлено в галерею"
                else "Не удалось экспортировать фото"
            )
        }
    }
    val storage =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) permissionExport?.let { export(it) }
            else scope.launch { snackbar.showSnackbar("Доступ к галерее запрещён") }
        }
    fun beginExport(file: File) {
        if (
            Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionExport = file
            storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else export(file)
    }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (!ok && pending.isNotEmpty()) File(pending).delete()
            vm.refresh()
        }
    fun take() {
        try {
            val f = vm.newFile()
            pending = f.absolutePath
            camera.launch(
                androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            )
        } catch (e: Exception) {
            scope.launch { snackbar.showSnackbar("Камера недоступна") }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) take() else scope.launch { snackbar.showSnackbar("Доступ к камере запрещён") }
        }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selected == null)
                FloatingActionButton(
                    onClick = {
                        if (
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                        )
                            take()
                        else permission.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text("+", fontSize = 26.sp)
                }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected != null) {
                TextButton(onClick = { selected = null }) { Text("← Назад") }
                coil.compose.AsyncImage(
                    File(selected!!),
                    contentDescription = "Выбранная фотография",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                if (true)
                    Button(onClick = { beginExport(File(selected!!)) }) {
                        Text("Экспорт в галерею")
                    }
            } else {
                Heading("MyGallery")
                Text("${photos.size} фотографий")
                if (photos.isEmpty())
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            "У вас пока нет фото\nСделайте первое фото!",
                            Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center,
                        )
                    }
                else
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(photos, key = { it.name }) { file ->
                            var menu by remember { mutableStateOf(false) }
                            Column {
                                coil.compose.AsyncImage(
                                    file,
                                    contentDescription = file.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier =
                                        Modifier.aspectRatio(1f).clickable {
                                            selected = file.absolutePath
                                        },
                                )
                                if (true) {
                                    TextButton(
                                        onClick = { menu = true },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text("⋮")
                                    }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Экспорт в галерею") },
                                            onClick = {
                                                menu = false
                                                beginExport(file)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}
