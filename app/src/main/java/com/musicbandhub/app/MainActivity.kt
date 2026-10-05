package com.musicbandhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicbandhub.app.auth.AuthRepository
import com.musicbandhub.app.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B0D)
private val Accent = Color(0xFFFF3158)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MusicBandHubApp() } }
}

@Composable fun MusicBandHubApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg)) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val store = remember { SessionStore(context) }
        val token by store.accessToken.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        if (token.isNullOrBlank()) AuthScreen { t -> scope.launch { store.saveToken(t) } }
        else HomeScreen(store)
    }
}

@Composable private fun AuthScreen(onToken: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val repo = remember { AuthRepository() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("MUSIC BAND HUB", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Музыка, песни и репетиции в одном месте", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; error = null
            scope.launch { val result = withContext(Dispatchers.IO) { if (register) repo.signUp(email.trim(), password) else repo.signIn(email.trim(), password) }; busy = false; result.onSuccess(onToken).onFailure { error = it.message } }
        }) { Text(if (busy) "Загрузка…" else if (register) "Создать аккаунт" else "Войти") }
        TextButton(onClick = { register = !register }) { Text(if (register) "Уже есть аккаунт" else "Создать аккаунт") }
        error?.let { Text(it, color = Color(0xFFFF6B81)) }
    }
}

@Composable private fun HomeScreen(store: SessionStore) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Music Band Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Группы • Песни • Репетиции • Участники", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("Stage 2", style = MaterialTheme.typography.titleLarge); Text("Базовое Android-приложение готово к подключению Supabase.", color = Color.Gray) } }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { scope.launch { store.clear() } }, modifier = Modifier.fillMaxWidth()) { Text("Выйти") }
    }
}