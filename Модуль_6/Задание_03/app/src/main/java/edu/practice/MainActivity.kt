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
import edu.practice.presentation.AuthViewModel
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
fun Screen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("emilys") }
    var password by rememberSaveable { mutableStateOf("emilyspass") }
    var selected by remember { mutableStateOf<edu.practice.domain.User?>(null) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.loggedIn) {
            Heading("Вход")
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                username,
                { username = it },
                label = { Text("Имя пользователя") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !state.loading,
                onClick = { vm.login(username, password) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Войти")
            }
            Spacer(Modifier.weight(1f))
        } else if (selected != null) {
            val u = selected!!
            TextButton(onClick = { selected = null }) { Text("← Детали пользователя") }
            coil.compose.AsyncImage(
                u.image,
                contentDescription = "Аватар ${u.firstName}",
                modifier = Modifier.align(Alignment.CenterHorizontally).size(160.dp),
            )
            Heading("${u.firstName} ${u.lastName}")
            Text(u.email)
            Text("@${u.username}")
            Button(
                onClick = {
                    selected = null
                    vm.logout()
                }
            ) {
                Text("Выйти")
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Heading("Пользователи")
                TextButton(onClick = { vm.logout() }) { Text("Выйти") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.users, key = { it.id }) { u ->
                    Card(Modifier.fillMaxWidth().clickable { vm.detail(u.id) { selected = it } }) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            coil.compose.AsyncImage(
                                u.image,
                                contentDescription = u.firstName,
                                modifier = Modifier.size(52.dp),
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("${u.firstName} ${u.lastName}", fontWeight = FontWeight.Bold)
                                Text("@${u.username}", fontSize = 12.sp)
                                Text(u.email, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        if (state.loading) CircularProgressIndicator()
        if (state.error.isNotEmpty()) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            if (state.loggedIn) Button(onClick = { vm.loadUsers() }) { Text("Повторить") }
        }
    }
}
