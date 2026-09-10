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

class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val city = inputData.getString("city") ?: ""
        val stage =
            inputData.getStringArray("stage")?.firstOrNull()
                ?: inputData.getString("stage")
                ?: "download"
        fun fg(text: String): ForegroundInfo {
            val n = notification(applicationContext, text, true)
            return if (Build.VERSION.SDK_INT >= 29)
                ForegroundInfo(
                    20,
                    n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            else ForegroundInfo(20, n)
        }
        setForeground(
            fg(
                if (stage == "report") "Все данные получены, формируем отчёт…"
                else "Загружаем погоду для 3 городов…"
            )
        )
        if (stage == "report") {
            val cities = inputData.getStringArray("city") ?: return Result.failure()
            val temps = inputData.getIntArray("temp") ?: return Result.failure()
            if (cities.size != temps.size || temps.isEmpty()) return Result.failure()
            val report =
                cities.indices.joinToString("\n") { "${cities[it]}: ${temps[it]}°C" } +
                    "\nСредняя температура: %.1f°C".format(temps.average())
            delay(600)
            withContext(Dispatchers.IO) {
                File(applicationContext.filesDir, "weather-report.txt").writeText(report)
            }
            notify(
                applicationContext,
                21,
                "Отчёт готов! Средняя температура %.1f°C".format(temps.average()),
            )
            return Result.success(workDataOf("report" to report))
        }
        setProgress(workDataOf("status" to "Загрузка…"))
        delay(
            when (city) {
                "Москва" -> 1400L
                "Лондон" -> 2300L
                else -> 3200L
            }
        )
        val temp =
            when (city) {
                "Москва" -> 21
                "Лондон" -> -2
                else -> -7
            }
        setProgress(workDataOf("status" to "Готово"))
        setForeground(fg("Готово: $city. Остальные города — в процессе…"))
        return Result.success(workDataOf("city" to city, "temp" to temp))
    }
}

@Composable
fun Screen() {
    NotificationPermission()
    val ctx = LocalContext.current
    val manager = remember { WorkManager.getInstance(ctx) }
    val works by
        manager.getWorkInfosForUniqueWorkFlow("weather").collectAsState(initial = emptyList())
    val busy = works.any { !it.state.isFinished }
    val cities = listOf("Москва", "Лондон", "Нью-Йорк")
    val report = works.firstOrNull { it.tags.contains("report") }?.outputData?.getString("report")
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading("Прогноз погоды")
        Notice(
            if (busy) "Загрузка…" else if (report != null) "Все данные получены" else "Готов начать"
        )
        cities.forEach { city ->
            val w = works.find { it.tags.contains(city) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(city)
                    Text(
                        when (w?.state) {
                            WorkInfo.State.SUCCEEDED -> "Готово: ${w.outputData.getInt("temp",0)}°C"
                            WorkInfo.State.RUNNING -> "Загрузка…"
                            WorkInfo.State.FAILED -> "Ошибка"
                            WorkInfo.State.CANCELLED -> "Отменено"
                            else -> "Ожидание"
                        }
                    )
                }
            }
        }
        if (report != null)
            Card(Modifier.fillMaxWidth()) {
                Text("Итоговый прогноз\n$report", Modifier.padding(16.dp))
            }
        Spacer(Modifier.weight(1f))
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val requests =
                    cities.map {
                        OneTimeWorkRequestBuilder<WeatherWorker>()
                            .setInputData(workDataOf("city" to it))
                            .addTag(it)
                            .build()
                    }
                val end =
                    OneTimeWorkRequestBuilder<WeatherWorker>()
                        .setInputData(workDataOf("stage" to "report"))
                        .setInputMerger(ArrayCreatingInputMerger::class.java)
                        .addTag("report")
                        .build()
                manager
                    .beginUniqueWork("weather", ExistingWorkPolicy.REPLACE, requests)
                    .then(end)
                    .enqueue()
            },
        ) {
            Text("Собрать прогноз")
        }
        if (busy)
            OutlinedButton(onClick = { manager.cancelUniqueWork("weather") }) { Text("Отменить") }
    }
}
