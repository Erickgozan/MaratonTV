package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.example.data.UiChannel
import com.example.ui.TvViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.BloodRed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.TextLight

@Composable
fun MovieDetailsScreen(viewModel: TvViewModel) {
    val channel by viewModel.selectedChannel.collectAsState()
    val allChannels by viewModel.allChannels.collectAsState()

    val seriesEpisodes by viewModel.seriesEpisodes.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()
    val selectedEpisodeNum by viewModel.selectedEpisodeNum.collectAsState()
    val activeTrailerUrl by viewModel.activeTrailerUrl.collectAsState()

    val currentChannel = channel ?: return

    val imdbDetails by viewModel.imdbDetailsState.collectAsState()
    val isLoadingImdb by viewModel.isLoadingImdb.collectAsState()
    val disableTrailers by viewModel.disableTrailers.collectAsState()
    val trailerSource by viewModel.trailerSource.collectAsState()

    val displaySynopsis = if (isLoadingImdb) {
        "Cargando mejores detalles de IMDb España..."
    } else {
        imdbDetails?.synopsis ?: currentChannel.synopsis
    }

    val displayRating = imdbDetails?.rating ?: currentChannel.rating
    val displayYear = imdbDetails?.year ?: currentChannel.year
    val displayDirector = imdbDetails?.director ?: currentChannel.director
    val displayActors = imdbDetails?.actors ?: currentChannel.actors

    val companionMovies = remember(currentChannel) {
        val list = allChannels.filter { it.name != currentChannel.name }
        if (list.size >= 6) list.take(7) else list
    }

    var showLanguageOverlay by remember { mutableStateOf(false) }

    val epInfo = viewModel.parseSeriesEpisode(currentChannel)
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

    val isPlayerMaximized by viewModel.isPlayerMaximized.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCharcoal)
            .padding(if (isPlayerMaximized) 0.dp else 16.dp)
    ) {
        if (!isPlayerMaximized) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Upper Block: Cinemascope Top layout (Split Left: text metadata, Right: empty placeholder space for Preview window player overlaid on top)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Content: Title, info fields, genre list, and synopsis
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Return button to parent screen
                        IconButton(
                            onClick = { viewModel.selectChannelDetails(null) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White
                            )
                        }

                        // Dynamic Header conforming to Screenshot 6
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val displayName = if (isSeries) (epInfo?.seriesName ?: currentChannel.name) else currentChannel.name
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "★ $displayRating",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFFBBF24)
                            )
                            
                            if (isSeries) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "T$selectedSeason - E$selectedEpisodeNum",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }

                        // Metadata subtitle row
                        Text(
                            text = "América del Sur | $displayYear",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextLight.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val posterUrl = imdbDetails?.posterUrl ?: ""
                            if (posterUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = "Póster de contenido",
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.5.dp, BloodRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                // Chips / Tags (Genre tokens)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    GenreBadge(text = if (isSeries) "Series" else "Película")
                                    GenreBadge(text = if (imdbDetails?.sourceUsed == "TMDB") "TMDb" else "IMDb")
                                }

                                // Director & Cast info fields
                                Text(
                                    text = "Director :  $displayDirector",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "Actores :  $displayActors",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextLight.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Synopsis
                                Text(
                                    text = "Sinopsis: $displaySynopsis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.7f),
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.35,
                                    modifier = Modifier.padding(end = 16.dp),
                                    maxLines = 4
                                )
                            }
                        }

                        // Action controls row (D-pad focusable navigation)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            // Button 1: Full screen toggle
                            DetailActionButton(
                                text = "Pantalla c..",
                                icon = Icons.Default.PlayArrow,
                                onClick = { viewModel.setPlayerMaximized(true) }
                            )

                            // Button 2: Stream alternatives
                            DetailActionButton(
                                text = "Idioma",
                                icon = Icons.Default.Info,
                                onClick = { showLanguageOverlay = true }
                            )

                            // Button 3: Toggle Favorite state (Conforms to Rating Star style fallback)
                            DetailActionButton(
                                text = if (currentChannel.isFavorite) "Quitar Fav" else "Favorito",
                                icon = Icons.Default.Star,
                                onClick = { viewModel.toggleFavorite(currentChannel) }
                            )

                            // Button 4: Play Trailer from chosen source
                            if (!disableTrailers) {
                                DetailActionButton(
                                    text = "Tráiler",
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { viewModel.showTrailerForChannel(currentChannel) }
                                )
                            }
                        }

                        // Dynamic Series Season & Episode selection blocks conform to Image 2
                        if (isSeries) {
                            var showSeasonDropdown by remember { mutableStateOf(false) }
                            
                            Column(
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box {
                                        var isDropdownFocused by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isDropdownFocused) BloodRed else Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                .onFocusChanged { isDropdownFocused = it.isFocused }
                                                .clickable { showSeasonDropdown = true }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Temporada $selectedSeason",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack, // Standard back icon rotated/adapted or same chevron effect
                                                contentDescription = "Desplegar",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        
                                        DropdownMenu(
                                            expanded = showSeasonDropdown,
                                            onDismissRequest = { showSeasonDropdown = false },
                                            modifier = Modifier.background(CardSlate)
                                        ) {
                                            uniqueSeasons.forEach { sNum ->
                                                DropdownMenuItem(
                                                    text = { Text("Temporada $sNum", color = Color.White) },
                                                    onClick = {
                                                        viewModel.selectSeason(sNum)
                                                        showSeasonDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "1-${episodesInSelectedSeason.size}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextLight.copy(alpha = 0.5f)
                                    )
                                }

                                // Scrollable/Flow Row of Episode Buttons matching Image 2 perfectly
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentPadding = PaddingValues(end = 24.dp)
                                ) {
                                    items(episodesInSelectedSeason) { epChannel ->
                                        val info = viewModel.parseSeriesEpisode(epChannel)
                                        val epNum = info?.episode ?: 1
                                        val isSelected = epNum == selectedEpisodeNum
                                        
                                        var isEpFocused by remember { mutableStateOf(false) }
                                        val epBgColor = if (isEpFocused) {
                                            BloodRed
                                        } else if (isSelected) {
                                            Color.White.copy(alpha = 0.25f)
                                        } else {
                                            Color.White.copy(alpha = 0.08f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(width = 54.dp, height = 44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(epBgColor)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isEpFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.4f) else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .onFocusChanged { isEpFocused = it.isFocused }
                                                .clickable {
                                                    viewModel.selectEpisode(epNum)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Reproducir",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "$epNum",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Leave this space transparent/empty so that the absolute overlaid player shows in this exact segment
                    Spacer(modifier = Modifier.fillMaxWidth(0.415f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Lower Block: Companion suggestions "Quizás te guste" list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.7f),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "Quizás te guste",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 40.dp, bottom = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(companionMovies) { compChannel ->
                            CompanionMovieCard(
                                channel = compChannel,
                                onClick = {
                                    viewModel.selectChannelDetails(compChannel)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Overlay Player: Always the exact same Composable node at the root-level of MovieDetailsScreen
        val streamToPlay = currentChannel.primaryStreamUrl
        if (streamToPlay.isNotEmpty()) {
            FullscreenPlayer(
                viewModel = viewModel,
                isMaximized = isPlayerMaximized,
                modifier = if (isPlayerMaximized) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.405f)
                        .fillMaxHeight(0.60f)
                        .padding(top = 44.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, BloodRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                }
            )
        }
    }

    if (showLanguageOverlay) {
        AlertDialog(
            onDismissRequest = { showLanguageOverlay = false },
            title = { Text("Opciones de Configuración", color = Color.White) },
            text = {
                Column {
                    Text("Este canal cuenta con ${currentChannel.sources.size} transmisiones agregadas en tus listas M3U.", color = TextLight, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Al reproducir en pantalla completa, podrás cambiar el idioma del audio (Latino/Castellano/Inglés), cambiar de servidor o ajustar reguladores de calidad.", color = TextLight.copy(alpha = 0.6f))
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLanguageOverlay = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed)
                ) {
                    Text("Entendido", color = Color.White)
                }
            },
            containerColor = CardSlate
        )
    }
}

@Composable
fun GenreBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

@Composable
fun DetailActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) BloodRed else Color.White.copy(alpha = 0.1f))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
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
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) Color.White else TextLight
        )
    }
}

@Composable
fun CompanionMovieCard(
    channel: UiChannel,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .height(85.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) BloodRed else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(CardSlate)
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Rating / Info overlay tags on card (Screenshot 6 styled)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = channel.rating,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFBBF24)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) BloodRed else Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
