package com.maratonTv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maratonTv.ui.utils.isAndroidTv
import com.maratonTv.ui.viewmodel.TvViewModel

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val category: String? = null
)

@Composable
fun getNavItems(): List<NavItem> {
    return listOf(
        NavItem("MAIN", "TV", Icons.Default.Tv, "TV"),
        NavItem("FAVORITES", "FAVORITOS", Icons.Default.Favorite, "FAVORITOS"),
        NavItem("MOVIES", "PELÍCULA", Icons.Default.Movie, "PELICULA"),
        NavItem("SERIES", "SERIES", Icons.Default.VideoLibrary, "SERIES"),
        NavItem("KIDS", "KIDS", Icons.Default.ChildCare, "KIDS"),
        NavItem("ANIME", "ANIME", Icons.Default.Animation, "ANIME"),
        NavItem("HISTORY", "HISTORIAL", Icons.Default.History, "HISTORIAL"),
        NavItem("SETTINGS", "AJUSTES", Icons.Default.Settings)
    )
}

@Composable
fun AppNavigationWrapper(
    viewModel: TvViewModel,
    content: @Composable (PaddingValues) -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeCategory by viewModel.activeCategory.collectAsState()
    val isTv = isAndroidTv()
    val isMaximized by viewModel.isPlayerMaximized.collectAsState()

    if (isMaximized) {
        content(PaddingValues(0.dp))
        return
    }

    if (isTv) {
        Row(Modifier.fillMaxSize().background(Color.Black)) {
            TvSideMenu(
                selectedScreen = currentScreen,
                activeCategory = activeCategory,
                onItemClick = { item ->
                    if (item.category != null) {
                        viewModel.selectCategory(item.category)
                        viewModel.setScreen("MAIN")
                    } else {
                        viewModel.setScreen(item.id)
                    }
                }
            )
            Box(Modifier.fillMaxSize()) {
                content(PaddingValues(0.dp))
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                MobileBottomNav(
                    selectedScreen = currentScreen,
                    activeCategory = activeCategory,
                    onItemClick = { item ->
                        if (item.category != null) {
                            viewModel.selectCategory(item.category)
                            viewModel.setScreen("MAIN")
                        } else {
                            viewModel.setScreen(item.id)
                        }
                    }
                )
            },
            containerColor = Color.Black
        ) { padding ->
            content(padding)
        }
    }
}

@Composable
fun MobileBottomNav(
    selectedScreen: String,
    activeCategory: String,
    onItemClick: (NavItem) -> Unit
) {
    val navItems = getNavItems()
    // Mostramos las secciones principales solicitadas por el usuario
    val mainItems = listOf(
        navItems.find { it.id == "MAIN" }!!,
        navItems.find { it.id == "MOVIES" }!!,
        navItems.find { it.id == "SERIES" }!!,
        navItems.find { it.id == "KIDS" }!!,
        navItems.find { it.id == "FAVORITES" }!!,
        navItems.find { it.id == "SETTINGS" }!!
    )
    
    NavigationBar(
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        tonalElevation = 8.dp
    ) {
        mainItems.forEach { item ->
            val isSelected = if (item.category != null) {
                selectedScreen == "MAIN" && activeCategory == item.category
            } else {
                selectedScreen == item.id
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemClick(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF00E5FF),
                    selectedTextColor = Color(0xFF00E5FF),
                    indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}

@Composable
fun TvSideMenu(
    selectedScreen: String,
    activeCategory: String,
    onItemClick: (NavItem) -> Unit
) {
    val navItems = getNavItems()
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(Color(0xFF121212))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        navItems.forEach { item ->
            val isSelected = if (item.category != null) {
                selectedScreen == "MAIN" && activeCategory == item.category
            } else {
                selectedScreen == item.id
            }

            var isFocused by remember { mutableStateOf(false) }
            val backgroundColor = when {
                isFocused -> Color(0xFF39FF14).copy(alpha = 0.2f)
                isSelected -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                else -> Color.Transparent
            }
            val contentColor = when {
                isFocused -> Color(0xFF39FF14)
                isSelected -> Color(0xFF00E5FF)
                else -> Color.Gray
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onItemClick(item) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, contentDescription = item.label, tint = contentColor, modifier = Modifier.size(24.dp))
                    Text(item.label, color = contentColor, fontSize = 8.sp)
                }
            }
        }
    }
}
