package com.ringan.musik

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private val RinganColors = darkColorScheme(
    primary = Color(0xFF1ED760),
    onPrimary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1C),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB3B3B3)
)

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = RinganColors) { RinganApp() }
        }
    }
}

@Composable
fun RinganApp(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val now by vm.now.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(now.playing) {
        if (now.playing) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.refreshPosition()
                    delay(500)
                }
            }
        }
    }

    val downloadedIds = remember(downloaded) { downloaded.map { it.id }.toSet() }
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                Column {
                    if (now.id != null) {
                        MiniPlayer(now, onOpen = { expanded = true }, onToggle = vm::togglePlay)
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val ind = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        NavigationBarItem(
                            selected = tab == 0, onClick = { tab = 0 }, colors = ind,
                            icon = { Text("♪", fontSize = 20.sp) }, label = { Text("Jelajah") }
                        )
                        NavigationBarItem(
                            selected = tab == 1, onClick = { tab = 1 }, colors = ind,
                            icon = { Text("↓", fontSize = 20.sp) }, label = { Text("Unduhan") }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                if (tab == 0) {
                    BrowseScreen(
                        query = query,
                        onQuery = { query = it },
                        title = if (submitted.isBlank()) "Lagi populer" else "Hasil untuk \"$submitted\"",
                        feed = feed,
                        nowId = now.id,
                        downloadedIds = downloadedIds,
                        progress = progress,
                        onSearch = { submitted = query.trim(); vm.load(query) },
                        onRetry = { vm.load(submitted) },
                        onPlay = { i ->
                            val t = feed.tracks[i]
                            if (now.id == t.id) vm.togglePlay() else vm.play(feed.tracks, i)
                        },
                        onDownload = vm::download
                    )
                } else {
                    DownloadsScreen(
                        list = downloaded,
                        busy = progress.size,
                        nowId = now.id,
                        onPlay = { i ->
                            val t = downloaded[i]
                            if (now.id == t.id) vm.togglePlay() else vm.play(downloaded, i)
                        },
                        onDelete = vm::delete
                    )
                }
            }
        }

        if (expanded && now.id != null) {
            FullPlayer(now, vm, onClose = { expanded = false })
        }
    }
}

@Composable
fun BrowseScreen(
    query: String,
    onQuery: (String) -> Unit,
    title: String,
    feed: Feed,
    nowId: String?,
    downloadedIds: Set<String>,
    progress: Map<String, Int>,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Int) -> Unit,
    onDownload: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Cari lagu atau artis") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        when {
            feed.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            feed.error != null -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(feed.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text("Coba lagi") }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(feed.tracks, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, current = t.id == nowId, onClick = { onPlay(i) }) {
                        val pct = progress[t.id]
                        when {
                            t.id in downloadedIds -> Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                            pct != null -> Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            else -> TextButton(onClick = { onDownload(t) }) {
                                Text("↓", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    list: List<Track>,
    busy: Int,
    nowId: String?,
    onPlay: (Int) -> Unit,
    onDelete: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Unduhan",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
        )
        if (busy > 0) {
            Text(
                "Sedang mengunduh $busy lagu…",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    "Belum ada lagu yang diunduh.\nKetuk ↓ di tab Jelajah untuk menyimpan lagu agar bisa diputar offline.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, current = t.id == nowId, onClick = { onPlay(i) }) {
                        TextButton(onClick = { onDelete(t) }) {
                            Text("Hapus", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(t: Track, current: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(t.artwork, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                t.title,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
            Text(
                t.artist,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(initialValue = ImageCache.get(url), url) {
        if (url != null && value == null) value = ImageCache.load(url)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bmp?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun MiniPlayer(now: Now, onOpen: () -> Unit, onToggle: () -> Unit) {
    val frac = if (now.durMs > 0) (now.posMs.toFloat() / now.durMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(now.artwork, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(now.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    now.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onToggle) {
                Text(
                    if (now.playing) "❚❚" else if (now.buffering) "…" else "▶",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}

@Composable
fun FullPlayer(now: Now, vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = now.durMs.coerceAtLeast(1L)
    val pos = drag ?: (now.posMs.toFloat() / dur).coerceIn(0f, 1f)
    val white = MaterialTheme.colorScheme.onBackground

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose) { Text("▾ Tutup", color = white) }
        }
        Spacer(Modifier.weight(1f))
        Artwork(now.artwork, Modifier.fillMaxWidth(0.8f).aspectRatio(1f))
        Spacer(Modifier.height(24.dp))
        Text(
            now.title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            now.artist,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = pos,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { vm.seekTo((it * dur).toLong()) }
                drag = null
            },
            colors = SliderDefaults.colors(
                thumbColor = white,
                activeTrackColor = white,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(now.posMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(now.durMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = vm::previous) { Text("|◀", fontSize = 24.sp, color = white) }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(white)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (now.playing) "❚❚" else if (now.buffering) "…" else "▶",
                    fontSize = 24.sp,
                    color = Color.Black
                )
            }
            TextButton(onClick = vm::next) { Text("▶|", fontSize = 24.sp, color = white) }
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
