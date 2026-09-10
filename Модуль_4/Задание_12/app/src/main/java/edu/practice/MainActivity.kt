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

class FactViewModel : ViewModel() {
    val fact = MutableStateFlow("Нажми кнопку, чтобы узнать факт!")
    val loading = MutableStateFlow(false)
    private val facts =
        listOf(
            "У осьминога три сердца.",
            "Слоны не умеют прыгать.",
            "Синий кит — крупнейшее современное животное.",
            "Пингвины — птицы, которые не летают.",
            "Жирафы имеют семь шейных позвонков.",
            "Кошки используют усы для осязания.",
            "Дельфины дышат атмосферным воздухом.",
            "Бобры строят плотины.",
            "Пчёлы сообщают о пище с помощью танца.",
            "Летучие мыши способны к активному полёту.",
            "Морские коньки относятся к рыбам.",
            "Белые медведи имеют чёрную кожу.",
            "Крокодилы откладывают яйца.",
            "Бабочки проходят стадию куколки.",
            "У пауков восемь ног.",
        )

    fun getRandomFact(): Flow<String> = flow {
        delay(kotlin.random.Random.nextLong(1500, 3001))
        emit(facts.random())
    }

    fun generate() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            try {
                getRandomFact().collect { fact.value = it }
            } finally {
                loading.value = false
            }
        }
    }
}

@Composable
fun Screen(vm: FactViewModel = viewModel()) {
    val fact by vm.fact.collectAsStateWithLifecycle()
    val busy by vm.loading.collectAsStateWithLifecycle()
    Center {
        Heading("Факты о животных")
        if (busy) {
            CircularProgressIndicator()
            Text("Ищем интересный факт…")
        } else {
            AnimatedContent(fact, label = "fact") { value ->
                Card(Modifier.fillMaxWidth()) {
                    Text(value, Modifier.padding(24.dp), textAlign = TextAlign.Center)
                }
            }
        }
        Button(enabled = !busy, onClick = { vm.generate() }) {
            Text(if (busy) "Генерируем…" else "Новый факт!")
        }
    }
}
