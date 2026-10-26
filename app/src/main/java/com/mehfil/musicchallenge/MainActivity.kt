package com.mehfil.musicchallenge

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0C0B0F)
private val Panel = Color(0xFF19171D)
private val Gold = Color(0xFFE6BE72)
private val Cream = Color(0xFFF7F0E5)
private val Muted = Color(0xFFA9A1B0)
private val Raised = Color(0xFF24212A)

data class Track(val uri: Uri, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the status bar so the clock, notifications, signal and battery icons do not overlay the app.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    onPrimary = Ink,
                    background = Ink,
                    surface = Panel,
                    onSurface = Cream,
                    secondary = Gold
                )
            ) {
                MusicGameScreen(
                    getName = { uri -> queryDisplayName(uri) },
                    playerFactory = { ExoPlayer.Builder(this).build() }
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "Unknown song"
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        return name.removeSuffix(".mp3").removeSuffix(".MP3")
    }
}

@Composable
private fun MusicGameScreen(
    getName: (Uri) -> String,
    playerFactory: () -> ExoPlayer
) {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf<Track>() }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var timerLength by remember { mutableIntStateOf(12) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var answerVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Add songs to begin") }
    val player = remember { playerFactory() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                // Persist access so the selected files remain available after the picker closes.
                // Some providers may not offer persistable grants; in that case the current grant is used.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                if (tracks.none { it.uri == uri }) tracks.add(Track(uri, getName(uri)))
            }
        }
        if (tracks.isNotEmpty() && selectedIndex == -1) {
            selectedIndex = 0
            status = "Ready to play"
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    status = "Song finished — make your guess!"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(timerRunning, secondsLeft) {
        if (timerRunning && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        } else if (timerRunning && secondsLeft <= 0) {
            timerRunning = false
            status = "Time's up! Reveal the answer."
        }
    }

    fun stopAudio() {
        player.pause()
        player.seekTo(0)
        isPlaying = false
    }

    fun playClip() {
        val track = tracks.getOrNull(selectedIndex) ?: return
        answerVisible = false
        status = "Listen carefully..."
        timerRunning = true
        secondsLeft = timerLength
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.seekTo(0)
        player.playWhenReady = true
    }

    fun nextTrack() {
        if (tracks.isEmpty()) return
        stopAudio()
        selectedIndex = (selectedIndex + 1) % tracks.size
        answerVisible = false
        timerRunning = false
        secondsLeft = 0
        status = "Ready — two participants, one song"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(44.dp).background(Gold, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Text("M", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("MEHFIL-E-RUBAROO", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Music Challenge", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.background(Raised, RoundedCornerShape(20.dp)).padding(horizontal = 11.dp, vertical = 8.dp)) {
                Text("DEV MODE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("LET THE MUSIC", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
        Text("DO THE TALKING.  ♪", color = Gold, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
        Spacer(Modifier.height(5.dp))
        Text("Two players. One song. Can they name that tune?", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(17.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { picker.launch(arrayOf("audio/mpeg", "audio/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) { Text("＋ Add MP3s", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = {
                    if (tracks.isNotEmpty()) {
                        tracks.shuffle()
                        selectedIndex = 0
                        answerVisible = false
                        status = "Songs shuffled"
                    }
                },
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
            ) { Text("Shuffle") }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(25.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39303B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("CURRENT CHALLENGE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(
                    tracks.getOrNull(selectedIndex)?.name ?: "Choose a song from your library",
                    color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(status, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 12, 15).forEach { value ->
                        FilterChip(
                            selected = timerLength == value,
                            onClick = { timerLength = value },
                            label = { Text("${value}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Gold,
                                selectedLabelColor = Ink,
                                labelColor = Cream
                            )
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (timerRunning) "${secondsLeft}s" else "Timer",
                        color = Gold, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { playClip() },
                        enabled = tracks.getOrNull(selectedIndex) != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("Play Full Song")
                    }
                    OutlinedButton(
                        onClick = { stopAudio(); status = "Audio stopped" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("Stop")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { playClip() },
                        enabled = tracks.getOrNull(selectedIndex) != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
                    ) { Text("Replay") }
                    OutlinedButton(
                        onClick = {
                            timerRunning = false
                            answerVisible = true
                            stopAudio()
                            status = "Answer revealed"
                        },
                        enabled = tracks.getOrNull(selectedIndex) != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)
                    ) { Text("Reveal Answer") }
                    Button(
                        onClick = { nextTrack() },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Text("Next")
                    }
                }
                if (answerVisible && tracks.getOrNull(selectedIndex) != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Gold.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text("CORRECT ANSWER", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(tracks[selectedIndex].name, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your music library", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${tracks.size} songs", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Gold, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Your songs will appear here", color = Cream, fontWeight = FontWeight.SemiBold)
                    Text("Tap “Add MP3s” to choose downloaded audio files.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            stopAudio()
                            selectedIndex = index
                            answerVisible = false
                            timerRunning = false
                            status = "Selected — ready to play"
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == selectedIndex) Color(0xFF3B3020) else Panel
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (index == selectedIndex) Gold else Color(0xFF2A2630))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Gold, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(track.name, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(if (index == selectedIndex) "Selected" else "Tap to select", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        }
    }
}
