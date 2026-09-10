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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

class CounterService : Service() {
    companion object {
        val seconds = MutableStateFlow(0L)
        val running = MutableStateFlow(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification(this, "Прошло ${seconds.value} секунд", true))
        if (job == null) {
            running.value = true
            val start = SystemClock.elapsedRealtime() - seconds.value * 1000
            job =
                scope.launch {
                    while (isActive) {
                        seconds.value = (SystemClock.elapsedRealtime() - start) / 1000
                        getSystemService(NotificationManager::class.java)
                            .notify(
                                1,
                                notification(
                                    this@CounterService,
                                    "Прошло ${seconds.value} секунд",
                                    true,
                                ),
                            )
                        delay(1000)
                    }
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}

@Composable
fun Screen() {
    NotificationPermission()
    val ctx = LocalContext.current
    val n by CounterService.seconds.collectAsStateWithLifecycle()
    val running by CounterService.running.collectAsStateWithLifecycle()
    Center {
        Text(n.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                ContextCompat.startForegroundService(ctx, Intent(ctx, CounterService::class.java))
            },
            enabled = !running,
        ) {
            Text("Старт")
        }
        Button(
            onClick = { ctx.stopService(Intent(ctx, CounterService::class.java)) },
            enabled = running,
        ) {
            Text("Стоп")
        }
    }
}
