package com.musicbandhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicbandhub.app.auth.AuthRepository
import com.musicbandhub.app.data.Band
import com.musicbandhub.app.data.BandRepository
import com.musicbandhub.app.data.SessionStore
import com.musicbandhub.app.data.Song
import com.musicbandhub.app.data.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B0D)
private val Accent = Color(0xFFFF3158)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicBandHubApp() }
    }
}

@Composable
fun MusicBandHubApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg)) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val store = remember { SessionStore(context) }
        val token by store.accessToken.collectAsState(initial = null)
        val scope = rememberCoroutineScope()

        if (token.isNullOrBlank()) {
            AuthScreen { receivedToken -> scope.launch { store.saveToken(receivedToken) } }
        } else {
            HomeScreen(token = token!!, store = store)
        }
    }
}

@Composable
private fun AuthScreen(onToken: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "MUSIC BAND HUB",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text("Музыка, песни и репетиции в одном месте", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(14.dp))

        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (register) repo.signUp(email.trim(), password)
                        else repo.signIn(email.trim(), password)
                    }
                    busy = false
                    result.onSuccess(onToken).onFailure { error = it.message }
                }
            }
        ) {
            Text(if (busy) "Загрузка…" else if (register) "Создать аккаунт" else "Войти")
        }

        TextButton(onClick = { register = !register }) {
            Text(if (register) "Уже есть аккаунт" else "Создать аккаунт")
        }
        error?.let { Text(it, color = Color(0xFFFF6B81)) }
    }
}

@Composable
private fun HomeScreen(token: String, store: SessionStore) {
    val scope = rememberCoroutineScope()
    val bandRepo = remember { BandRepository() }
    val songRepo = remember { SongRepository() }

    var bands by remember { mutableStateOf<List<Band>>(emptyList()) }
    var selectedBandId by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCreateBand by remember { mutableStateOf(false) }
    var showJoinBand by remember { mutableStateOf(false) }
    var showCreateSong by remember { mutableStateOf(false) }

    fun reloadBands() {
        scope.launch {
            busy = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) { bandRepo.bands(token) }
            }.onSuccess { loaded ->
                bands = loaded
                if (selectedBandId == null || loaded.none { it.id == selectedBandId }) {
                    selectedBandId = loaded.firstOrNull()?.id
                }
            }.onFailure { error = it.message ?: "Не удалось загрузить группы" }
            busy = false
        }
    }

    fun reloadSongs(bandId: String) {
        scope.launch {
            error = null
            runCatching {
                withContext(Dispatchers.IO) { songRepo.list(token, bandId) }
            }.onSuccess { songs = it }
                .onFailure { error = it.message ?: "Не удалось загрузить песни" }
        }
    }

    LaunchedEffect(Unit) {
        reloadBands()
    }

    LaunchedEffect(selectedBandId) {
        val bandId = selectedBandId
        if (bandId != null) reloadSongs(bandId) else songs = emptyList()
    }

    val selectedBand = bands.firstOrNull { it.id == selectedBandId }

    Column(
        Modifier.fillMaxSize().padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Music Band Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Рабочее пространство группы", color = Color.LightGray)
            }
            TextButton(onClick = { scope.launch { store.clear() } }) {
                Text("Выйти")
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { showCreateBand = true }
            ) { Text("Новая группа") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { showJoinBand = true }
            ) { Text("Войти по коду") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Мои группы", style = MaterialTheme.typography.titleMedium)

        if (bands.isEmpty() && !busy) {
            Spacer(Modifier.height(8.dp))
            Text("Групп пока нет. Создай первую или присоединись по коду.", color = Color.Gray)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(bands, key = { it.id }) { band ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (band.id == selectedBandId)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { selectedBandId = band.id }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(band.name, fontWeight = FontWeight.Bold)
                            if (band.description.isNotBlank()) {
                                Text(band.description, color = Color.Gray)
                            }
                            Text(
                                "Код приглашения: \${band.inviteCode}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))

        if (selectedBand != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        selectedBand.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Песни", color = Color.Gray)
                }
                Button(onClick = { showCreateSong = true }) {
                    Text("+ Песня")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (songs.isEmpty()) {
                Text("Песен пока нет. Добавь первую идею.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(song.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "\${song.status}\${song.bpm?.let { " • $it BPM" } ?: ""}\${song.musicalKey?.let { " • $it" } ?: ""}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (song.notes.isNotBlank()) {
                                    Text(song.notes, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text("Выбери группу, чтобы увидеть песни.", color = Color.Gray)
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF6B81))
        }
    }

    if (showCreateBand) {
        BandDialog(
            title = "Создать группу",
            confirmText = "Создать",
            onDismiss = { showCreateBand = false },
            onConfirm = { name, description ->
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        withContext(Dispatchers.IO) { bandRepo.create(token, name, description) }
                    }.onSuccess {
                        showCreateBand = false
                        reloadBands()
                    }.onFailure { error = it.message ?: "Не удалось создать группу" }
                    busy = false
                }
            },
            busy = busy
        )
    }

    if (showJoinBand) {
        CodeDialog(
            onDismiss = { showJoinBand = false },
            onConfirm = { code ->
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        withContext(Dispatchers.IO) { bandRepo.join(token, code) }
                    }.onSuccess {
                        showJoinBand = false
                        reloadBands()
                    }.onFailure { error = it.message ?: "Не удалось присоединиться" }
                    busy = false
                }
            },
            busy = busy
        )
    }

    if (showCreateSong && selectedBandId != null) {
        SongDialog(
            onDismiss = { showCreateSong = false },
            onConfirm = { title ->
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        withContext(Dispatchers.IO) {
                            songRepo.create(token, selectedBandId!!, title)
                        }
                    }.onSuccess {
                        showCreateSong = false
                        reloadSongs(selectedBandId!!)
                    }.onFailure { error = it.message ?: "Не удалось создать песню" }
                    busy = false
                }
            },
            busy = busy
        )
    }
}

@Composable
private fun BandDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    busy: Boolean
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Название") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Описание") }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.trim().length >= 2,
                onClick = { onConfirm(name.trim(), description.trim()) }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun CodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    busy: Boolean
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Войти в группу") },
        text = {
            OutlinedTextField(
                code,
                { code = it.uppercase() },
                label = { Text("Код приглашения") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = !busy && code.trim().isNotEmpty(),
                onClick = { onConfirm(code.trim()) }
            ) { Text("Войти") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun SongDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    busy: Boolean
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая песня") },
        text = {
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("Название песни") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = !busy && title.trim().isNotEmpty(),
                onClick = { onConfirm(title.trim()) }
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") }
        }
    )
}
