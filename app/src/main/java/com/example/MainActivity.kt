package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBox
import com.example.data.AppDatabase
import com.example.data.TvRepository
import com.example.ui.TvViewModel
import com.example.R
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.components.VideoPlayer
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
        
        // Initialize ViewModel using proper ViewModelProvider storing the instance through recreation
        viewModel = ViewModelProvider(
            this,
            TvViewModel.provideFactory(
                application = this.application,
                repository = repository
            )
        )[TvViewModel::class.java]

        com.example.data.PelisPlusScraper.appContext = this.applicationContext

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

                    androidx.compose.runtime.LaunchedEffect(playbackMsg) {
                        playbackMsg?.let { msg ->
                            android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                            viewModel.clearPlaybackMessage()
                        }
                    }

                    val isPlayerMaximized by viewModel.isPlayerMaximized.collectAsState()
                    var showTelegramBanner by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                    var lastBackPressTime by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }

                    // Intercept Back Button / Back Gesture to navigate backwards in the UI instead of exiting the app
                    androidx.activity.compose.BackHandler(enabled = true) {
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
                                            android.widget.Toast.makeText(this@MainActivity, "Presiona atrás de nuevo para salir", android.widget.Toast.LENGTH_SHORT).show()
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

@androidx.compose.runtime.Composable
fun YoutubeTrailerPlayer(
    trailerId: String,
    onClose: () -> Unit
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { _ ->
                onClose()
                true
            }
            .clickable {
                onClose()
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val webView = android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    webViewClient = android.webkit.WebViewClient()
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                android.webkit.CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView, true)
                }
                webView
            },
            update = { webView ->
                val currentTag = webView.tag as? String
                if (currentTag != trailerId) {
                    webView.tag = trailerId
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #000; }
                                iframe { width: 100%; height: 100%; border: none; }
                            </style>
                        </head>
                        <body>
                            <iframe id="player" src="https://www.youtube.com/embed/$trailerId?autoplay=1&mute=0&controls=1&playsinline=1&enablejsapi=1&origin=https://www.youtube.com" allow="autoplay; encrypted-media" allowfullscreen></iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    webView.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable { onClose() }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "CERRAR TRÁILER: PRESIONE CUALQUIER BOTÓN O CLIC EN PANTALLA",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun TelegramBannerPromo(
    onDismiss: () -> Unit
) {
    var secondsLeft by androidx.compose.runtime.remember { mutableIntStateOf(5) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            secondsLeft--
        }
        onDismiss()
    }

    // Capture keys to dismiss banner early when using DPAD controls
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else false
            }
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val calculatedSize = if (this.maxWidth < this.maxHeight) this.maxWidth * 0.85f else this.maxHeight * 0.85f

        // Main container card matching the 1:1 format of the generated asset
        Box(
            modifier = Modifier
                .size(calculatedSize)
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, Color(0xFF38BDF8), RoundedCornerShape(24.dp))
                .background(Color(0xFF0F172A))
                .clickable {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            "https://t.me/BloodersTv".toUri()
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback
                    }
                }
        ) {
            // Render the authentic high-resolution Telegram promotional image
            Image(
                painter = painterResource(id = R.drawable.img_telegram_banner_1780320162432),
                contentDescription = "¡Únete a Telegram BloodersTV!",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Overlaid Countdown Bubble
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(50))
                    .clickable { 
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Cerrar (${secondsLeft}s) ✕",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Click instructions overlay bar at the bottom center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "CLIC PARA UNIRSE / PRESIONE CUALQUIER BOTÓN PARA CERRAR",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}
