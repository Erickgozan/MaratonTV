package com.maratonTv

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.maratonTv.data.local.AppDatabase
import com.maratonTv.data.repository.*
import com.maratonTv.ui.viewmodel.TvViewModel
import com.maratonTv.data.remote.scrapers.PelisPlusScraper
import androidx.lifecycle.ViewModelProvider
import com.maratonTv.ui.screens.*
import com.maratonTv.ui.theme.MyApplicationTheme
import com.maratonTv.ui.components.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TvViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure Coil with customized persistent disk & memory cache, ignoring short-lived HTTP expire rules to reuse assets extensively
        val imageLoader = coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        // Force landscape orientation regardless of the device with a fallback safety check
        try {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Initialize Room Database
        val database = AppDatabase.getDatabase(this)
        val tvDao = database.tvDao()
        
        // Initialize Repository
        val repository = TvRepository(tvDao, this)
        val metadataRepository = MediaMetadataRepository(repository, repository.okHttpClient)
        val scraperRepository = ScraperRepository()
        
        // Initialize ViewModel using proper ViewModelProvider storing the instance through recreation
        viewModel = ViewModelProvider(
            this,
            TvViewModel.provideFactory(
                application = this.application,
                repository = repository,
                metadataRepository = metadataRepository,
                scraperRepository = scraperRepository
            )
        )[TvViewModel::class.java]

        PelisPlusScraper.appContext = this.applicationContext

        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val playbackMsg by viewModel.playbackMessage.collectAsState()
                    val trailerUrl by viewModel.activeTrailerUrl.collectAsState()

                    LaunchedEffect(playbackMsg) {
                        playbackMsg?.let { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            viewModel.clearPlaybackMessage()
                        }
                    }

                    val isPlayerMaximized by viewModel.isPlayerMaximized.collectAsState()
                    var showTelegramBanner by remember { mutableStateOf(true) }
                    var lastBackPressTime by remember { mutableLongStateOf(0L) }

                    // Intercept Back Button / Back Gesture to navigate backwards in the UI instead of exiting the app
                    BackHandler(enabled = true) {
                        if (showTelegramBanner) {
                            showTelegramBanner = false
                        } else if (trailerUrl != null) {
                            viewModel.closeTrailer()
                        } else if (isPlayerMaximized) {
                            viewModel.setPlayerMaximized(false)
                        } else {
                            val wentBack = viewModel.goBack()
                            if (!wentBack) {
                                when (currentScreen) {
                                    "SETTINGS" -> viewModel.setScreen("MAIN")
                                    "PLAYER" -> {
                                        viewModel.stopPlayback()
                                        viewModel.setScreen("MAIN")
                                    }
                                    "DETAILS" -> {
                                        viewModel.selectChannelDetails(null)
                                    }
                                    "MAIN" -> viewModel.setScreen("PROFILES")
                                    else -> {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastBackPressTime < 2000) {
                                            this@MainActivity.finish()
                                        } else {
                                            lastBackPressTime = currentTime
                                            Toast.makeText(this@MainActivity, "Presiona atrás de nuevo para salir", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            "PROFILES" -> ProfilesScreen(viewModel = viewModel)
                            "MAIN" -> MainTvDashboard(viewModel = viewModel)
                            "DETAILS" -> MovieDetailsScreen(viewModel = viewModel)
                            "PLAYER" -> FullscreenPlayer(viewModel = viewModel)
                            "SETTINGS" -> SettingsScreen(viewModel = viewModel)
                            else -> ProfilesScreen(viewModel = viewModel)
                        }

                        if (showTelegramBanner) {
                            TelegramBannerPromo(onDismiss = { showTelegramBanner = false })
                        }

                        val isImdbTrailerActive by viewModel.isImdbTrailerActive.collectAsState()
                        trailerUrl?.let { url ->
                            if (isImdbTrailerActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                        .clickable { viewModel.closeTrailer() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    VideoPlayer(
                                        streamUrl = url,
                                        modifier = Modifier.fillMaxSize(),
                                        showController = false
                                    )
                                    Button(
                                        onClick = { viewModel.closeTrailer() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.6f)),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                                    ) {
                                        Text("Cerrar Tráiler")
                                    }
                                }
                            } else {
                                YoutubeTrailerPlayer(
                                    trailerId = url,
                                    onClose = { viewModel.closeTrailer() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
