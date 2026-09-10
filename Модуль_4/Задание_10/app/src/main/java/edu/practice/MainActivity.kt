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
import com.google.android.gms.location.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
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
fun Screen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("Нажмите кнопку") }
    var coords by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    @Suppress("MissingPermission")
    fun locate() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(ctx)
                val token = com.google.android.gms.tasks.CancellationTokenSource()
                val loc =
                    withTimeout(20000) {
                        try {
                            client
                                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                                .await()
                        } finally {
                            token.cancel()
                        }
                    } ?: error("Местоположение недоступно. Включите геолокацию")
                coords = "Lat: %.6f\nLng: %.6f".format(Locale.US, loc.latitude, loc.longitude)
                val found =
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        val list =
                            android.location
                                .Geocoder(ctx, Locale.getDefault())
                                .getFromLocation(loc.latitude, loc.longitude, 1)
                        list?.firstOrNull()?.getAddressLine(0)
                    }
                address = found ?: "Адрес не найден. Координаты получены"
            } catch (e: TimeoutCancellationException) {
                address = "Не удалось определить положение за 20 секунд"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                address = e.message ?: "Нет связи с геолокацией"
            } finally {
                busy = false
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            grants ->
            if (grants.values.any { it }) locate() else address = "Доступ к местоположению запрещён"
        }
    Center {
        if (busy) CircularProgressIndicator()
        else Text(address, fontSize = 24.sp, textAlign = TextAlign.Center)
        Text(coords, textAlign = TextAlign.Center)
        Button(
            enabled = !busy,
            onClick = {
                if (
                    ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                )
                    locate()
                else
                    permission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
            },
        ) {
            Text("Получить мой адрес")
        }
    }
}
