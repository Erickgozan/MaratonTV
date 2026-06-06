package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.TvViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.BloodRed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.TextLight

@Composable
fun FullscreenPlayer(
    viewModel: TvViewModel,
    isMaximized: Boolean = true,
    modifier: Modifier = Modifier
) {
    val channel by viewModel.playingChannel.collectAsState()
    val activeStreamUrl by viewModel.activeStreamUrl.collectAsState()

    val videoFormat by viewModel.videoFormat.collectAsState()
    val videoLanguage by viewModel.videoLanguage.collectAsState()
    val videoSubtitles by viewModel.videoSubtitles.collectAsState()
    val videoPlayerEngine by viewModel.videoPlayerEngine.collectAsState()

    val libVlcInstalled by viewModel.libVlcUtilityInstalled.collectAsState()
    val codecsPackInstalled by viewModel.codecsPackInstalled.collectAsState()
    val audioFixIgnoreErrors by viewModel.audioFixIgnoreErrors.collectAsState()
    val audioFixChannels by viewModel.audioFixChannels.collectAsState()

    var showSourceDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf("Autómatico (4K Max)") }
    var selectedAudio by remember { mutableStateOf("Español Latino (Estéreo AC3)") }

    val currentChannel = channel ?: return

    val isTvCategory = currentChannel.groupTitle == "TV"
    var showIptvOverlay by remember { mutableStateOf(false) }
    var showHudControls by remember { mutableStateOf(false) }
    var hudInteractionTrigger by remember { mutableStateOf(0) }

    var showConfigOverlay by remember { mutableStateOf(false) }
    var showAudioFixesOverlay by remember { mutableStateOf(false) }
    var showEpisodesOverlay by remember { mutableStateOf(false) }

    val seriesEpisodes by viewModel.seriesEpisodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val selectedEpisodeNum by viewModel.selectedEpisodeNum.collectAsState()
    val isSeries = seriesEpisodes.isNotEmpty()

    val uniqueSeasons = remember(seriesEpisodes) {
        seriesEpisodes.map { 
            val info = viewModel.parseSeriesEpisode(it)
            info?.season ?: 1
        }.distinct().sorted()
    }

    val episodesInSelectedSeason = remember(seriesEpisodes, selectedSeason) {
        seriesEpisodes.filter { ch ->
            val info = viewModel.parseSeriesEpisode(ch)
            info != null && info.season == selectedSeason
        }.sortedBy { ch ->
            val info = viewModel.parseSeriesEpisode(ch)
            info?.episode ?: 1
        }
    }

    val firstEpisodeFocusRequester = remember { FocusRequester() }

    // Video Player Instance & Progression Persistent State Flow
    var playerInstance by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var webViewInstance by remember(activeStreamUrl) { mutableStateOf<android.webkit.WebView?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var isVideoPlaying by remember { mutableStateOf(false) }

    var savedProgressState by remember(activeStreamUrl) { mutableStateOf<com.example.data.PlaybackProgress?>(null) }
    var hasRestoredProgress by remember(activeStreamUrl) { mutableStateOf(false) }
    var showRestoredToast by remember(activeStreamUrl) { mutableStateOf(false) }

    val isEmbed = remember(activeStreamUrl, currentChannel.isEmbedText) {
        currentChannel.isEmbedText || com.example.ui.components.isEmbedUrl(activeStreamUrl)
    }

    // Fetch progress and auto-resume
    LaunchedEffect(playerInstance, activeStreamUrl) {
        val player = playerInstance
        if (player != null) {
            val inMemoryPos = viewModel.getLivePlaybackPosition(activeStreamUrl)
            if (inMemoryPos > 0L && !hasRestoredProgress) {
                if (!isTvCategory) {
                    player.seekTo(inMemoryPos)
                    hasRestoredProgress = true
                }
            } else {
                val progress = viewModel.getPlaybackProgress(activeStreamUrl)
                savedProgressState = progress
                
                if (progress != null && progress.position > 5000 && !hasRestoredProgress) {
                    if (!isTvCategory) {
                        player.seekTo(progress.position)
                        hasRestoredProgress = true
                        showRestoredToast = true
                        // dismiss automatically after 8 seconds
                        delay(8000)
                        showRestoredToast = false
                    }
                }
            }
        }
    }

    // Periodical progress tracking loop
    LaunchedEffect(playerInstance, isVideoPlaying) {
        val player = playerInstance
        if (player != null) {
            while (true) {
                currentPosition = player.currentPosition
                totalDuration = player.duration.coerceAtLeast(0L)
                isVideoPlaying = player.isPlaying
                
                // Periodically persist position in DB and in-memory cache
                if (!isTvCategory && currentPosition > 1000) {
                    viewModel.setLivePlaybackPosition(activeStreamUrl, currentPosition)
                    if (isVideoPlaying && totalDuration > 10000) {
                        if (currentPosition > totalDuration * 0.95f) {
                            viewModel.deletePlaybackProgress(activeStreamUrl)
                        } else {
                            viewModel.savePlaybackProgress(activeStreamUrl, currentPosition, totalDuration)
                        }
                    }
                }
                delay(1000) // check every second for maximum synchronization tightness
            }
        }
    }

    var isConfigScrimClickable by remember(showConfigOverlay) { mutableStateOf(false) }
    LaunchedEffect(showConfigOverlay) {
        if (showConfigOverlay) {
            delay(150)
            isConfigScrimClickable = true
        } else {
            isConfigScrimClickable = false
        }
    }

    var isAudioFixScrimClickable by remember(showAudioFixesOverlay) { mutableStateOf(false) }
    LaunchedEffect(showAudioFixesOverlay) {
        if (showAudioFixesOverlay) {
            delay(150)
            isAudioFixScrimClickable = true
        } else {
            isAudioFixScrimClickable = false
        }
    }

    val mainFocusRequester = remember { FocusRequester() }
    val firstConfigFocusRequester = remember { FocusRequester() }
    val firstAudioFixFocusRequester = remember { FocusRequester() }
    val firstIptvCategoryFocusRequester = remember { FocusRequester() }
    val firstHudBtnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showIptvOverlay) {
        if (showIptvOverlay) {
            delay(250) // wait for transitions
            try {
                firstIptvCategoryFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(showHudControls) {
        if (showHudControls) {
            delay(250) // wait for transitions
            try {
                firstHudBtnFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(showConfigOverlay) {
        if (showConfigOverlay) {
            delay(250) // wait for panel/scrim transitions
            try {
                firstConfigFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures safely
            }
        }
    }

    LaunchedEffect(showAudioFixesOverlay) {
        if (showAudioFixesOverlay) {
            delay(250) // wait for panel/scrim transitions
            try {
                firstAudioFixFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures safely
            }
        }
    }

    LaunchedEffect(showEpisodesOverlay) {
        if (showEpisodesOverlay) {
            delay(250) // wait for panel/scrim transitions
            try {
                firstEpisodeFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore safely
            }
        }
    }

    LaunchedEffect(showConfigOverlay, showAudioFixesOverlay, showIptvOverlay, showEpisodesOverlay) {
        if (!showConfigOverlay && !showAudioFixesOverlay && !showIptvOverlay && !showEpisodesOverlay) {
            delay(200)
            try {
                mainFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore safely
            }
        }
    }

    val allChannels by viewModel.allChannels.collectAsState()
    
    LaunchedEffect(showHudControls, hudInteractionTrigger) {
        if (showHudControls) {
            delay(5000)
            showHudControls = false
        }
    }

    var selectedOverlayCategory by remember { mutableStateOf("CANALES") }
    var localSearchQuery by remember { mutableStateOf("") }
    var parentPINUnlocked by remember { mutableStateOf(false) }
    var inputPINValue by remember { mutableStateOf("") }

    LaunchedEffect(isMaximized) {
        if (isMaximized) {
            try {
                mainFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val dynamicCategoriesList = remember(allChannels) {
        val coreList = listOf(
            Triple("BUSCA", "Busca", Icons.Default.Search),
            Triple("SUSCRIPCIONES", "Lista de suscripciones...", Icons.Default.Refresh),
            Triple("FAVORITOS", "Favoritos", Icons.Default.Star),
            Triple("CANALES", "Lista de canales", Icons.Default.List)
        )
        val tvGroups = allChannels
            .filter { it.groupTitle == "TV" && it.originalGroup.isNotBlank() && it.originalGroup != "TV" }
            .map { it.originalGroup }
            .distinct()
            .sorted()
            .map { groupName ->
                val icon = if (groupName.contains("Deporte", ignoreCase = true) || groupName.contains("Sport", ignoreCase = true)) {
                    Icons.Default.PlayArrow
                } else if (groupName.contains("18+") || groupName.contains("Adult", ignoreCase = true) || groupName.contains("XXX", ignoreCase = true)) {
                    Icons.Default.Lock
                } else if (groupName.contains("Noticia", ignoreCase = true) || groupName.contains("News", ignoreCase = true)) {
                    Icons.Default.PlayArrow
                } else {
                    Icons.Default.PlayArrow
                }
                Triple(groupName, groupName, icon)
            }
        coreList + tvGroups
    }

    val qualityOptions = listOf(
        "Autómatico (4K Max)",
        "Ultra HD 4K (2160p)",
        "Full HD (1080p)",
        "High Definition (720p)",
        "Ahorro de datos (480p)"
    )

    val audioOptions = listOf(
        "Español Latino (Estéreo AC3)",
        "Castellano (5.1 Surround)",
        "Inglés Original (Dolby Atmos)",
        "Portugués"
    )

    val exoplayerResizeMode = when (videoFormat) {
        "Normal" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "Pantalla completa" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        "16:9", "forzar 16:9" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    val customAspectRatio: Float? = when (videoFormat) {
        "16:9", "forzar 16:9" -> 16f / 9f
        "4:3" -> 4f / 3f
        "Normal" -> null
        "Pantalla completa" -> null
        else -> 16f / 9f
    }

    val rootModifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .focusRequester(mainFocusRequester)
        .focusable(isMaximized && !showIptvOverlay && !showConfigOverlay && !showAudioFixesOverlay && !showHudControls && !showEpisodesOverlay)
        .pointerInput(Unit) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    if (dragAmount.y < -15f || dragAmount.y > 15f) {
                        if (!showConfigOverlay && !showAudioFixesOverlay && !showEpisodesOverlay && !showIptvOverlay) {
                            showHudControls = true
                            hudInteractionTrigger++
                            change.consume()
                        }
                    }
                }
            )
        }
        .onKeyEvent { keyEvent ->
            if (isMaximized) {
                hudInteractionTrigger++
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!isTvCategory && !showIptvOverlay && !showConfigOverlay && !showAudioFixesOverlay && !showHudControls && !showEpisodesOverlay) {
                                playerInstance?.let { p ->
                                    val newPos = (p.currentPosition - 10000).coerceAtLeast(0L)
                                    p.seekTo(newPos)
                                    currentPosition = newPos
                                }
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!isTvCategory && !showIptvOverlay && !showConfigOverlay && !showAudioFixesOverlay && !showHudControls && !showEpisodesOverlay) {
                                playerInstance?.let { p ->
                                    val newPos = (p.currentPosition + 10000).coerceAtMost(p.duration)
                                    p.seekTo(newPos)
                                    currentPosition = newPos
                                }
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Spacebar, Key.Enter -> {
                            if (!isTvCategory && !showIptvOverlay && !showConfigOverlay && !showAudioFixesOverlay && !showHudControls && !showEpisodesOverlay) {
                                playerInstance?.let { p ->
                                    p.playWhenReady = !p.playWhenReady
                                    isVideoPlaying = p.playWhenReady
                                }
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (!showConfigOverlay && !showAudioFixesOverlay && !showEpisodesOverlay && !showIptvOverlay) {
                                showHudControls = true
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            false
                        }
                        Key.Back -> {
                            if (showEpisodesOverlay) {
                                showEpisodesOverlay = false
                                true
                            } else if (showAudioFixesOverlay) {
                                showAudioFixesOverlay = false
                                true
                            } else if (showIptvOverlay) {
                                showIptvOverlay = false
                                true
                            } else if (showConfigOverlay) {
                                showConfigOverlay = false
                                true
                            } else if (showHudControls) {
                                showHudControls = false
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            } else {
                false
            }
        }
        .then(
            if (!isMaximized) {
                Modifier.clickable {
                    viewModel.setPlayerMaximized(true)
                }
            } else {
                Modifier
            }
        )

    Box(
        modifier = rootModifier,
        contentAlignment = Alignment.Center
    ) {
        // The Video Player core with reactive live stream audio fixes
        VideoPlayer(
            streamUrl = activeStreamUrl,
            modifier = Modifier.fillMaxSize(),
            showController = false,
            resizeMode = exoplayerResizeMode,
            aspectRatio = customAspectRatio,
            libVlcInstalled = libVlcInstalled,
            codecsPackInstalled = codecsPackInstalled,
            videoLanguage = videoLanguage,
            videoSubtitles = videoSubtitles,
            audioFixIgnoreErrors = audioFixIgnoreErrors,
            audioFixChannels = audioFixChannels,
            onPlayerCreated = { playerInstance = it },
            isEmbedText = currentChannel.isEmbedText,
            adBlockerEnabled = currentChannel.adBlockerEnabled,
            onWebViewCreated = { webViewInstance = it },
            onProgressUpdated = { current, total, playing ->
                if (isEmbed) {
                    currentPosition = current
                    totalDuration = total
                    isVideoPlaying = playing
                }
            }
        )

        if (!isMaximized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable {
                        viewModel.setPlayerMaximized(true)
                    }
            )
        }

        if (isMaximized) {
            if (!isEmbed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable {
                            if (isTvCategory) {
                                showIptvOverlay = true
                            } else {
                                showHudControls = true
                                hudInteractionTrigger++
                            }
                        }
                )
            } else {
                // For embedded video players (WebView, e.g., fastream, streamwish, delta), we do NOT place 
                // a full-window click interceptor so that the user can fully touch/click and interact 
                // directly with the web player.
                // However, we provide an elegant, small floating exit button at the top-left of the screen 
                // to allow them to easily close the player and return to the home screen.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    IconButton(
                        onClick = {
                            if (!viewModel.goBack()) {
                                viewModel.stopPlayback()
                                viewModel.setScreen("MAIN")
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Salir del reproductor",
                            tint = Color.White
                        )
                    }
                }
            }

        if (showConfigOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = isConfigScrimClickable) { showConfigOverlay = false }
            )
        }

        if (showAudioFixesOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = isAudioFixScrimClickable) { showAudioFixesOverlay = false }
            )
        }

        // Player Controls HUD Overlay (Simple top-header / bottom-footer style)
        AnimatedVisibility(
            visible = showHudControls && !showIptvOverlay && !showConfigOverlay,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showHudControls = false
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header: Screen Back & Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (!viewModel.goBack()) {
                                    viewModel.stopPlayback()
                                    viewModel.setScreen("MAIN")
                                }
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Cerrar reproductor",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentChannel.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            if (isTvCategory) {
                                currentChannel.currentProgram?.let {
                                    Text(
                                        text = "En Vivo: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BloodRed
                                    )
                                }
                            }
                        }
                    }

                    // Top Right Pill - Sync and active stream state
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Green)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buffer Estable 100%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Bottom controls overlay (playback controls eliminated, keeping only specific remote pills)
                val activeIsSeries = isSeries || currentChannel.groupTitle == "SERIES" || viewModel.parseSeriesEpisode(currentChannel) != null
                if (activeIsSeries) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BloodRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable(
                                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Serie",
                                        tint = BloodRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CONTROLES RÁPIDOS DE SERIE",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White
                                    )
                                }
                                
                                val currentSeasonText = if (selectedSeason > 0) "T$selectedSeason " else ""
                                val currentEpText = if (selectedEpisodeNum > 0) "E$selectedEpisodeNum" else ""
                                Text(
                                    text = "Viendo actualmente: $currentSeasonText$currentEpText",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextLight
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerPillBtn(
                                    text = "Elegir Capítulo",
                                    icon = Icons.Default.List,
                                    onClick = {
                                        showEpisodesOverlay = true
                                        showHudControls = false
                                    }
                                )
                                
                                PlayerPillBtn(
                                    text = "Cambiar Fuente / Servidor",
                                    icon = Icons.Default.Menu,
                                    onClick = {
                                        showSourceDialog = true
                                    }
                                )
                            }

                            if (episodesInSelectedSeason.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cambio Rápido de Capítulo (Temporada $selectedSeason):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = BloodRed
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(episodesInSelectedSeason) { epChannel ->
                                        val info = viewModel.parseSeriesEpisode(epChannel)
                                        val epNum = info?.episode ?: 1
                                        val isCurrentEp = epNum == selectedEpisodeNum
                                        
                                        var isEpFocused by remember { mutableStateOf(false) }
                                        
                                        val epBgColor = if (isEpFocused) {
                                            BloodRed
                                        } else if (isCurrentEp) {
                                            Color.White.copy(alpha = 0.25f)
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(epBgColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isEpFocused) Color.White else if (isCurrentEp) BloodRed else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .onFocusChanged { isEpFocused = it.isFocused }
                                                .clickable {
                                                    viewModel.selectEpisode(epNum)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Cap. $epNum",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom controls overlay (playback controls eliminated, keeping only specific remote pills)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bottom info and dynamic config anchors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Info Section
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Live",
                                tint = BloodRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reproduciendo de ${if (currentChannel.sources.size > 1) "${currentChannel.sources.size} Listas" else "Lista Única"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextLight.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = selectedQuality,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "Audio: $selectedAudio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight
                                )
                            }
                        }

                        // Quick Navigation Action buttons (TV Remote accessible)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 1. Switch Stream Source option
                            if (currentChannel.sources.size > 1) {
                                PlayerPillBtn(
                                    text = "Cambiar Fuente",
                                    icon = Icons.Default.Menu,
                                    onClick = { showSourceDialog = true },
                                    modifier = Modifier.focusRequester(firstHudBtnFocusRequester)
                                )
                            }

                            // 2. Calidad
                            PlayerPillBtn(
                                text = "Calidad",
                                icon = Icons.Default.List,
                                onClick = { showQualityDialog = true },
                                modifier = if (currentChannel.sources.size <= 1) Modifier.focusRequester(firstHudBtnFocusRequester) else Modifier
                            )

                            // 3. Switch Audio Language
                            PlayerPillBtn(
                                text = "Idioma & Audio",
                                icon = Icons.Default.PlayArrow,
                                onClick = { showAudioDialog = true }
                            )

                            // 4. Adjust Screen
                            PlayerPillBtn(
                                text = "Ajustar Pantalla 📺",
                                icon = Icons.Default.Settings,
                                onClick = {
                                    showConfigOverlay = true
                                    showHudControls = false
                                }
                            )

                            // 5. Audio Patches & Fixes Quick Button
                            PlayerPillBtn(
                                text = "Solucionar Sonido 🛠️",
                                icon = Icons.Default.Build,
                                onClick = {
                                    showAudioFixesOverlay = true
                                    showHudControls = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- NEW PREMIUM IPTV CATEGORY CHANNELS SIDEBAR OVERLAY (Image 1 Layout) ---
        if (showIptvOverlay) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                // --- CATEGORIES SIDEBAR (LEFT) ---
                Column(
                    modifier = Modifier
                        .width(230.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF0C0C0B).copy(alpha = 0.88f))
                        .padding(vertical = 12.dp, horizontal = 10.dp)
                ) {
                    // Back To Dashboard navigation top icon button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (!viewModel.goBack()) {
                                    viewModel.stopPlayback()
                                    viewModel.setScreen("MAIN")
                                }
                            },
                            modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row {
                            Text(
                                text = "BLOODERS ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Text(
                                text = "TV",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
                                color = BloodRed
                            )
                        }
                    }

                    // Categories List matching Image 1 exactly! (Scope promoted to outer function block)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(dynamicCategoriesList) { (key, label, icon) ->
                            val isSelected = selectedOverlayCategory == key
                            var isFocused by remember { mutableStateOf(false) }
                            
                            val bgSelectedColor = if (isSelected) Color(0xFF2E63E9) else if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bgSelectedColor)
                                    .then(
                                        if (isSelected) Modifier.focusRequester(firstIptvCategoryFocusRequester) else Modifier
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        selectedOverlayCategory = key
                                        if (key != "BUSCA") {
                                            localSearchQuery = "" // Reset search if choosing another category
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (key == "18+" || key.contains("18+") || key.contains("XXX", ignoreCase = true) || key.contains("Adult", ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Pin lock",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom item 1: Reparar Audio
                    var isAudioFixFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAudioFixFocused) BloodRed.copy(alpha = 0.15f) else Color.Transparent)
                            .onFocusChanged { isAudioFixFocused = it.isFocused }
                            .focusable()
                            .clickable {
                                showIptvOverlay = false
                                showAudioFixesOverlay = true
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Reparar Audio",
                            tint = if (isAudioFixFocused) BloodRed else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Reparar Audio 🛠️",
                            color = if (isAudioFixFocused) BloodRed else Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom item 2: Adjustments to open Bottom Settings config (Image 2)
                    var isSettingsFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSettingsFocused) BloodRed.copy(alpha = 0.15f) else Color.Transparent)
                            .onFocusChanged { isSettingsFocused = it.isFocused }
                            .focusable()
                            .clickable {
                                showIptvOverlay = false
                                showConfigOverlay = true
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes del Reproductor",
                            tint = if (isSettingsFocused) BloodRed else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ajustes de Video",
                            color = if (isSettingsFocused) BloodRed else Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // --- CHANNELS LIST (CENTER) ---
                Column(
                    modifier = Modifier
                        .width(330.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF131312).copy(alpha = 0.94f))
                        .padding(vertical = 12.dp, horizontal = 12.dp)
                ) {
                    val capCategoryTitle = dynamicCategoriesList.find { it.first == selectedOverlayCategory }?.second ?: "Canales"
                    Text(
                        text = capCategoryTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp, start = 6.dp)
                    )

                    if (selectedOverlayCategory == "BUSCA") {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Escribe para buscar...", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF2E63E9),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }

                    val isAdultCategoryChecked = selectedOverlayCategory == "18+" || 
                        selectedOverlayCategory.contains("18+") || 
                        selectedOverlayCategory.contains("XXX", ignoreCase = true) || 
                        selectedOverlayCategory.contains("Adult", ignoreCase = true)

                    if (isAdultCategoryChecked && !parentPINUnlocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, start = 8.dp, end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock PIN",
                                tint = BloodRed,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "CONTROL PARENTAL",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Introduce el PIN de cuatro dígitos por defecto: '0000'",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            OutlinedTextField(
                                value = inputPINValue,
                                onValueChange = {
                                    if (it.length <= 4) {
                                        inputPINValue = it
                                        if (it == "0000") {
                                            parentPINUnlocked = true
                                        }
                                    }
                                },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                ),
                                modifier = Modifier.width(120.dp)
                            )

                            Button(
                                onClick = {
                                    if (inputPINValue == "0000") {
                                        parentPINUnlocked = true
                                    } else {
                                        inputPINValue = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed)
                            ) {
                                Text("DESBLOQUEAR", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else if (selectedOverlayCategory == "SUSCRIPCIONES") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Servicio Activo • Premium",
                                color = Color.Green,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Listas cargadas: 1\nSincronización en la nube: Activa\nÚltimo escaneo: Hace pocos instantes.",
                                color = TextLight,
                                style = MaterialTheme.typography.bodySmall
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            Text(
                                text = "Sintonizando de forma integrada y sin interrupciones todos tus perfiles de reproducción.",
                                color = TextLight.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        // Channel listing with reactive filters
                        val filteredOverlayChannels = remember(allChannels, selectedOverlayCategory, localSearchQuery) {
                            allChannels.filter { ch ->
                                val matchSearch = if (localSearchQuery.isNotEmpty()) {
                                    ch.name.contains(localSearchQuery, ignoreCase = true)
                                } else true

                                val matchCategory = when (selectedOverlayCategory) {
                                    "BUSCA" -> true
                                    "FAVORITOS" -> ch.isFavorite
                                    "CANALES" -> ch.groupTitle == "TV"
                                    "DEPORTES" -> ch.groupTitle == "Deportes" || ch.name.contains("deportes", ignoreCase = true) || ch.name.contains("sports", ignoreCase = true)
                                    "18+" -> ch.groupTitle == "18+" || ch.groupTitle == "Adultos" || ch.groupTitle == "Adults" || ch.name.contains("XXX", ignoreCase = true) || ch.name.contains("18+", ignoreCase = true)
                                    "VIVO_GRATIS" -> ch.groupTitle == "TV"
                                    "CINE_SERIES" -> ch.groupTitle == "PELICULA" || ch.groupTitle == "SERIES"
                                    "POPULAR" -> (ch.rating.toDoubleOrNull() ?: 0.0) >= 8.0
                                    "VENEZUELA" -> ch.groupTitle == "Venezuela" || ch.name.contains("Venezuela", ignoreCase = true) || ch.name.contains("VTV", ignoreCase = true) || ch.name.contains("Televen", ignoreCase = true) || ch.name.contains("Venevisión", ignoreCase = true)
                                    else -> ch.originalGroup == selectedOverlayCategory && ch.groupTitle == "TV"
                                }

                                matchSearch && matchCategory
                            }
                        }

                        if (filteredOverlayChannels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron canales en esta sección.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredOverlayChannels) { index, ch ->
                                    val isPlaying = ch.name == currentChannel.name
                                    var isFocused by remember { mutableStateOf(false) }

                                    val bgCardColor = if (isFocused) {
                                        BloodRed.copy(alpha = 0.15f)
                                    } else if (isPlaying) {
                                        Color.White.copy(alpha = 0.06f)
                                    } else {
                                        Color.Transparent
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bgCardColor)
                                            .border(
                                                width = 1.dp,
                                                color = if (isFocused) BloodRed else if (isPlaying) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                viewModel.playChannel(ch)
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Logo
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.DarkGray)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Column Details
                                        Column(modifier = Modifier.weight(1f)) {
                                            val displayChannelName = if (ch.name.firstOrNull()?.isDigit() == true) {
                                                ch.name
                                            } else {
                                                "${index + 6} ${ch.name}"
                                            }

                                            Text(
                                                text = displayChannelName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isFocused) BloodRed else Color.White,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = ch.currentProgram ?: "No dato",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isFocused) TextLight else TextLight.copy(alpha = 0.5f),
                                                maxLines = 1
                                            )
                                        }

                                        // Favorites toggle button
                                        IconButton(
                                            onClick = { viewModel.toggleFavorite(ch) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = if (ch.isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                                                tint = if (ch.isFavorite) Color(0xFFFBBF24) else if (isFocused) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Catchup loop arrow click play icon
                                        IconButton(
                                            onClick = { viewModel.playChannel(ch) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Catchup",
                                                tint = if (isFocused) BloodRed else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- DISMISS SENSITIVE CLICK ZONE (RIGHT) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showIptvOverlay = false }
                )
            }
        }
        }

    // Dialog: Switch Alternate Playlist stream source
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Múltiples Fuentes Disponibles", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "Sintonizamos el mismo título de forma global en tus listas. Selecciona cuál deseas reproducir:",
                        color = TextLight.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentChannel.sources) { src ->
                            val isActive = src.streamUrl == activeStreamUrl
                            SourceItemRow(
                                title = src.playlistName,
                                subtitle = src.streamUrl,
                                isActive = isActive,
                                onClick = {
                                    viewModel.changeSource(src.streamUrl)
                                    showSourceDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("Cerrar", color = BloodRed)
                }
            },
            containerColor = CardSlate
        )
    }

    // Dialog: Switch Video Quality
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Configuración de Calidad del Video", color = Color.White) },
            text = {
                Column {
                    qualityOptions.forEach { q ->
                        val isActive = q == selectedQuality
                        SourceItemRow(
                            title = q,
                            subtitle = if (isActive) "Activo ahora" else "Tasa de bits variable",
                            isActive = isActive,
                            onClick = {
                                selectedQuality = q
                                showQualityDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cerrar", color = BloodRed)
                }
            },
            containerColor = CardSlate
        )
    }

    // Dialog: Switch Audio Track
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Selección de Audio & Idioma", color = Color.White) },
            text = {
                Column {
                    audioOptions.forEach { aud ->
                        val isActive = aud == selectedAudio
                        SourceItemRow(
                            title = aud,
                            subtitle = if (isActive) "Activo ahora" else "Pista de audio",
                            isActive = isActive,
                            onClick = {
                                selectedAudio = aud
                                showAudioDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDialog = false }) {
                    Text("Cerrar", color = BloodRed)
                }
            },
            containerColor = CardSlate
        )
    }

    // Animated Right-Sided Configuration Sidebar Overlay (Perfect TV symmetry, high contrast & 100% remote layout visible)
    if (showConfigOverlay) {
        Box(
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
            color = Color.Black.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
                .clickable(enabled = true, onClick = {}) // Consume all click/tap events inside panel
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Block with Close Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes del Reproductor",
                            tint = BloodRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "AJUSTES DE VIDEO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Personalización del Directo",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                    IconButton(
                        onClick = { showConfigOverlay = false },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // 1. FORMATO DE PANTALLA
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FORMATO DE PANTALLA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )
                     val formats = listOf("Normal", "Pantalla completa", "forzar 16:9", "4:3")
                     formats.forEachIndexed { fIdx, fmt ->
                         val isSelected = videoFormat == fmt
                         AudioFixItemRow(
                             title = fmt,
                             subtitle = when(fmt) {
                                 "Normal" -> "Relación de aspecto original sin recortes."
                                 "Pantalla completa" -> "Ajustar al tamaño total del marco."
                                 "forzar 16:9" -> "Forzar pantalla panorámica moderna."
                                 "4:3" -> "Forzar escala clásica televisiva."
                                 else -> ""
                             },
                            isSelected = isSelected,
                            onClick = { viewModel.setVideoFormat(fmt) },
                            modifier = if (fIdx == 0) Modifier.focusRequester(firstConfigFocusRequester) else Modifier
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. FUENTE / REPRODUCTOR
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FUENTES DISPONIBLES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )
                    val sourcesList = currentChannel.sources
                    val engines = if (sourcesList.isNotEmpty()) {
                        sourcesList.indices.map { "Línea de Reserva ${it + 1}" }
                    } else {
                        listOf("Reproductor 1")
                    }
                    engines.forEachIndexed { idx, eng ->
                        val isSelected = if (idx < sourcesList.size) {
                            activeStreamUrl == sourcesList[idx].streamUrl
                        } else {
                            videoPlayerEngine == eng
                        }
                        AudioFixItemRow(
                            title = eng,
                            subtitle = if (idx < sourcesList.size) "Sintonizar de playlist: ${sourcesList[idx].playlistName}" else "Motor por defecto.",
                            isSelected = isSelected,
                            onClick = {
                                if (idx < sourcesList.size) {
                                    viewModel.changeSource(sourcesList[idx].streamUrl)
                                }
                                viewModel.setVideoPlayerEngine(eng)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. IDIOMA Y AUDIO
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "IDIOMA PREFERENTE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )
                    val languages = listOf("Español Latino", "Castellano", "Inglés Original")
                    languages.forEach { lang ->
                        AudioFixItemRow(
                            title = lang,
                            subtitle = "Forzar pista de audio etiquetada en $lang.",
                            isSelected = videoLanguage == lang,
                            onClick = { viewModel.setVideoLanguage(lang) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. SUBTÍTULOS
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SUBTÍTULOS DEL VIDEO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )
                    val subs = listOf("Desactivados", "Español", "Inglés")
                    subs.forEach { sub ->
                        AudioFixItemRow(
                            title = sub,
                            subtitle = if (sub == "Desactivados") "Desactivar representación de subs." else "Priorizar subtítulos en $sub.",
                            isSelected = videoSubtitles == sub,
                            onClick = { viewModel.setVideoSubtitles(sub) }
                        )
                    }
                }
            }
        }
    }
    }

        // --- FLOATING AUDIO FIXES HANDLE TAB (pestaña) ---
        // High-contrast premium red tab slides in/out only alongside HUD controls
        if (showHudControls && !showIptvOverlay && !showAudioFixesOverlay && !showConfigOverlay) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 0.dp)
                ) {
                var isTabFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (isTabFocused) Color(0xFFFF2530) else BloodRed.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp, 
                        if (isTabFocused) Color.White else Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .onFocusChanged { isTabFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            showAudioFixesOverlay = true
                            showHudControls = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Pestaña de Fixes de Audio",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SOLUCIONAR AUDIO 🛠️",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
        }

        // --- FLOATING AUDIO FIXES SIDEBAR OVERLAY (SLIDES IN FROM RIGHT) ---
        if (showAudioFixesOverlay) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                color = Color.Black.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .clickable(enabled = true, onClick = {}) // Consume all click/tap events inside panel
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Block with Close Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Audio Fixes icon",
                                tint = BloodRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "REPARAR AUDIO",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "Canales en Vivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAudioFixesOverlay = false },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Text(
                        text = "Si escuchas ruido estático, silencio o paros de reproducción, selecciona una combinación alternativa de decodificador abajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Selector de motor / decodificador
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DECÓDER / MOTOR REPRODUCTOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = BloodRed
                        )
                        AudioFixItemRow(
                            title = "Por Defecto (ExoPlayer)",
                            subtitle = "Aceleración por HW normal de Android.",
                            isSelected = !codecsPackInstalled && !libVlcInstalled,
                            onClick = {
                                viewModel.setCodecsPackInstalled(false)
                                viewModel.setLibVlcUtilityInstalled(false)
                            }
                        )
                        AudioFixItemRow(
                            title = "Software (JLayer MP2 Fix)",
                            subtitle = "Decodificación por software ideal para canales MPEG2/MPGA en vivo.",
                            isSelected = codecsPackInstalled,
                            onClick = {
                                viewModel.setCodecsPackInstalled(true)
                                viewModel.setLibVlcUtilityInstalled(false)
                            }
                        )
                        AudioFixItemRow(
                            title = "Motor Alternativo (VLC Media)",
                            subtitle = "Librerías adaptables para audio complejo.",
                            isSelected = libVlcInstalled,
                            onClick = {
                                viewModel.setLibVlcUtilityInstalled(true)
                                viewModel.setCodecsPackInstalled(false)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Omitir silenciador en errores de inicio
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ESTRATEGIA ANTE FALLOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = BloodRed
                        )
                        AudioFixItemRow(
                            title = "Silenciar Pista con Falla",
                            subtitle = "Desactiva audio temporalmente si el renderizador tira error.",
                            isSelected = !audioFixIgnoreErrors,
                            onClick = { viewModel.setAudioFixIgnoreErrors(false) }
                        )
                        AudioFixItemRow(
                            title = "Ignorar Fallas (Auto-Recuperar)",
                            subtitle = "Fuerza reintento de sonido continuo en vez de silenciar.",
                            isSelected = audioFixIgnoreErrors,
                            onClick = { viewModel.setAudioFixIgnoreErrors(true) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. Mezcla de pistas y canales
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CANALES DE AUDIO (DOWNMIX)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = BloodRed
                        )
                        val modes = listOf(
                            Triple("Automático (Stereo)", "Stereo", "Usa la salida estándar por defecto del stream."),
                            Triple("Forzar Estéreo (F-Stereo)", "Forzar Estéreo", "Convierte flujos 5.1/Dolby/Surround a Estéreo."),
                            Triple("Forzar Mono", "Forzar Mono", "Fuerza la salida a un solo canal de audio.")
                        )
                        modes.forEach { (label, value, desc) ->
                            val isSelected = audioFixChannels == value || (value == "Stereo" && audioFixChannels == "Stereo")
                            AudioFixItemRow(
                                title = label,
                                subtitle = desc,
                                isSelected = isSelected,
                                onClick = { viewModel.setAudioFixChannels(value) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 4. Idiomas preferidos
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "IDIOMA PREFERENTE DEL STREAMING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = BloodRed
                        )
                        val langs = listOf("Español Latino", "Castellano", "Inglés Original")
                        langs.forEach { lang ->
                            AudioFixItemRow(
                                title = lang,
                                subtitle = "Prioriza pistas con esta etiqueta de idioma.",
                                isSelected = videoLanguage == lang,
                                onClick = { viewModel.setVideoLanguage(lang) }
                            )
                        }
                    }
                }
            }
        }
        }

        // --- FLOATING SEASONS & EPISODES SIDEBAR OVERLAY (SLIDES IN FROM RIGHT) ---
        if (showEpisodesOverlay) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                color = Color.Black.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .clickable(enabled = true, onClick = {}) // Consume all click/tap events inside panel
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Block with Close Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Episodes icon",
                                tint = BloodRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "TEMPORADAS Y EPISODIOS",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = currentChannel.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(
                            onClick = { showEpisodesOverlay = false },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Season Selector Section (Horizontal scrollable bar of Season buttons)
                    Text(
                        text = "SELECCIONAR TEMPORADA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uniqueSeasons) { seasonNum ->
                            val isSelected = seasonNum == selectedSeason
                            var isSeasonFocused by remember { mutableStateOf(false) }

                            val bgColor = if (isSeasonFocused) {
                                BloodRed
                            } else if (isSelected) {
                                Color.White.copy(alpha = 0.2f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSeasonFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { isSeasonFocused = it.isFocused }
                                    .clickable {
                                        viewModel.selectSeason(seasonNum)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Temp $seasonNum",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Episode List Section
                    Text(
                        text = "EPISODIOS DISPONIBLES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = BloodRed
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(episodesInSelectedSeason) { idx, epChannel ->
                            val info = viewModel.parseSeriesEpisode(epChannel)
                            val epNum = info?.episode ?: (idx + 1)
                            val isSelected = epNum == selectedEpisodeNum

                            var isEpFocused by remember { mutableStateOf(false) }

                            val epBgColor = if (isEpFocused) {
                                BloodRed
                            } else if (isSelected) {
                                Color.White.copy(alpha = 0.16f)
                            } else {
                                Color.White.copy(alpha = 0.04f)
                            }

                            val focusModifier = if (idx == 0) Modifier.focusRequester(firstEpisodeFocusRequester) else Modifier

                            Row(
                                modifier = focusModifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(epBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (isEpFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { isEpFocused = it.isFocused }
                                    .clickable {
                                        viewModel.selectEpisode(epNum)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) BloodRed else Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$epNum",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = epChannel.name.substringAfter("E$epNum").substringAfter("e$epNum").trim().ifEmpty { "Episodio $epNum" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Reproducir este capítulo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproduciendo actual",
                                        tint = BloodRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // Floating Netflix-like Resume Banner
        if (showRestoredToast && savedProgressState != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 120.dp)
            ) {
                savedProgressState?.let { progress ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0E).copy(alpha = 0.92f)),
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .border(1.dp, BloodRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reanudado",
                            tint = BloodRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reanudado desde ${formatMs(progress.position)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Seguir viendo o empezar de nuevo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                        Button(
                            onClick = {
                                playerInstance?.seekTo(0L)
                                currentPosition = 0L
                                showRestoredToast = false
                                viewModel.deletePlaybackProgress(activeStreamUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Reiniciar",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }
}


@Composable
fun ConfigOptionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = if (isFocused) {
        BloodRed
    } else if (isSelected) {
        Color.White.copy(alpha = 0.25f)
    } else {
        Color.White.copy(alpha = 0.1f)
    }

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Activo",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

@Composable
fun PlayerPillBtn(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(if (isFocused) BloodRed else Color.White.copy(alpha = 0.15f))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isFocused) Color.White else TextLight,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (isFocused) Color.White else TextLight
        )
    }
}

@Composable
fun SourceItemRow(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) BloodRed.copy(alpha = 0.15f)
                else if (isActive) Color.White.copy(alpha = 0.05f)
                else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (isActive) BloodRed else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isFocused) BloodRed else Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextLight.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Activo",
                tint = BloodRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AudioFixItemRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardBgColor = if (isSelected) {
        BloodRed.copy(alpha = 0.15f)
    } else if (isFocused) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.03f)
    }
    val borderColor = if (isFocused) {
        Color.White
    } else if (isSelected) {
        BloodRed
    } else {
        Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        RadioButton(
            selected = isSelected,
            onClick = { onClick() },
            colors = RadioButtonDefaults.colors(
                selectedColor = BloodRed,
                unselectedColor = Color.White.copy(alpha = 0.4f)
            )
        )
    }
}

