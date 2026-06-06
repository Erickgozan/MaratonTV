package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import com.example.R
import com.example.data.UiChannel
import com.example.ui.TvViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.BloodRed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.TextLight
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainTvDashboard(viewModel: TvViewModel) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val allChannels by viewModel.allChannels.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val profile by viewModel.currentProfile.collectAsState()
    val syncStatus by viewModel.cloudSyncStatus.collectAsState()
    val scrapedTitle by viewModel.scrapedTitle.collectAsState()
    val isPlayerMaximized by viewModel.isPlayerMaximized.collectAsState()
    val playingChannel by viewModel.playingChannel.collectAsState()

    var showVoiceSearchDialog by remember { mutableStateOf(false) }
    var clockTime by remember { mutableStateOf("") }

    // Real-time clock update (Matches PM clock in header!)
    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            clockTime = formatter.format(Date())
            delay(1000)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 760

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCharcoal)
        ) {
        // --- 1. Left Sidebar Navigation Menu ---
        Column(
            modifier = Modifier
                .width(if (isCompactScreen) 64.dp else 200.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 14.dp, horizontal = if (isCompactScreen) 6.dp else 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = if (isCompactScreen) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // App branding name (Professional and Beautiful)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = if (isCompactScreen) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp, start = if (isCompactScreen) 0.dp else 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.blooders_app_logo_1779688823340),
                        contentDescription = "Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(30.dp)
                            .wrapContentWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (!isCompactScreen) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row {
                            Text(
                                text = "BLOODERS ",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "TV",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = BloodRed
                            )
                        }
                    }
                }

                // Category List Buttons (Mapped to clean core Icons replicating Xuper design)
                val categories = listOf(
                    Triple("TV", "TV", Icons.Default.PlayArrow),
                    Triple("FAVORITOS", "FAVORITOS", Icons.Default.Star),
                    Triple("PELICULA", "PELÍCULA", Icons.Default.PlayArrow),
                    Triple("SERIES", "SERIES", Icons.Default.List),
                    Triple("KIDS", "KIDS", Icons.Default.Face),
                    Triple("ANIME", "ANIME", Icons.Default.Star),
                    Triple("HISTORIAL", "HISTORIAL", Icons.Default.Refresh)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (key, label, icon) ->
                        val isSelected = activeCategory == key && query.isEmpty()
                        SideDrawerItem(
                            label = label,
                            icon = icon,
                            isSelected = isSelected,
                            categoryKey = key,
                            onClick = { viewModel.selectCategory(key) },
                            showLabel = !isCompactScreen
                        )
                    }
                }
            }

            // Quick access to Settings
            SideDrawerItem(
                label = "AJUSTES",
                icon = Icons.Default.Settings,
                isSelected = false,
                categoryKey = "SETTINGS",
                onClick = { viewModel.setScreen("SETTINGS") },
                showLabel = !isCompactScreen
            )
        }

        // --- 2. Main Content Frame (Header & Dashboard cards grid) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // HEADER BAR: Conforming exactly to screenshots from video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header Titles (Left side)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        Text(
                            text = "Búsqueda: \"$query\"",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = when (activeCategory) {
                                "TV" -> "Guía de Canales"
                                "PELICULA" -> "Películas"
                                "SERIES" -> "Series & Novelas"
                                "KIDS" -> "Infantil"
                                "ANIME" -> "Anime de Culto"
                                "HISTORIAL" -> "Historial de Reproducción"
                                "EXTENSION" -> scrapedTitle
                                else -> "Contenido de TV"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BloodRed.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Live (${filteredChannels.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = BloodRed
                        )
                    }
                }

                // Header Utility Action bar (Minimal outline style matching video screenshot)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 16.dp)
                ) {
                    // search button icon
                    HeaderIconBtn(
                        icon = Icons.Default.Search,
                        contentDescription = "Búsqueda por voz global",
                        onClick = { showVoiceSearchDialog = true }
                    )

                    // history clock icon
                    HeaderIconBtn(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Historial",
                        onClick = { viewModel.selectCategory("HISTORIAL") }
                    )

                    // Profile silhouette avatar or custom image
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            .clickable { viewModel.setScreen("PROFILES") }
                    ) {
                        profile?.let { activeProf ->
                            AsyncImage(
                                model = activeProf.avatarUrl,
                                contentDescription = activeProf.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Perfil",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp).fillMaxSize()
                        )
                    }

                    if (!isCompactScreen) {
                        // Wifi strength indicator icon (Star as mockup or build custom SVG indicator)
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "WIFI OK",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // High-resolution local Clock time formatted perfectly (e.g. 12:04 AM)
                    Text(
                        text = clockTime.ifEmpty { "12:04 AM" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = if (isCompactScreen) 2.dp else 0.dp)
                    )
                }
            }

            // MIDDLE AREA CONTENT
            Box(modifier = Modifier.weight(1f)) {
                if (activeCategory == "TV" && query.isEmpty()) {
                    // SIDE BY SIDE LIVE TV SECTOR (Screenshot 2 / TV Mode)
                    TvGuideGridSector(
                        channels = filteredChannels,
                        viewModel = viewModel
                    )
                } else {
                    // CINEMATIC SIDE-BY-SIDE BILLBOARD & CAROUSEL (Screenshot 1 / Highlights Mode)
                    HomeMoviesSector(
                        channels = filteredChannels,
                        allChannels = allChannels,
                        categoryTitle = activeCategory,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Speech / Voice Recognition Dialog representation (Voice Search Requirement)
    var voiceInputSimulatorText by remember(showVoiceSearchDialog) { mutableStateOf(query) }

    if (showVoiceSearchDialog) {
        Dialog(
            onDismissRequest = { showVoiceSearchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT COLUMN: Keyboard and input field (35% width)
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .background(Color(0xFF0C0D14)) // Dark slate-black keyboard background
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = BloodRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BÚSQUEDA GLOBAL", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }

                            // Text Input Field
                            OutlinedTextField(
                                value = voiceInputSimulatorText,
                                onValueChange = {
                                    voiceInputSimulatorText = it
                                    viewModel.updateSearchQuery(it)
                                },
                                placeholder = { Text("Escribe para buscar...", color = Color.White.copy(alpha = 0.4f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color(0xFF1E1F24),
                                    unfocusedContainerColor = Color(0xFF13141C),
                                    cursorColor = BloodRed,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Keyboard Header Special Buttons Row (123, Trash, Backspace, Busca)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Clear (trash) button
                                Button(
                                    onClick = {
                                        voiceInputSimulatorText = ""
                                        viewModel.updateSearchQuery("")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232533)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Limpiar", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Cancel click back button
                                Button(
                                    onClick = {
                                        if (voiceInputSimulatorText.isNotEmpty()) {
                                            val nextText = voiceInputSimulatorText.dropLast(1)
                                            voiceInputSimulatorText = nextText
                                            viewModel.updateSearchQuery(nextText)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232533)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retroceso", tint = Color.White, modifier = Modifier.size(16.dp))
                                }

                                // Busca button (Dismisses search with selected filters kept)
                                Button(
                                    onClick = { showVoiceSearchDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text("Buscar", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }
                            }

                            // Alphabet letters grid (A - Z, Space)
                            val alphabetRows = listOf(
                                listOf("A", "B", "C", "D", "E", "F", "G"),
                                listOf("H", "I", "J", "K", "L", "M", "N"),
                                listOf("O", "P", "Q", "R", "S", "T", "U"),
                                listOf("V", "W", "X", "Y", "Z", " ")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                alphabetRows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        row.forEach { char ->
                                            val isSpace = char == " "
                                            Button(
                                                onClick = {
                                                    val nextText = voiceInputSimulatorText + char
                                                    voiceInputSimulatorText = nextText
                                                    viewModel.updateSearchQuery(nextText)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232533)),
                                                contentPadding = PaddingValues(0.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(if (isSpace) 2f else 1f).height(36.dp)
                                            ) {
                                                if (isSpace) {
                                                    // Drawing space bar symbol
                                                    Box(
                                                        modifier = Modifier
                                                            .width(50.dp)
                                                            .height(3.dp)
                                                            .background(Color.White)
                                                    )
                                                } else {
                                                    Text(char, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Feedback button mockup
                        Button(
                            onClick = { showVoiceSearchDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Cerrar Buscador", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // RIGHT COLUMN: Results and matching channels (65% width)
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (voiceInputSimulatorText.isEmpty()) "Canales Sugeridos" else "Resultados de Búsqueda",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )

                            Text(
                                text = "Coincidencias: ${filteredChannels.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFFBBF24)
                            )
                        }

                        // Grid displaying matching channels as media cards
                        if (filteredChannels.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No se encontraron canales. Escribe con el teclado lateral.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(filteredChannels, key = { it.primaryStreamUrl }) { ch ->
                                    LandscapeMediaCard(
                                        channel = ch,
                                        onTrailerPlay = {},
                                        onClick = {
                                            viewModel.selectChannelDetails(ch)
                                            showVoiceSearchDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        val isTv = playingChannel?.groupTitle != "PELICULA" && playingChannel?.groupTitle != "SERIES"
        if (isPlayerMaximized && playingChannel != null && isTv) {
            FullscreenPlayer(
                viewModel = viewModel,
                isMaximized = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SideDrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    categoryKey: String,
    onClick: () -> Unit,
    showLabel: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }

    // Dynamic icon colors to match the professional mockup
    // (Live TV active is modern electric blue, movie/shows active are bold red/white)
    val activeColor = when (categoryKey) {
        "TV" -> Color(0xFF3B82F6) // Electric blue TV indicator!
        else -> BloodRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) activeColor.copy(alpha = 0.15f)
                else if (isFocused) Color.White.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = if (showLabel) 14.dp else 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.Start else Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else if (isFocused) Color.White else TextLight.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = if (isSelected) activeColor else if (isFocused) Color.White else TextLight.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun HeaderIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isFocused) BloodRed else Color.White.copy(alpha = 0.05f))
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.White else tint.copy(alpha = 0.85f),
            modifier = Modifier.size(17.dp)
        )
    }
}

// Side by Side Surfing layout conforming perfectly to Screenshot 2
@Composable
fun TvGuideGridSector(
    channels: List<UiChannel>,
    viewModel: TvViewModel
) {
    val playingChannel by viewModel.playingChannel.collectAsState()
    val isPlayerMaximized by viewModel.isPlayerMaximized.collectAsState()

    var selectedSubCategory by remember(channels) { mutableStateOf("Todos") }

    val subCategories = remember(channels) {
        listOf("Todos") + channels.map { it.originalGroup }.filter { it.isNotBlank() }.distinct()
    }

    val filteredDisplayChannels = remember(channels, selectedSubCategory) {
        if (selectedSubCategory == "Todos") {
            channels
        } else {
            channels.filter { it.originalGroup == selectedSubCategory }
        }
    }

    var currentActivePreviewChannel by remember(filteredDisplayChannels) {
        mutableStateOf(
            filteredDisplayChannels.find { it.primaryStreamUrl == playingChannel?.primaryStreamUrl }
                ?: filteredDisplayChannels.firstOrNull()
        )
    }

    val listState = rememberLazyListState()
    var hasScrolledToInitial by remember(filteredDisplayChannels) { mutableStateOf(false) }

    LaunchedEffect(filteredDisplayChannels, playingChannel) {
        val playingInList = filteredDisplayChannels.find { it.primaryStreamUrl == playingChannel?.primaryStreamUrl }
        if (playingInList != null) {
            currentActivePreviewChannel = playingInList
            val index = filteredDisplayChannels.indexOfFirst { it.primaryStreamUrl == playingInList.primaryStreamUrl }
            if (index >= 0) {
                listState.scrollToItem(index)
                hasScrolledToInitial = true
            }
        } else {
            if (currentActivePreviewChannel == null || !filteredDisplayChannels.any { it.name == currentActivePreviewChannel?.name }) {
                currentActivePreviewChannel = filteredDisplayChannels.firstOrNull()
            }
            if (!hasScrolledToInitial && currentActivePreviewChannel != null) {
                val index = filteredDisplayChannels.indexOfFirst { it.primaryStreamUrl == currentActivePreviewChannel?.primaryStreamUrl }
                if (index >= 0) {
                    listState.scrollToItem(index)
                    hasScrolledToInitial = true
                }
            }
        }
    }

    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val isHistorial = viewModel.activeCategory.collectAsState().value == "HISTORIAL"
            val emptyMsg = if (isHistorial) {
                "Aún no has reproducido ninguna película o serie. Tu historial de reproducción aparecerá aquí."
            } else {
                "No hay canales disponibles en esta categoría."
            }
            Text(emptyMsg, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column (65% space): Main Video Player window
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(end = 16.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable {
                        currentActivePreviewChannel?.let { activeCh ->
                            viewModel.playChannel(activeCh)
                        }
                    }
            ) {
                currentActivePreviewChannel?.let { activeCh ->
                    var initialPosition by remember(activeCh.primaryStreamUrl) { mutableStateOf(-1L) }
                    LaunchedEffect(activeCh.primaryStreamUrl) {
                        val inMemory = viewModel.getLivePlaybackPosition(activeCh.primaryStreamUrl)
                        if (inMemory > 0L) {
                            initialPosition = inMemory
                        } else {
                            val dbProgress = viewModel.getPlaybackProgress(activeCh.primaryStreamUrl)
                            initialPosition = dbProgress?.position ?: 0L
                        }
                    }

                    if (initialPosition >= 0L && !isPlayerMaximized) {
                        VideoPlayer(
                            streamUrl = activeCh.primaryStreamUrl,
                            modifier = Modifier.fillMaxSize(),
                            showController = false,
                            initialPosition = initialPosition,
                            onPositionUpdated = { currentPos, duration ->
                                viewModel.setLivePlaybackPosition(activeCh.primaryStreamUrl, currentPos)
                                val isTvCategory = activeCh.groupTitle != "PELICULA" && activeCh.groupTitle != "SERIES"
                                if (!isTvCategory && currentPos > 1000 && duration > 10000) {
                                    if (currentPos > duration * 0.95f) {
                                        viewModel.deletePlaybackProgress(activeCh.primaryStreamUrl)
                                    } else {
                                        viewModel.savePlaybackProgress(activeCh.primaryStreamUrl, currentPos, duration)
                                    }
                                }
                            },
                            isEmbedText = activeCh.isEmbedText,
                            adBlockerEnabled = activeCh.adBlockerEnabled
                        )

                        // Intercept clicks on dashboard preview player to launch fullscreen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .clickable {
                                    viewModel.playChannel(activeCh)
                                }
                        )
                    }

                    // Overlay rating badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FHD LIVE • SATÉLITE",
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Bottom info bar overlay matching video controller screenshot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = activeCh.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            activeCh.currentProgram?.let {
                                Text(
                                    text = "Sintonizando: $it",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFF3B82F6) // Matches blue selection color
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Favorites toggle button
                            IconButton(
                                onClick = { viewModel.toggleFavorite(activeCh) },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = if (activeCh.isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                                    tint = if (activeCh.isFavorite) Color(0xFFFBBF24) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Fullscreen enlarge expand icon
                            IconButton(
                                onClick = { viewModel.playChannel(activeCh) },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Pantalla Completa",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column (35% space): Vertical slider list of Channels surfing (Replicating Screenshot 2 right list)
        Column(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal row of sub-category chips for Live TV grouping
            if (subCategories.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    items(subCategories) { cat ->
                        val isCatSelected = cat == selectedSubCategory
                        var isCatFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCatSelected) BloodRed
                                    else if (isCatFocused) Color.White.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCatSelected) BloodRed else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .onFocusChanged { isCatFocused = it.isFocused }
                                .clickable { selectedSubCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Scroll Up Arrow Indicator Replicating Screenshot 2
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Desplazar Arriba",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(24.dp)
                    .padding(bottom = 2.dp)
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredDisplayChannels, key = { it.primaryStreamUrl }) { channel ->
                    val isSelectedPreview = channel.name == currentActivePreviewChannel?.name
                    TvCompactChannelRow(
                        channel = channel,
                        isSelected = isSelectedPreview,
                        onFocused = {
                            currentActivePreviewChannel = channel
                        },
                        onClick = {
                            viewModel.playChannel(channel)
                        }
                    )
                }
            }

            // Scroll Down Arrow Indicator Replicating Screenshot 2
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Desplazar Abajo",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun TvCompactChannelRow(
    channel: UiChannel,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) Color(0xFF1E293B) // Dark slate on search focus
                else if (isSelected) Color(0xFF1E1F24) // Match mockup black gray
                else CardSlate
            )
            .border(
                width = 1.1.dp,
                color = if (isFocused) Color(0xFF3B82F6) else if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel Image logo (Screenshot 2 left square)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Play Triangle indicator ▶ only visible on active item row!
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Active channel",
                tint = Color(0xFF3B82F6),
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 6.dp)
            )
        }

        // Channel Name
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color(0xFF3B82F6) else Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        // Favorites indicator icon
        if (channel.isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorito",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Cinematic Layout Conforming to Screenshot 1 Side-by-Side arrangement
@Composable
fun HomeMoviesSector(
    channels: List<UiChannel>,
    allChannels: List<UiChannel>,
    categoryTitle: String,
    viewModel: TvViewModel
) {
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontró contenido en esta categoría.", color = TextLight, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    var selectedSubCategory by remember(channels) { mutableStateOf("Todos") }

    val subCategories = remember(channels) {
        listOf("Todos") + channels.map { it.originalGroup }.filter { it.isNotBlank() }.distinct()
    }

    val filteredDisplayChannels = remember(channels, selectedSubCategory) {
        if (selectedSubCategory == "Todos") {
            channels
        } else {
            channels.filter { it.originalGroup == selectedSubCategory }
        }
    }

    val playingChannel by viewModel.playingChannel.collectAsState()

    // Default primary featured highlight billboard movie/show
    var selectedBillboardChannel by remember(filteredDisplayChannels) {
        mutableStateOf(
            filteredDisplayChannels.find { it.primaryStreamUrl == playingChannel?.primaryStreamUrl }
                ?: filteredDisplayChannels.firstOrNull()
        )
    }

    val isSeriesCategory = categoryTitle.equals("SERIES", ignoreCase = true)
    var activePremiumSeriesBanner by remember { mutableStateOf(0) } // 0 = Breaking Bad, 1 = Better Call Saul

    // Auto rotation effect for the premium series banner
    LaunchedEffect(isSeriesCategory) {
        if (isSeriesCategory) {
            while (true) {
                delay(8000)
                activePremiumSeriesBanner = (activePremiumSeriesBanner + 1) % 2
            }
        }
    }

    // Dynaymically find first episodes of Breaking Bad and Better Call Saul
    val bbTargetChannel = remember(allChannels) {
        allChannels.find { it.name.contains("Breaking Bad", ignoreCase = true) && it.name.contains("E01", ignoreCase = true) }
            ?: allChannels.find { it.name.contains("Breaking Bad", ignoreCase = true) }
    }
    val bcsTargetChannel = remember(allChannels) {
        allChannels.find { it.name.contains("Better Call Saul", ignoreCase = true) && it.name.contains("E01", ignoreCase = true) }
            ?: allChannels.find { it.name.contains("Better Call Saul", ignoreCase = true) }
    }

    // Other 3 movies in the category stacked vertically to the right (Replicating Screenshot 1 layout)
    val sideList = remember(filteredDisplayChannels, selectedBillboardChannel) {
        val filtered = filteredDisplayChannels.filter { it.name != selectedBillboardChannel?.name }
        if (filtered.isNotEmpty()) filtered.take(3) else filteredDisplayChannels.take(3)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- Category Selection Chips (Parsed from user's custom checklist!) ---
        if (subCategories.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                items(subCategories) { cat ->
                    val isCatSelected = cat == selectedSubCategory
                    var isCatFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCatSelected) BloodRed
                                else if (isCatFocused) Color.White.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCatSelected) BloodRed else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .clickable { selectedSubCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- 1. Cinematic Side-by-Side Showcase (60% Widescreen Highlight, 40% Widescreen cards stacked) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Huge main Billboard Card
            if (isSeriesCategory) {
                // PREMIUM CAROUSEL BANNER: BREAKING BAD & BETTER CALL SAUL
                val currentBannerIndex = activePremiumSeriesBanner
                val premiumTitle = if (currentBannerIndex == 0) "Breaking Bad" else "Better Call Saul"
                val premiumSubtitle = if (currentBannerIndex == 0) "LA MEJOR SERIE DE LA HISTORIA" else "EL ARTE DE LAS DEFENSAS JURÍDICAS"
                val premiumSynopsis = if (currentBannerIndex == 0) {
                    "Un profesor de química con cáncer terminal manufactura metanfetaminas para asegurar la estabilidad financiera de su familia."
                } else {
                    "La caída moral del abogado Jimmy McGill mientras se transforma progresivamente en el excéntrico Saul Goodman."
                }
                val premiumThemeColor = if (currentBannerIndex == 0) Color(0xFF10B981) else Color(0xFFFBBF24) // Acid Green vs Saul Gold
                val premiumRating = if (currentBannerIndex == 0) "9.5" else "9.0"
                val premiumYear = if (currentBannerIndex == 0) "2008" else "2015"
                val premiumBackdrop = if (currentBannerIndex == 0) {
                    "https://images.unsplash.com/photo-1509316785289-025f5b846b35?w=800" // RV Desert feel
                } else {
                    "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=800" // Law context
                }
                val premiumTargetChannel = if (currentBannerIndex == 0) bbTargetChannel else bcsTargetChannel

                var isBannerFocused by remember { mutableStateOf(false) }
                val bannerScale = if (isBannerFocused) 1.02f else 1.0f

                Card(
                    onClick = {
                        viewModel.selectPremiumSeriesByName(premiumTitle)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .onFocusChanged { isBannerFocused = it.isFocused }
                        .scale(bannerScale)
                        .border(
                            width = if (isBannerFocused) 2.5.dp else 1.dp,
                            color = if (isBannerFocused) premiumThemeColor else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = premiumBackdrop,
                            contentDescription = premiumTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.95f),
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.7f)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(premiumThemeColor)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "RECOMENDADO",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                                fontSize = 9.sp
                                            ),
                                            color = Color.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = premiumTitle,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White
                                )

                                Text(
                                    text = premiumSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = premiumThemeColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Text(
                                text = premiumSynopsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                maxLines = 2,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.15f
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFBBF24))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = " ★ $premiumRating IMDb ",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black
                                        )
                                    }

                                    Text(
                                        text = "Año: $premiumYear | Culto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (currentBannerIndex == 0) premiumThemeColor else Color.White.copy(alpha = 0.3f))
                                            .clickable { activePremiumSeriesBanner = 0 }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (currentBannerIndex == 1) premiumThemeColor else Color.White.copy(alpha = 0.3f))
                                            .clickable { activePremiumSeriesBanner = 1 }
                                    )
                                }
                            }
                        }

                        premiumTargetChannel?.let { target ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 36.dp)
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(premiumThemeColor)
                                    .clickable { viewModel.selectChannelDetails(target) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Detalles de Serie",
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                selectedBillboardChannel?.let { billboardMovie ->
                    var isBillboardFocused by remember { mutableStateOf(false) }
                    val billboardScale = if (isBillboardFocused) 1.02f else 1.0f

                    Card(
                        onClick = { viewModel.selectChannelDetails(billboardMovie) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .onFocusChanged { isBillboardFocused = it.isFocused }
                            .scale(billboardScale)
                            .border(
                                width = if (isBillboardFocused) 2.5.dp else 1.dp,
                                color = if (isBillboardFocused) BloodRed else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Background image
                            AsyncImage(
                                model = billboardMovie.logoUrl,
                                contentDescription = billboardMovie.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Elegant cinematic left-leaning gradient shadow
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.95f),
                                                Color.Black.copy(alpha = 0.6f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Content details overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.65f)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = billboardMovie.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "PREMIUM DE ESTRENO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                        color = BloodRed,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                // Description
                                Text(
                                    text = billboardMovie.synopsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    maxLines = 2,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.15f
                                )

                                // Rating badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFBBF24))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = " ★ ${billboardMovie.rating} IMDb ",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black
                                        )
                                    }

                                    Text(
                                        text = "Año: ${billboardMovie.year} | ${billboardMovie.groupTitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Vertical Stack of companion widescreen movie cards (Replicating Screenshot 1 layout)
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sideList.forEach { ch ->
                    var isCardFocused by remember { mutableStateOf(false) }

                    LaunchedEffect(isCardFocused) {
                        if (isCardFocused) {
                            delay(3000)
                            viewModel.showTrailerForChannel(ch)
                        }
                    }

                    Card(
                        onClick = { selectedBillboardChannel = ch },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onFocusChanged { isCardFocused = it.isFocused }
                            .border(
                                width = if (isCardFocused || selectedBillboardChannel?.name == ch.name) 2.dp else 1.dp,
                                color = if (isCardFocused || selectedBillboardChannel?.name == ch.name) BloodRed else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = ch.logoUrl,
                                contentDescription = ch.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Bottom darkened layer
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                        )
                                    )
                            )

                            // Title details label
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = ch.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }

                            // Rating bubble badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = " ★ ${ch.rating} ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Horizontal scroll categories catalog bar ---
        if (selectedSubCategory != "Todos") {
            // Show only the selected category
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = selectedSubCategory,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 2.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 6.dp)
                ) {
                    items(filteredDisplayChannels, key = { it.primaryStreamUrl }) { ch ->
                        LandscapeMediaCard(
                            channel = ch,
                            onTrailerPlay = { viewModel.showTrailerForChannel(it) },
                            onClick = { viewModel.selectChannelDetails(ch) }
                        )
                    }
                }
            }
        } else {
            // Group and display all sub-categories (originalGroup) found in this section
            val groupedCategories = remember(channels) {
                channels.groupBy { it.originalGroup }
            }

            groupedCategories.forEach { (categoryName, categoryChannels) ->
                val displayCatName = categoryName.ifEmpty { "Generales" }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = displayCatName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(start = 2.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(categoryChannels, key = { it.primaryStreamUrl }) { ch ->
                            LandscapeMediaCard(
                                channel = ch,
                                onTrailerPlay = { viewModel.showTrailerForChannel(it) },
                                onClick = { viewModel.selectChannelDetails(ch) }
                            )
                        }
                    }
                }
            }
        }

    }
}

fun getYoutubeTrailerId(channelName: String): String {
    return when {
         channelName.contains("matar", ignoreCase = true) -> "BfK6GAbh3v8"
         channelName.contains("Punisher", ignoreCase = true) -> "L6P3O1DdqAc"
         channelName.contains("Jack Ryan", ignoreCase = true) -> "1K_C_76X-k0"
         channelName.contains("The Boys", ignoreCase = true) -> "M1bhOaLv4i4"
         channelName.contains("From", ignoreCase = true) -> "c947d-fQ8K0"
         channelName.contains("Cartoon", ignoreCase = true) || channelName.contains("Kids", ignoreCase = true) -> "r9H_S-b2J8s"
         channelName.contains("Anime", ignoreCase = true) -> "KUb9NCSv4hA"
         channelName.contains("Historia", ignoreCase = true) || channelName.contains("Explora", ignoreCase = true) -> "BfK6GAbh3v8"
         else -> "dQw4w9WgXcQ"
    }
}

@Composable
fun LandscapeMediaCard(
    channel: UiChannel,
    onTrailerPlay: (UiChannel) -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(3000)
            onTrailerPlay(channel)
        }
    }

    Column(
        modifier = Modifier
            .width(180.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .height(110.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) BloodRed else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                )
                .background(CardSlate)
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // IMDb rating overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "★ ${channel.rating}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFBBF24)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (isFocused) BloodRed else Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

