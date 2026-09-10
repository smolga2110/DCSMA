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

object Reminder {
    fun prefs(c: Context) = c.getSharedPreferences("reminder", Context.MODE_PRIVATE)

    fun next(): Long {
        val now = java.time.ZonedDateTime.now()
        var next = now.withHour(20).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    fun intent(c: Context) =
        PendingIntent.getBroadcast(
            c,
            10,
            Intent(c, ReminderReceiver::class.java).setAction("REMIND"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(c: Context): Boolean {
        val manager = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return false
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(), intent(c))
        return true
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java).cancel(intent(c))
        prefs(c).edit().putBoolean("enabled", false).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Reminder.prefs(c).getBoolean("enabled", false)) return
        if (i.action == "REMIND") notify(c, 10, "Время принять таблетку!")
        Reminder.schedule(c)
    }
}

@Composable
fun Screen() {
    NotificationPermission()
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(Reminder.prefs(ctx).getBoolean("enabled", false)) }
    var message by remember { mutableStateOf("") }
    Center {
        Text("●", fontSize = 80.sp, color = if (enabled) Color(0xFF00896D) else Color.Gray)
        Heading("Напоминание о таблетке")
        Text(if (enabled) "Включено" else "Выключено")
        if (enabled) {
            val date =
                java.time.Instant.ofEpochMilli(Reminder.next())
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            Text(
                "Следующее напоминание: ${if(date==java.time.LocalDate.now())"сегодня" else "завтра"} в 20:00"
            )
        }
        Button(
            onClick = {
                if (enabled) {
                    Reminder.cancel(ctx)
                    enabled = false
                } else if (Reminder.schedule(ctx)) {
                    Reminder.prefs(ctx).edit().putBoolean("enabled", true).apply()
                    enabled = true
                    message = ""
                } else {
                    message = "Разрешите точные будильники, затем нажмите кнопку снова"
                    ctx.startActivity(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:${ctx.packageName}"),
                        )
                    )
                }
            }
        ) {
            Text(if (enabled) "Выключить напоминание" else "Включить напоминание")
        }
        if (message.isNotEmpty()) Notice(message)
    }
}
