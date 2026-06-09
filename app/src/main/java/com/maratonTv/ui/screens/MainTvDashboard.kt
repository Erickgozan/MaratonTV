package com.maratonTv.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maratonTv.R
import com.maratonTv.data.model.UiChannel
import com.maratonTv.ui.viewmodel.TvViewModel
import com.maratonTv.ui.components.VideoPlayer
import com.maratonTv.ui.utils.isAndroidTv
import kotlinx.coroutines.delay

@Composable
fun MainTvDashboard(viewModel: TvViewModel) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val isTv = isAndroidTv()
    
    var showSearch by remember { mutableStateOf(false) }
    val query by viewModel.searchQuery.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (activeCategory) {
            "TV" -> TvDashboard(viewModel, isTv, onSearchClick = { showSearch = true })
            "PELICULA", "SERIES", "KIDS", "ANIME" -> MovieCatalogDashboard(viewModel, isTv, onSearchClick = { showSearch = true })
            else -> TvDashboard(viewModel, isTv, onSearchClick = { showSearch = true })
        }
        
        if (showSearch) {
            SearchOverlay(
                query = query,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onDismiss = { showSearch = false },
                isTvSection = activeCategory == "TV"
            )
        }
    }
}

@Composable
fun TvDashboard(viewModel: TvViewModel, isTv: Boolean, onSearchClick: () -> Unit) {
    val playingChannel by viewModel.playingChannel.collectAsState()
    val activeStreamUrl by viewModel.activeStreamUrl.collectAsState()
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. REPRODUCTOR (16:9 Fijo)
        Box(modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.DarkGray)
        ) {
            if (playingChannel != null) {
                VideoPlayer(
                    streamUrl = activeStreamUrl,
                    modifier = Modifier.fillMaxSize(),
                    showController = true // Habilitamos controles básicos para TV
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.blooders_app_logo_1779688823340),
                        contentDescription = "MaratonTV",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }
            
            // Lupa de búsqueda (Solo si no está maximizado para no estorbar el video)
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Search, "Buscar", tint = Color.White)
            }
        }

        // 2. LISTA DE CANALES Y CATEGORÍAS (65% de la pantalla)
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            TvCategoryTabs(viewModel)
            ChannelGrid(viewModel, filteredChannels)
        }
    }
}

@Composable
fun MovieCatalogDashboard(viewModel: TvViewModel, isTv: Boolean, onSearchClick: () -> Unit) {
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val scrapedCatalogItems by viewModel.scrapedCatalogItems.collectAsState()
    val activeCategory by viewModel.activeCategory.collectAsState()
    
    val items = if (scrapedCatalogItems.isNotEmpty()) scrapedCatalogItems else filteredChannels

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Carrusel de Estrenos
        item {
            FeaturedCarousel(items.take(5)) {
                viewModel.selectChannelDetails(it)
            }
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Explorar $activeCategory",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, "Buscar", tint = Color.White)
                }
            }
        }
        
        items(items.chunked(if (isTv) 5 else 3)) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    MovieCard(item, modifier = Modifier.weight(1f)) {
                        viewModel.selectChannelDetails(item)
                    }
                }
                repeat( (if (isTv) 5 else 3) - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun TvCategoryTabs(viewModel: TvViewModel) {
    val selectedSub by viewModel.selectedTvSubCategory.collectAsState()
    val categories = listOf("TODOS", "DEPORTES", "CINE / PELÍCULAS", "DOCUMENTALES", "TV ABIERTA", "NOTICIAS")

    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedSub).coerceAtLeast(0),
        containerColor = Color.Transparent,
        contentColor = Color(0xFF00E5FF),
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[categories.indexOf(selectedSub).coerceAtLeast(0)]),
                color = Color(0xFF00E5FF)
            )
        }
    ) {
        categories.forEach { category ->
            Tab(
                selected = selectedSub == category,
                onClick = { viewModel.selectTvSubCategory(category) },
                text = { Text(category, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = Color(0xFF00E5FF),
                unselectedContentColor = Color.Gray
            )
        }
    }
}

@Composable
fun CategoryTabs(viewModel: TvViewModel) {
    val activeCategory by viewModel.activeCategory.collectAsState()
    val categories = listOf("TV", "FAVORITOS", "PELICULA", "SERIES", "KIDS", "ANIME", "HISTORIAL")

    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(activeCategory).coerceAtLeast(0),
        containerColor = Color.Transparent,
        contentColor = Color(0xFF00E5FF),
        edgePadding = 16.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[categories.indexOf(activeCategory).coerceAtLeast(0)]),
                color = Color(0xFF00E5FF)
            )
        }
    ) {
        categories.forEach { category ->
            Tab(
                selected = activeCategory == category,
                onClick = { viewModel.selectCategory(category) },
                text = { Text(category, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                selectedContentColor = Color(0xFF00E5FF),
                unselectedContentColor = Color.Gray
            )
        }
    }
}

@Composable
fun ChannelGrid(viewModel: TvViewModel, channels: List<UiChannel>) {
    val isTv = isAndroidTv()
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTv) 6 else 3),
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(channels) { channel ->
            ChannelCard(
                channel = channel,
                onToggleFavorite = { viewModel.toggleFavorite(channel) },
                onClick = { viewModel.playChannel(channel) }
            )
        }
    }
}

@Composable
fun ChannelCard(channel: UiChannel, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Contenedor raíz para separar el clic de la tarjeta del clic del corazón
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        // 1. Capa Base: La tarjeta interactiva
        Card(
            onClick = onClick, // Usamos el onClick nativo de Material3 para mejor respuesta
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
            border = BorderStroke(
                width = if (isFocused) 2.dp else 0.5.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.1f)
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Fondo de respaldo
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.05f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Imagen
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Franja del Título (cortada antes del botón para no interferir)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 36.dp) // Espacio sagrado para el botón
                    )
                }
            }
        }

        // 2. Capa Superior: El Botón de Favorito (FUERA de la tarjeta para ser intocable)
        // Se coloca al final para garantizar que esté por encima de TODO
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(36.dp) // Área táctil generosa
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (channel.isFavorite) Color.Red else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MovieCard(channel: UiChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF39FF14) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            if (channel.rating.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("★ ${channel.rating}", color = Color(0xFFFBBF24), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FeaturedCarousel(items: List<UiChannel>, onItemSelected: (UiChannel) -> Unit) {
    if (items.isEmpty()) return
    
    var currentIndex by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    val item = items[currentIndex]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onItemSelected(item) }
    ) {
        AsyncImage(
            model = item.logoUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(item.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(item.synopsis, color = Color.LightGray, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    isTvSection: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val placeholder = if (isTvSection) "Buscar canales de TV..." else "Buscar películas o series..."
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(if (isAndroidTv()) 0.6f else 0.9f),
                placeholder = { Text(placeholder, color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.Gray
                ),
                trailingIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                },
                singleLine = true
            )
        }
    }
}
