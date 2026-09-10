package edu.practice

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.work.*
import java.io.File
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

class PhotoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                val step = inputData.getString("step") ?: return@withContext Result.failure()
                val source = inputData.getString("file") ?: return@withContext Result.failure()
                setProgress(
                    workDataOf(
                        "status" to
                            when (step) {
                                "compress" -> "Сжимаем фото…"
                                "watermark" -> "Добавляем водяной знак…"
                                else -> "Загружаем фото…"
                            }
                    )
                )
                delay(600)
                val result = File(applicationContext.filesDir, "${id}.jpg")
                if (step == "upload") {
                    delay(1400)
                    return@withContext Result.success(
                        workDataOf(
                            "file" to source,
                            "url" to "https://cloud.example.com/uploaded/${File(source).name}",
                        )
                    )
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source, bounds)
                if (bounds.outWidth <= 0)
                    return@withContext Result.failure(
                        workDataOf("error" to "Файл не является изображением")
                    )
                var sample = 1
                while (
                    bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600
                ) sample *= 2
                val bitmap =
                    BitmapFactory.decodeFile(
                        source,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    ) ?: return@withContext Result.failure()
                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                if (step == "watermark") {
                    val paint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.WHITE
                            textSize = (mutable.width / 22f).coerceAtLeast(20f)
                            setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                        }
                    Canvas(mutable).drawText("Учебная фотография", 20f, mutable.height - 30f, paint)
                }
                result.outputStream().use {
                    mutable.compress(
                        Bitmap.CompressFormat.JPEG,
                        if (step == "compress") 75 else 90,
                        it,
                    )
                }
                bitmap.recycle()
                mutable.recycle()
                Result.success(workDataOf("file" to result.absolutePath))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(workDataOf("error" to (e.message ?: "Ошибка обработки")))
            }
        }
}

@Composable
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { WorkManager.getInstance(ctx) }
    var file by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val works by
        manager.getWorkInfosForUniqueWorkFlow("photo").collectAsState(initial = emptyList())
    val busy = works.any { !it.state.isFinished }
    val finished = works.find { it.tags.contains("upload") }
    val status =
        works.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.getString("status")
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        file =
                            withContext(Dispatchers.IO) {
                                val f =
                                    File(ctx.filesDir, "input-${System.currentTimeMillis()}.jpg")
                                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                                    f.outputStream().use { input.copyTo(it) }
                                }
                                f.absolutePath
                            }
                        error = ""
                    } catch (e: Exception) {
                        error = "Не удалось открыть фотографию"
                    }
                }
        }
    Center {
        Heading("Обработка и загрузка фото")
        Button(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Выбрать фото") }
        if (file.isNotEmpty()) Text(File(file).name)
        Notice(
            error.ifEmpty {
                if (finished?.state == WorkInfo.State.SUCCEEDED) "Фото успешно загружено!"
                else if (works.any { it.state == WorkInfo.State.FAILED }) "Ошибка обработки фото"
                else status ?: "Выберите фотографию"
            }
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        finished?.outputData?.getString("url")?.let {
            Text("Имитация загрузки\n$it", fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Button(
            enabled = file.isNotEmpty() && !busy,
            onClick = {
                fun request(step: String) =
                    OneTimeWorkRequestBuilder<PhotoWorker>()
                        .setInputData(workDataOf("step" to step, "file" to file))
                        .addTag(step)
                        .build()
                manager
                    .beginUniqueWork("photo", ExistingWorkPolicy.REPLACE, request("compress"))
                    .then(
                        OneTimeWorkRequestBuilder<PhotoWorker>()
                            .setInputData(workDataOf("step" to "watermark"))
                            .addTag("watermark")
                            .build()
                    )
                    .then(
                        OneTimeWorkRequestBuilder<PhotoWorker>()
                            .setInputData(workDataOf("step" to "upload"))
                            .addTag("upload")
                            .build()
                    )
                    .enqueue()
            },
        ) {
            Text("Начать обработку и загрузку")
        }
        if (busy)
            OutlinedButton(onClick = { manager.cancelUniqueWork("photo") }) { Text("Отменить") }
    }
}
