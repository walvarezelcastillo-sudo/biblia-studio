package com.example.bibliastudio

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore("biblia_prefs")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF2F2F2)) {
                    PlayerScreen()
                }
            }
        }
    }
}

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var currentFile by remember { mutableStateOf<DocumentFile?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    val player = remember { ExoPlayer.Builder(context).build() }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Launcher to pick folder
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Persist permissions
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            treeUri = uri
            // Save treeUri to DataStore
            coroutineScope.launch {
                context.dataStore.edit { prefs ->
                    prefs[preferencesKey<String>("tree_uri")] = uri.toString()
                }
                // load files
                files = loadMp3sFromTree(context, uri)
            }
        }
    }

    // Try to load saved treeUri on start
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val saved = prefs[preferencesKey<String>("tree_uri")]
        if (!saved.isNullOrEmpty()) {
            treeUri = Uri.parse(saved)
            files = withContext(Dispatchers.IO) { loadMp3sFromTree(context, treeUri!!) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Biblia Studio", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { pickFolder.launch(null) }) {
                Text(text = "Seleccionar carpeta")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(files) { file ->
                val name = file.name ?: "Archivo"
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        currentFile = file
                        // load last position
                        coroutineScope.launch {
                            val key = preferencesKey<Long>(file.uri.toString())
                            val prefs = context.dataStore.data.first()
                            val pos = prefs[key] ?: 0L
                            player.stop()
                            player.setMediaItem(MediaItem.fromUri(file.uri))
                            player.prepare()
                            player.seekTo(pos)
                            position = pos
                            isPlaying = true
                            player.play()
                        }
                    }) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = name, fontWeight = FontWeight.Medium)
                    }
                    Text(text = "${formatMs(file.length())}")
                }
                Divider()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player controls
        currentFile?.let { file ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = file.name ?: "-", modifier = Modifier.padding(4.dp))

                // Progress
                LaunchedEffect(player) {
                    while (true) {
                        position = player.currentPosition
                        duration = if (player.duration > 0) player.duration else 0L
                        kotlinx.coroutines.delay(500)
                    }
                }

                Slider(value = if (duration > 0) position / duration.toFloat() else 0f, onValueChange = { frac ->
                    val newPos = (frac * (if (duration>0) duration else 0L)).toLong()
                    player.seekTo(newPos)
                    position = newPos
                })

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        // rewind 10s
                        val newPos = (player.currentPosition - 10_000).coerceAtLeast(0)
                        player.seekTo(newPos)
                    }) {
                        Text(text = "-10s")
                    }

                    Button(onClick = {
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                            // save position
                            coroutineScope.launch {
                                val key = preferencesKey<Long>(file.uri.toString())
                                context.dataStore.edit { prefs ->
                                    prefs[key] = player.currentPosition
                                }
                            }
                        } else {
                            player.play()
                            isPlaying = true
                        }
                    }, modifier = Modifier.size(80.dp)) {
                        Text(text = if (player.isPlaying) "Pause" else "Play")
                    }

                    Button(onClick = {
                        val newPos = (player.currentPosition + 10_000).coerceAtMost(if (player.duration>0) player.duration else Long.MAX_VALUE)
                        player.seekTo(newPos)
                    }) {
                        Text(text = "+10s")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatMs(position))
                    Text(text = if (duration>0) formatMs(duration) else "--:--")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        // open system share for the file
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.type = "audio/*"
                        shareIntent.putExtra(Intent.EXTRA_STREAM, file.uri)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir/Exportar"))
                    }) {
                        Text(text = "Compartir/Exportar")
                    }

                    Button(onClick = {
                        // stop and save position
                        player.pause()
                        coroutineScope.launch {
                            val key = preferencesKey<Long>(file.uri.toString())
                            context.dataStore.edit { prefs ->
                                prefs[key] = player.currentPosition
                            }
                        }
                    }) {
                        Text(text = "Guardar posición")
                    }
                }
            }
        } ?: run {
            Text(text = "No hay archivo seleccionado", modifier = Modifier.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

    }
}

suspend fun loadMp3sFromTree(context: Context, treeUri: Uri): List<DocumentFile> = withContext(Dispatchers.IO) {
    val tree = DocumentFile.fromTreeUri(context, treeUri)
    if (tree == null) return@withContext emptyList<DocumentFile>()
    val children = tree.listFiles().filter { it.isFile && (it.name?.lowercase()?.endsWith(".mp3") ?: false) }
    // sort by name
    return@withContext children.sortedBy { it.name }
}

fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
