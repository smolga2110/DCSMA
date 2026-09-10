package edu.practice

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

class CompassViewModel : ViewModel() {
    val angle = MutableStateFlow(0f)
    private var previous: Float? = null

    fun update(degree: Float) {
        val prev = previous
        val delta = if (prev == null) degree else ((degree - prev + 540) % 360) - 180
        angle.value += delta
        previous = degree
    }
}

@Composable
fun Screen(vm: CompassViewModel = viewModel()) {
    val ctx = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val manager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val angle by vm.angle.collectAsStateWithLifecycle()
    val rotation by animateFloatAsState(-angle, animationSpec = tween(180), label = "compass")
    DisposableEffect(owner, manager) {
        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}

                override fun onSensorChanged(e: SensorEvent) {
                    val matrix = FloatArray(9)
                    val remapped = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(matrix, e.values)
                    @Suppress("DEPRECATION")
                    val display =
                        (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                            .defaultDisplay
                            .rotation
                    val axes =
                        when (display) {
                            android.view.Surface.ROTATION_90 ->
                                SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                            android.view.Surface.ROTATION_180 ->
                                SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                            android.view.Surface.ROTATION_270 ->
                                SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                        }
                    SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
                    val values = FloatArray(3)
                    SensorManager.getOrientation(remapped, values)
                    vm.update(((Math.toDegrees(values[0].toDouble()) + 360) % 360).toFloat())
                }
            }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sensor != null)
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            if (event == Lifecycle.Event.ON_PAUSE) manager.unregisterListener(listener)
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && sensor != null)
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF111111)).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Text("Компас", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (sensor == null)
            Text(
                "Устройство не поддерживает датчик ориентации",
                color = Color.Red,
                textAlign = TextAlign.Center,
            )
        else {
            Box(Modifier.fillMaxWidth(.85f).aspectRatio(1f)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFF303030), style = Stroke(5.dp.toPx()))
                    rotate(rotation) {
                        drawLine(
                            Color(0xFFE84D4D),
                            center,
                            Offset(center.x, center.y - size.minDimension * .39f),
                            8.dp.toPx(),
                            StrokeCap.Round,
                        )
                        drawLine(
                            Color(0xFF555555),
                            center,
                            Offset(center.x, center.y + size.minDimension * .39f),
                            8.dp.toPx(),
                            StrokeCap.Round,
                        )
                    }
                }
                Text(
                    "N",
                    Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                    color = Color(0xFFE84D4D),
                    fontSize = 26.sp,
                )
            }
            Text("Азимут: ${((angle%360+360)%360).toInt()}°", color = Color.White, fontSize = 26.sp)
        }
    }
}
