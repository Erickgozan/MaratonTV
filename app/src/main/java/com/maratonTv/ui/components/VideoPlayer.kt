package com.maratonTv.ui.components

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier
import java.security.cert.X509Certificate

private var sslCheckingDisabled = false

private val okHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

private fun disableSSLCertificateChecking() {
    if (sslCheckingDisabled) return
    try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        
        val allHostsValid = HostnameVerifier { _, _ -> true }
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
        sslCheckingDisabled = true
        Log.d("VideoPlayer", "SSL Certificate Verification bypassed globally for maximum IPTV compatibility.")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getMimeTypeOptions(streamUrl: String): List<String?> {
    val options = mutableListOf<String?>()
    val urlLower = streamUrl.lowercase(java.util.Locale.ROOT)
    
    if (urlLower.contains(".m3u8") || urlLower.contains("/hls/") || urlLower.contains("type=m3u8")) {
        options.add(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP2T)
        options.add(null)
    } else if (urlLower.contains(".ts") || urlLower.contains("output=ts") || urlLower.contains("format=ts") || urlLower.contains("output=mpegts")) {
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP2T)
        options.add(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        options.add(null)
    } else if (urlLower.contains("get.php")) {
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP2T)
        options.add(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        options.add(null)
    } else if (urlLower.contains(".mp4") || urlLower.contains(".m4v") || urlLower.contains(".h264") || urlLower.contains(".h265")) {
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP4)
        options.add(null)
    } else if (urlLower.contains(".mkv") || urlLower.contains(".webm") || urlLower.contains(".avi") || urlLower.contains(".mov") || urlLower.contains(".flv")) {
        options.add(androidx.media3.common.MimeTypes.VIDEO_WEBM)
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP4)
        options.add(null)
    } else if (urlLower.contains(".mp3") || urlLower.contains(".mp2") || urlLower.contains(".mpa") || urlLower.contains(".mpeg") || urlLower.contains(".mpega")) {
        options.add(androidx.media3.common.MimeTypes.AUDIO_MPEG)
        options.add(null)
    } else if (urlLower.contains(".aac")) {
        options.add(androidx.media3.common.MimeTypes.AUDIO_AAC)
        options.add(null)
    } else {
        options.add(null)
        options.add(androidx.media3.common.MimeTypes.VIDEO_MP2T)
        options.add(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
    }
    
    return options.distinct()
}

fun isEmbedUrl(url: String): Boolean {
    val low = url.lowercase()
    return low.startsWith("<iframe") || low.startsWith("<div") ||
            low.contains("<iframe") || low.contains("iframe>") ||
            low.contains("youtube.com/embed/") || low.contains("youtu.be/embed/") ||
            low.contains("vimeo.com/video/") || low.contains("twitch.tv/embed") ||
            low.contains("dailymotion.com/embed/") || low.contains(".html") ||
            low.contains("/embed") || low.contains("facebook.com/plugins/video.php") ||
            low.contains("youtube.com/watch?v=") || low.contains("youtu.be/") ||
            low.contains("trembed") || low.contains("seriesmetro") ||
            low.contains("fembed") || low.contains("upstream") ||
            low.contains("dood") || low.contains("mixdrop") ||
            low.contains("streamtape") || low.contains("voe") ||
            low.contains("vidguard") || low.contains("filemoon") ||
            low.contains("streamwish") || low.contains("streamptape") ||
            low.contains("fastream") || low.contains("vidhide") ||
            low.contains("delta") ||
            low.contains("pelispedia") || low.contains("pelisplus") ||
            low.contains("rapidvideo") || low.contains("waaw") ||
            low.contains("vidoza") || low.contains("uqload")
}

@androidx.annotation.Keep
class WebAppPlayerBridge(
    private val onProgress: (Long, Long, Boolean) -> Unit
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun updateProgress(currentTime: Double, duration: Double, isPlaying: Boolean) {
        val currMs = (currentTime * 1000).toLong()
        val durMs = (duration * 1000).toLong()
        handler.post {
            onProgress(currMs, durMs, isPlaying)
        }
    }
}

private fun injectVideoControllerScript(view: android.webkit.WebView?) {
    view?.evaluateJavascript(
        """
        (function() {
            window.findBloodersVideo = window.findBloodersVideo || function() {
                function scan(win) {
                    try {
                        var v = win.document.querySelector('video');
                        if (v) return v;
                    } catch(e) {}
                    try {
                        var frames = win.document.querySelectorAll('iframe');
                        for (var i = 0; i < frames.length; i++) {
                            var r = scan(frames[i].contentWindow);
                            if (r) return r;
                        }
                    } catch(e) {}
                    return null;
                }
                return scan(window);
            };

            function pollVideo() {
                try {
                    var v = window.findBloodersVideo();
                    if (v) {
                        if (!v.paused) {
                            window.bloodersAutoplayed = true;
                        }
                    }

                    if (!window.bloodersAutoplayed) {
                        if (v && v.paused) {
                            try { v.play(); } catch(e) {}
                        }

                        // Automaticaly bypass/trigger EMBED69 play button overlay clicks
                        try {
                            if (typeof window.showPlayerInterface === 'function') {
                                var fakePlayer = document.getElementById('fakePlayer');
                                if (fakePlayer && fakePlayer.style.display !== 'none') {
                                    console.log('[VideoPlayer] Auto-calling showPlayerInterface()');
                                    window.showPlayerInterface();
                                }
                            } else {
                                var playOverlay = document.querySelector('.play-button-overlay');
                                if (playOverlay && playOverlay.style.display !== 'none') {
                                    console.log('[VideoPlayer] Auto-clicking .play-button-overlay');
                                    playOverlay.click();
                                }
                            }
                        } catch(e) {
                            console.error('[VideoPlayer] Error clicking play button overlay:', e);
                        }

                        // General play selectors clicker for standard web players
                        try {
                            var playSelectors = [
                                '.vjs-big-play-button',
                                '.jw-display-icon-container',
                                '.jw-icon-display',
                                '.plyr__control--overlaid',
                                '#play_limit_box',
                                '.autoplay-btn',
                                '.play-button-overlay',
                                '[aria-label="Play"]',
                                'svg[viewBox="0 0 200 200"]',
                                '.jw-state-idle',
                                '[class*="play-button"]',
                                '[class*="play_button"]',
                                '[id*="play"]'
                            ];
                            for (var i = 0; i < playSelectors.length; i++) {
                                var btn = document.querySelector(playSelectors[i]);
                                if (btn && btn.offsetHeight > 0 && btn.offsetWidth > 0) {
                                    if (!v || v.paused) {
                                        console.log('[VideoPlayer] Auto-clicking play button: ' + playSelectors[i]);
                                        btn.click();
                                    }
                                }
                            }
                        } catch (e) {}
                    }

                    if (v) {
                        var c = v.currentTime;
                        var d = v.duration;
                        if (isNaN(c) || !isFinite(c)) c = 0;
                        if (isNaN(d) || !isFinite(d)) d = 0;
                        AndroidPlayerBridge.updateProgress(c, d, !v.paused);
                    }
                } catch(e) {
                    console.error('[VideoPlayer] Error polling video:', e);
                }
            }
            if (!window.videoPollIntervalId) {
                window.videoPollIntervalId = setInterval(pollVideo, 1000);
            }

            // Setup general postMessage listener in parent frame to handle cross-origin subframes forwarding
            if (!window.bloodersMsgListenerAdded) {
                window.addEventListener('message', function(event) {
                    try {
                        var data = event.data;
                        if (data && data.type === 'progress') {
                            if (window.AndroidPlayerBridge) {
                                window.AndroidPlayerBridge.updateProgress(data.currentTime, data.duration, data.isPlaying);
                            }
                        }
                    } catch(e) {}
                });
                window.bloodersMsgListenerAdded = true;
            }
        })();
        """.trimIndent(),
        null
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    showController: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    aspectRatio: Float? = null,
    onPlayerError: ((String) -> Unit)? = null,
    // Reactive parameters for multi-tier live stream audio fixes
    libVlcInstalled: Boolean = false,
    codecsPackInstalled: Boolean = false,
    videoLanguage: String = "Español Latino",
    videoSubtitles: String = "Desactivados",
    audioFixIgnoreErrors: Boolean = false,
    audioFixChannels: String = "Stereo",
    initialPosition: Long = 0L,
    onPositionUpdated: ((currentPosition: Long, duration: Long) -> Unit)? = null,
    onPlayerCreated: ((androidx.media3.exoplayer.ExoPlayer) -> Unit)? = null,
    isEmbedText: Boolean = false,
    adBlockerEnabled: Boolean = false,
    onProgressUpdated: ((Long, Long, Boolean) -> Unit)? = null,
    onWebViewCreated: ((android.webkit.WebView) -> Unit)? = null
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isEmbed = remember(streamUrl, isEmbedText) { isEmbedText || isEmbedUrl(streamUrl) }
    val effectiveAdBlocker = remember(adBlockerEnabled, isEmbed) { adBlockerEnabled || isEmbed }

    var currentMimeTypeIndex by remember(streamUrl) { mutableStateOf(0) }
    val mimeTypeOptions = remember(streamUrl) { getMimeTypeOptions(streamUrl) }

    // Optimized streaming buffer configuration to fast-start loading
    val loadControl = remember(libVlcInstalled) {
        if (libVlcInstalled) {
            // High-performance VLC-style buffering parameters (instant start, minimal delay)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2500,  // minBufferMs (extremely fast, VLC-like instant playback initiation)
                    15000, // maxBufferMs
                    800,   // bufferForPlaybackMs
                    1500   // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder().build()
        }
    }

    // Configure Custom renderers to prefer software fallback decoders for advanced video/audio formats (AC3, DTS, MPEG4, etc.)
    val renderersFactory = remember(codecsPackInstalled, audioFixChannels) {
        MpegL2MappingRenderersFactory(context, audioFixChannels).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
            // Custom MediaCodecSelector to handle and map legacy/unsupported audio codecs like MP2/MPEG1 Audio Layer II
            val customCodecSelector = object : androidx.media3.exoplayer.mediacodec.MediaCodecSelector {
                override fun getDecoderInfos(
                    mimeType: String,
                    requiresSecure: Boolean,
                    requiresTunneling: Boolean
                ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                    val isMp2OrMpegL2 = mimeType.equals("audio/mpeg-L2", ignoreCase = true) ||
                        mimeType.equals("audio/mpeg-l2", ignoreCase = true) ||
                        mimeType.equals("audio/mpeg-L1", ignoreCase = true) ||
                        mimeType.equals("audio/mpeg-l1", ignoreCase = true) ||
                        mimeType.equals("audio/mp2", ignoreCase = true) ||
                        mimeType.equals("audio/mpa", ignoreCase = true) ||
                        mimeType.equals("audio/x-mpeg", ignoreCase = true) ||
                        mimeType.equals("audio/x-mpa", ignoreCase = true) ||
                        mimeType.equals("audio/x-mp2", ignoreCase = true)

                    val isMpegAudio = isMp2OrMpegL2 ||
                        mimeType.equals("audio/mpga", ignoreCase = true) ||
                        mimeType.equals("audio/mpeg", ignoreCase = true)

                    // Always bypass hardware decoders for actual MP2/MPEG-L2 audio formats so that
                    // they automatically fallback to MpegL2SoftwareAudioRenderer (JLayer software decoder).
                    // Also bypass hardware for standard MPEG/MP3 format if codecsPackInstalled flag is enabled.
                    if (isMp2OrMpegL2 || (codecsPackInstalled && isMpegAudio)) {
                        Log.d("VideoPlayer", "Bypassing hardware decoders for audio format: $mimeType. Software decoder (JLayer) will be used.")
                        return emptyList()
                    }

                    val targetMimeType = if (isMp2OrMpegL2) "audio/mpeg" else mimeType
                    
                    val list = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                        targetMimeType,
                        requiresSecure,
                        requiresTunneling
                    )
                    return list
                }
            }
            setMediaCodecSelector(customCodecSelector)
        }
    }

    // Reinicio Limpio Absoluto al cambiar de URL (Reinicializa motor multimedia desde cero)
    val exoPlayer = remember(streamUrl, codecsPackInstalled, libVlcInstalled, audioFixChannels) {
        disableSSLCertificateChecking()

        val baseHttpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, baseHttpDataSourceFactory)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setTsExtractorFlags(25)
        }

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    // Handle overall ExoPlayer lifecycle disposal (Apagado en cadena estricto)
    DisposableEffect(exoPlayer) {
        onPlayerCreated?.invoke(exoPlayer)
        onDispose {
            Log.d("VideoPlayer", "Ejecutando Destrucción Absoluta del Reproductor...")
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Periodic position update loop
    LaunchedEffect(exoPlayer, streamUrl) {
        if (onPositionUpdated != null) {
            while (true) {
                try {
                    if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY) {
                        onPositionUpdated(exoPlayer.currentPosition, exoPlayer.duration)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val initialSanitizedUrl = remember(streamUrl) {
        var s = streamUrl.trim()
        if (s.startsWith("https://", ignoreCase = true)) {
            if (s.contains(":80/") || s.contains(":8080/") || s.contains(":80?") || s.contains(":8080?")) {
                s = s.replace("https://", "http://", ignoreCase = true)
            }
        }
        s
    }

    var activePlayUrl by remember(initialSanitizedUrl) { mutableStateOf(initialSanitizedUrl) }
    var seekedToInitial by remember(initialSanitizedUrl) { mutableStateOf(false) }

    // Adaptive preparation, retry, and play event lifecycle
    LaunchedEffect(activePlayUrl, currentMimeTypeIndex, videoLanguage, videoSubtitles) {
        if (isEmbed) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        
        // Apply user's language and subtitles preferences
        val preferredAudioLang = when {
            videoLanguage.contains("Español", ignoreCase = true) -> "spa"
            videoLanguage.contains("Inglés", ignoreCase = true) -> "eng"
            videoLanguage.contains("Portugués", ignoreCase = true) -> "por"
            videoLanguage.contains("Francés", ignoreCase = true) -> "fra"
            else -> "spa"
        }

        val preferredSubTitleLang = when {
            videoSubtitles.contains("Español", ignoreCase = true) -> "spa"
            videoSubtitles.contains("Inglés", ignoreCase = true) -> "eng"
            videoSubtitles.contains("Portugués", ignoreCase = true) -> "por"
            videoSubtitles.contains("Francés", ignoreCase = true) -> "fra"
            else -> null
        }

        val disableSubtitles = videoSubtitles.equals("Desactivados", ignoreCase = true)

        try {
            var trackParamsBuilder = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                .setPreferredAudioLanguage(preferredAudioLang)
            
            if (audioFixChannels.contains("Mono", ignoreCase = true)) {
                trackParamsBuilder = trackParamsBuilder.setMaxAudioChannelCount(1)
            } else if (audioFixChannels.contains("Estéreo", ignoreCase = true) || audioFixChannels.contains("Stereo", ignoreCase = true)) {
                trackParamsBuilder = trackParamsBuilder.setMaxAudioChannelCount(2)
            }

            if (disableSubtitles) {
                trackParamsBuilder = trackParamsBuilder
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            } else {
                trackParamsBuilder = trackParamsBuilder
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                if (preferredSubTitleLang != null) {
                    trackParamsBuilder = trackParamsBuilder.setPreferredTextLanguage(preferredSubTitleLang)
                }
            }
            exoPlayer.trackSelectionParameters = trackParamsBuilder.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (group in tracks.groups) {
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val mimeType = format.sampleMimeType
                            if (mimeType != null && (
                                mimeType.contains("mpeg-L2", ignoreCase = true) ||
                                mimeType.contains("mpeg-l2", ignoreCase = true) ||
                                mimeType.contains("mp2", ignoreCase = true) ||
                                mimeType.contains("mpa", ignoreCase = true)
                            )) {
                                Log.i("VideoPlayer", "onTracksChanged: Detected MP2/MPEG-L2 audio format ($mimeType) stream. Delegating to custom mapped audio/mpeg renderer.")
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                Log.e("VideoPlayer", "Error using format index $currentMimeTypeIndex (${mimeTypeOptions.getOrNull(currentMimeTypeIndex)}): ${error.localizedMessage}")
                
                val message = (error.localizedMessage ?: error.message ?: "").lowercase()
                val isSslOrTlsError = message.contains("ssl") || 
                    message.contains("tls") || 
                    message.contains("handshake") || 
                    message.contains("certignore") || 
                    message.contains("certificate") ||
                    message.contains("tls packet header") ||
                    (error.cause?.message?.lowercase()?.contains("tls") == true) ||
                    (error.cause?.message?.lowercase()?.contains("ssl") == true)

                if (isSslOrTlsError && activePlayUrl.startsWith("https://", ignoreCase = true)) {
                    Log.i("VideoPlayer", "SSL/TLS playback error detected. Falling back from HTTPS to HTTP for: $activePlayUrl")
                    activePlayUrl = activePlayUrl.replace("https://", "http://", ignoreCase = true)
                    return
                }

                val isAudioError = message.contains("audio", ignoreCase = true) ||
                    message.contains("mediacodecaudiorenderer", ignoreCase = true) ||
                    message.contains("audiosink", ignoreCase = true) ||
                    message.contains("audiotrack", ignoreCase = true) ||
                    (error.cause?.message?.lowercase()?.contains("audio") == true)
                
                if (isAudioError) {
                    if (audioFixIgnoreErrors) {
                        Log.w("VideoPlayer", "onPlayerError: Caught audio renderer error. [Parche Omitir Desactivar] Re-intentando preparación del canal con audio intacto.")
                        try {
                            exoPlayer.prepare()
                            exoPlayer.play()
                            return
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        Log.w("VideoPlayer", "onPlayerError: Caught audio renderer error. Disabling audio track and retrying video-only playback immediately.")
                        try {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                                .build()
                            exoPlayer.prepare()
                            exoPlayer.play()
                            return
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (currentMimeTypeIndex < mimeTypeOptions.size - 1) {
                    currentMimeTypeIndex++
                } else {
                    errorMessage = "Error de reproducción: ${error.localizedMessage}"
                    onPlayerError?.invoke(error.localizedMessage ?: "Unknown error")
                }
            }
        }
        exoPlayer.addListener(listener)

        try {
            if (activePlayUrl.isNotEmpty()) {
                val currentMimeType = mimeTypeOptions.getOrNull(currentMimeTypeIndex)
                val mediaItemBuilder = MediaItem.Builder().setUri(activePlayUrl)
                if (currentMimeType != null) {
                    mediaItemBuilder.setMimeType(currentMimeType)
                }
                
                val mediaItem = mediaItemBuilder.build()
                exoPlayer.setMediaItem(mediaItem)
                if (initialPosition > 0L && !seekedToInitial) {
                    exoPlayer.seekTo(initialPosition)
                    seekedToInitial = true
                }
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                exoPlayer.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (currentMimeTypeIndex < mimeTypeOptions.size - 1) {
                currentMimeTypeIndex++
            } else {
                errorMessage = e.localizedMessage ?: "Unknown error"
            }
        }

        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            exoPlayer.removeListener(listener)
        }
    }

    val containerModifier = if (aspectRatio != null) {
        modifier.aspectRatio(aspectRatio).background(Color.Black)
    } else {
        modifier.background(Color.Black)
    }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        if (isEmbed) {
            val cleanUrl = remember(streamUrl) {
                if (streamUrl.contains("youtube.com/watch?v=")) {
                    val videoId = streamUrl.substringAfter("v=").substringBefore("&")
                    "https://www.youtube.com/embed/$videoId?autoplay=1"
                } else if (streamUrl.contains("youtu.be/")) {
                    val videoId = streamUrl.substringAfter("youtu.be/").substringBefore("?")
                    "https://www.youtube.com/embed/$videoId?autoplay=1"
                } else {
                    streamUrl
                }
            }

            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        tag = cleanUrl
                        
                        addJavascriptInterface(
                            WebAppPlayerBridge { curr, dur, playing ->
                                onProgressUpdated?.invoke(curr, dur, playing)
                            },
                            "AndroidPlayerBridge"
                        )
                        onWebViewCreated?.invoke(this)

                        // Enable cookies
                        val cookieManager = android.webkit.CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        try {
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                        } catch (e: Exception) {}

                        settings.apply {
                            javaScriptEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                            allowContentAccess = true
                            databaseEnabled = true
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                        }
                        
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView,
                                request: android.webkit.WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                if (effectiveAdBlocker) {
                                    val requestUrl = request.url.toString()
                                    if (isKnownAdProvider(requestUrl)) {
                                        android.util.Log.d("AdBlocker", "Blocked request: $requestUrl")
                                        return android.webkit.WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            java.io.ByteArrayInputStream("".toByteArray())
                                        )
                                    }
                                }

                                val urlStr = request.url.toString()
                                val acceptHeader = request.requestHeaders["Accept"] ?: ""
                                val isDocRequest = request.isForMainFrame || acceptHeader.contains("html", ignoreCase = true)
                                val lowUrl = urlStr.lowercase()
                                val isMediaOrStatic = lowUrl.contains(".m3u8") || lowUrl.contains(".mp4") || 
                                        lowUrl.contains(".ts") || lowUrl.contains(".mpd") || 
                                        lowUrl.contains(".mkv") || lowUrl.contains(".png") ||
                                        lowUrl.contains(".jpg") || lowUrl.contains(".jpeg") ||
                                        lowUrl.contains(".gif") || lowUrl.contains(".svg") ||
                                        lowUrl.contains(".js") || lowUrl.contains(".css") ||
                                        lowUrl.contains("/hls/") || lowUrl.contains("/segments/")
                                if (false) { // Disabled OkHttp document interception to avoid session/cookie desync & Error 232011
                                    try {
                                        val headersBuilder = okhttp3.Headers.Builder()
                                        request.requestHeaders.forEach { (key, value) ->
                                            headersBuilder.add(key, value)
                                        }

                                        if (request.requestHeaders["User-Agent"] == null) {
                                            headersBuilder.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                        }

                                        val cookieHeader = android.webkit.CookieManager.getInstance().getCookie(urlStr)
                                        if (!cookieHeader.isNullOrEmpty()) {
                                            headersBuilder.add("Cookie", cookieHeader)
                                        }

                                        val okRequest = okhttp3.Request.Builder()
                                            .url(urlStr)
                                            .headers(headersBuilder.build())
                                            .build()

                                        val response = okHttpClient.newCall(okRequest).execute()
                                        if (response.isSuccessful) {
                                            val contentType = response.body?.contentType()
                                            val mimeType = contentType?.toString() ?: ""
                                            if (mimeType.contains("text/html", ignoreCase = true)) {
                                                val encoding = contentType?.charset()?.name() ?: "UTF-8"
                                                var html = response.body?.string() ?: ""

                                                val setCookies = response.headers("Set-Cookie")
                                                for (cookie in setCookies) {
                                                    android.webkit.CookieManager.getInstance().setCookie(urlStr, cookie)
                                                }

                                                // Injected script that queries progress and binds video state events immediately
                                                val scriptToInject = """
                                                    <script>
                                                    (function() {
                                                        console.log("[BloodersTV] Frame successfully hijacked: " + window.location.href);

                                                        function reportStatus(v) {
                                                            var c = v.currentTime;
                                                            var d = v.duration;
                                                            if (isNaN(c) || !isFinite(c)) c = 0;
                                                            if (isNaN(d) || !isFinite(d)) d = 0;
                                                            
                                                            if (window.AndroidPlayerBridge) {
                                                                window.AndroidPlayerBridge.updateProgress(c, d, !v.paused);
                                                            }
                                                            if (window.parent && window.parent !== window) {
                                                                window.parent.postMessage({ type: 'progress', currentTime: c, duration: d, isPlaying: !v.paused }, '*');
                                                            }
                                                        }

                                                        function setupListenersForVideo(v) {
                                                            if (v && !v.bloodersBound) {
                                                                v.bloodersBound = true;
                                                                ['play', 'playing', 'pause', 'timeupdate', 'durationchange', 'seeked'].forEach(function(evt) {
                                                                    v.addEventListener(evt, function() { reportStatus(v); });
                                                                });
                                                                reportStatus(v);
                                                            }
                                                        }

                                                        function pollLocalVideo() {
                                                            try {
                                                                var v = document.querySelector('video');
                                                                if (v) {
                                                                    setupListenersForVideo(v);
                                                                    reportStatus(v);
                                                                    if (!v.paused) {
                                                                        window.bloodersAutoplayed = true;
                                                                    }
                                                                    if (!window.bloodersAutoplayed && v.paused) {
                                                                        try { v.play(); } catch(e) {}
                                                                    }
                                                                }
                                                            } catch(err) {
                                                                console.error('[Helper Frame] Error polling:', err);
                                                            }
                                                        }
                                                        function pollLocalVideoOld() {
                                                            try {
                                                                var v = document.querySelector('video');
                                                                if (v) {
                                                                    setupListenersForVideo(v);
                                                                    reportStatus(v);
                                                                }
                                                            } catch(err) {
                                                                console.error('[Helper Frame] Error polling:', err);
                                                            }
                                                        }
                                                        
                                                        if (!window.localVideoPollId) {
                                                            window.localVideoPollId = setInterval(pollLocalVideo, 500);
                                                        }

                                                        // Setup Message receiver with cascading broadcast down all child iframes
                                                        window.addEventListener('message', function(event) {
                                                            try {
                                                                var data = event.data;
                                                                if (!data) return;

                                                                // Relay down to all child frames recursively to bypass cross-origin restrictions
                                                                try {
                                                                    var frames = document.querySelectorAll('iframe');
                                                                    for (var i = 0; i < frames.length; i++) {
                                                                        try {
                                                                            frames[i].contentWindow.postMessage(data, '*');
                                                                        } catch(e) {}
                                                                    }
                                                                } catch(e) {}

                                                                var v = document.querySelector('video');
                                                                if (v) {
                                                                    setupListenersForVideo(v);
                                                                    if (data.type === 'play') {
                                                                        v.play();
                                                                    } else if (data.type === 'pause') {
                                                                        v.pause();
                                                                    } else if (data.type === 'playToggle') {
                                                                        if (v.paused) v.play(); else v.pause();
                                                                    } else if (data.type === 'seek') {
                                                                        var seekTime = parseFloat(data.time);
                                                                        if (!isNaN(seekTime)) {
                                                                            v.currentTime = seekTime;
                                                                        }
                                                                    }
                                                                }
                                                            } catch(err) {
                                                                console.error('[Helper Frame] Control Message error:', err);
                                                            }
                                                        });
                                                    })();
                                                    </script>
                                                """.trimIndent()

                                                // Bypass self === top anti-host link checks that cause white blank screen
                                                 html = html
                                                     .replace("window.self === window.top", "false", ignoreCase = true)
                                                     .replace("window.self == window.top", "false", ignoreCase = true)
                                                     .replace("window.top === window.self", "false", ignoreCase = true)
                                                     .replace("window.top == window.self", "false", ignoreCase = true)
                                                     .replace("self === top", "false", ignoreCase = true)
                                                     .replace("self == top", "false", ignoreCase = true)
                                                     .replace("top === self", "false", ignoreCase = true)
                                                     .replace("top == self", "false", ignoreCase = true)

                                                 if (html.contains("<head>", ignoreCase = true)) {
                                                    html = html.replace("<head>", "<head>\n$scriptToInject", ignoreCase = true)
                                                } else if (html.contains("<body>", ignoreCase = true)) {
                                                    html = html.replace("<body>", "<body>\n$scriptToInject", ignoreCase = true)
                                                } else {
                                                    html = scriptToInject + html
                                                }

                                                return android.webkit.WebResourceResponse(
                                                    "text/html",
                                                    encoding,
                                                    java.io.ByteArrayInputStream(html.toByteArray(charset(encoding)))
                                                )
                                            }
                                        }
                                        response.close()
                                    } catch (e: Exception) {
                                        android.util.Log.e("VideoPlayer", "Error injecting tag in shouldInterceptRequest for URL: " + urlStr + ", detail: " + e.message)
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldInterceptRequest(
                                view: android.webkit.WebView,
                                url: String
                            ): android.webkit.WebResourceResponse? {
                                if (effectiveAdBlocker) {
                                    if (isKnownAdProvider(url)) {
                                        android.util.Log.d("AdBlocker", "Blocked raw request: $url")
                                        return android.webkit.WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            java.io.ByteArrayInputStream("".toByteArray())
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, url)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: android.webkit.WebView,
                                request: android.webkit.WebResourceRequest
                            ): Boolean {
                                if (effectiveAdBlocker) {
                                    val url = request.url.toString()
                                    if (isKnownAdProvider(url) || isAdRedirect(url, cleanUrl)) {
                                        android.util.Log.d("AdBlocker", "Blocked navigation to: $url")
                                        return true // Prevent loading
                                    }
                                }
                                return false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(
                                view: android.webkit.WebView,
                                url: String
                            ): Boolean {
                                if (effectiveAdBlocker) {
                                    if (isKnownAdProvider(url) || isAdRedirect(url, cleanUrl)) {
                                        android.util.Log.d("AdBlocker", "Blocked raw navigation to: $url")
                                        return true // Prevent loading
                                    }
                                }
                                return false
                            }

                            override fun onPageStarted(
                                view: android.webkit.WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                if (effectiveAdBlocker) {
                                    injectAdBlockScript(view)
                                }
                                injectChromeExtensions(view, url)
                            }

                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (effectiveAdBlocker) {
                                    injectAdBlockScript(view)
                                }
                                injectChromeExtensions(view, url)
                                injectVideoControllerScript(view)
                            }
                        }

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                if (effectiveAdBlocker) {
                                    injectAdBlockScript(view)
                                }
                                injectVideoControllerScript(view)
                            }

                            override fun onCreateWindow(
                                view: android.webkit.WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                return false // Blocks popups from spawning secondary windows
                            }
                        }

                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        val isActualHtml = (isEmbedText || cleanUrl.trim().startsWith("<") || cleanUrl.trim().startsWith("<iframe")) &&
                                !(cleanUrl.trim().startsWith("http://", ignoreCase = true) || cleanUrl.trim().startsWith("https://", ignoreCase = true))

                        if (isActualHtml) {
                            val styledHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'></head><body style='margin:0;padding:0;background:#000;display:flex;align-items:center;justify-content:center;'><div style='width:100%;height:100%;display:flex;align-items:center;justify-content:center;'>$cleanUrl</div></body></html>"
                            loadDataWithBaseURL("https://localhost", styledHtml, "text/html", "UTF-8", null)
                        } else {
                            loadUrl(cleanUrl)
                        }
                    }
                },
                update = { webView ->
                    onWebViewCreated?.invoke(webView)
                    val currentTag = webView.tag as? String
                    if (currentTag != cleanUrl) {
                        webView.tag = cleanUrl
                        val isActualHtml = (isEmbedText || cleanUrl.trim().startsWith("<") || cleanUrl.trim().startsWith("<iframe")) &&
                                !(cleanUrl.trim().startsWith("http://", ignoreCase = true) || cleanUrl.trim().startsWith("https://", ignoreCase = true))

                        if (isActualHtml) {
                            val styledHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'></head><body style='margin:0;padding:0;background:#000;display:flex;align-items:center;justify-content:center;'><div style='width:100%;height:100%;display:flex;align-items:center;justify-content:center;'>$cleanUrl</div></body></html>"
                            webView.loadDataWithBaseURL("https://localhost", styledHtml, "text/html", "UTF-8", null)
                        } else {
                            webView.loadUrl(cleanUrl)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = showController
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.useController = showController
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.Center),
                color = Color(0xFFFF2D2D)
            )
        }

        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Fallo al conectar con la fuente del canal",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Intentando buscar fuentes alternativas... | $error",
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }

        // Floating adaptive badges overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            if (libVlcInstalled) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Green)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UTILIDAD LIBVLC ACTIVA",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.Green,
                        fontSize = 10.sp
                    )
                }
            }

            if (codecsPackInstalled) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Green)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CÓDECS MPGA/MP2 ACTIVOS",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = Color.Green,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
class AppendableInputStream : java.io.InputStream() {
    private val queue = java.util.LinkedList<ByteArray>()
    private val lock = Object()
    private var currentArray: ByteArray? = null
    private var currentIndex = 0
    
    @Volatile
    var isFinished = false

    fun append(bytes: ByteArray) {
        synchronized(lock) {
            queue.add(bytes)
            lock.notifyAll()
        }
    }

    fun clear() {
        synchronized(lock) {
            queue.clear()
            currentArray = null
            currentIndex = 0
            isFinished = false
            lock.notifyAll()
        }
    }

    override fun read(): Int {
        synchronized(lock) {
            while (currentArray == null || currentIndex >= currentArray!!.size) {
                if (queue.isNotEmpty()) {
                    currentArray = queue.removeFirst()
                    currentIndex = 0
                } else if (isFinished) {
                    return -1
                } else {
                    try {
                        lock.wait(500) // Block safely on background decoder thread
                        if (queue.isEmpty() && isFinished) {
                            return -1
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return -1
                    }
                }
            }
            return currentArray!![currentIndex++].toInt() and 0xFF
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val nextByte = read()
        if (nextByte == -1) return -1
        b[off] = nextByte.toByte()
        
        var bytesRead = 1
        synchronized(lock) {
            while (bytesRead < len) {
                if (currentArray != null && currentIndex < currentArray!!.size) {
                    val available = currentArray!!.size - currentIndex
                    val toRead = minOf(available, len - bytesRead)
                    System.arraycopy(currentArray!!, currentIndex, b, off + bytesRead, toRead)
                    currentIndex += toRead
                    bytesRead += toRead
                } else {
                    if (queue.isNotEmpty()) {
                        currentArray = queue.removeFirst()
                        currentIndex = 0
                    } else {
                        break
                    }
                }
            }
        }
        return bytesRead
    }
}

@androidx.media3.common.util.UnstableApi
class MpegL2SoftwareAudioRenderer(
    private val context: android.content.Context,
    private val audioFixChannels: String = "Stereo"
) : androidx.media3.exoplayer.BaseRenderer(androidx.media3.common.C.TRACK_TYPE_AUDIO) {

    private val appendStream = AppendableInputStream()
    private var decoderThread: Thread? = null
    
    private var audioTrack: android.media.AudioTrack? = null
    private var currentSampleRate = 0
    private var currentChannels = 0
    private var isEndedValue = false

    private val formatHolder = androidx.media3.exoplayer.FormatHolder()
    private val buffer = androidx.media3.decoder.DecoderInputBuffer(androidx.media3.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun getName(): String = "MpegL2SoftwareAudioRenderer"

    override fun supportsFormat(format: androidx.media3.common.Format): Int {
        val mimeType = format.sampleMimeType
        if (mimeType != null && (
            mimeType.equals("audio/mpeg-L2", ignoreCase = true) ||
            mimeType.equals("audio/mpeg-l2", ignoreCase = true) ||
            mimeType.equals("audio/mpeg-L1", ignoreCase = true) ||
            mimeType.equals("audio/mpeg-l1", ignoreCase = true) ||
            mimeType.equals("audio/mp2", ignoreCase = true) ||
            mimeType.equals("audio/mpa", ignoreCase = true) ||
            mimeType.equals("audio/mpga", ignoreCase = true) ||
            mimeType.equals("audio/mpeg", ignoreCase = true) ||
            mimeType.equals("audio/x-mpeg", ignoreCase = true) ||
            mimeType.equals("audio/x-mpa", ignoreCase = true) ||
            mimeType.equals("audio/x-mp2", ignoreCase = true)
        )) {
            return androidx.media3.exoplayer.RendererCapabilities.create(androidx.media3.common.C.FORMAT_HANDLED)
        }
        return androidx.media3.exoplayer.RendererCapabilities.create(androidx.media3.common.C.FORMAT_UNSUPPORTED_TYPE)
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        if (isEndedValue) return

        while (true) {
            buffer.clear()
            val result = readSource(formatHolder, buffer, 0)
            if (result == androidx.media3.common.C.RESULT_BUFFER_READ) {
                if (buffer.isEndOfStream) {
                    isEndedValue = true
                    appendStream.isFinished = true
                    break
                }
                val byteBuffer = buffer.data
                if (byteBuffer != null && byteBuffer.remaining() > 0) {
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    appendStream.append(bytes)
                }
            } else {
                break
            }
        }
    }

    @Synchronized
    private fun startDecoderThread() {
        stopDecoderThread()
        isEndedValue = false
        appendStream.clear()
        
        decoderThread = Thread({
            Log.d("MpegL2Renderer", "JLayer decodificación iniciada.")
            val bitstream = javazoom.jl.decoder.Bitstream(appendStream)
            val decoder = javazoom.jl.decoder.Decoder()
            try {
                while (!Thread.currentThread().isInterrupted && !isEndedValue) {
                    try {
                        val header = bitstream.readFrame() ?: break
                        val sampleBuffer = decoder.decodeFrame(header, bitstream) as? javazoom.jl.decoder.SampleBuffer
                        if (sampleBuffer != null) {
                            writeToAudioTrack(sampleBuffer.buffer, sampleBuffer.bufferLength, sampleBuffer.sampleFrequency, sampleBuffer.channelCount)
                        }
                        bitstream.closeFrame()
                    } catch (e: Exception) {
                        if (isEndedValue) break
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                Log.e("MpegL2Renderer", "Error decoder: ${e.message}")
            } finally {
                try { bitstream.close() } catch (e: Exception) {}
                releaseAudioTrack()
            }
        }, "MpegL2DecoderThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    private fun stopDecoderThread() {
        try {
            isEndedValue = true
            appendStream.isFinished = true
            appendStream.clear() // Desbloquea hilos esperando en lock.wait()
            
            decoderThread?.apply {
                interrupt()
                // No usamos join() para evitar bloquear el hilo principal de UI, 
                // pero marcamos el thread para morir inmediatamente.
            }
            decoderThread = null
            releaseAudioTrack()
            Log.d("MpegL2Renderer", "Hilos y AudioTrack liberados con éxito.")
        } catch (e: Exception) {
            Log.e("MpegL2Renderer", "Error en parada forzada: ${e.message}")
        }
    }

    private fun releaseAudioTrack() {
        synchronized(this) {
            try {
                audioTrack?.apply {
                    if (state == android.media.AudioTrack.STATE_INITIALIZED) {
                        pause()
                        flush()
                        stop()
                    }
                    release()
                }
            } catch (e: Exception) {
                Log.e("MpegL2Renderer", "Error liberando AudioTrack: ${e.message}")
            }
            audioTrack = null
            currentSampleRate = 0
            currentChannels = 0
        }
    }

    private fun writeToAudioTrack(pcm: ShortArray, length: Int, sampleRate: Int, channels: Int) {
        val targetChannels = when (audioFixChannels) {
            "Forzar Mono" -> 1
            "Forzar Estéreo" -> 2
            else -> channels
        }
        if (audioTrack == null || currentSampleRate != sampleRate || currentChannels != targetChannels) {
            currentSampleRate = sampleRate
            currentChannels = targetChannels
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {}
            
            val channelConfig = if (targetChannels == 1) {
                android.media.AudioFormat.CHANNEL_OUT_MONO
            } else {
                android.media.AudioFormat.CHANNEL_OUT_STEREO
            }
            
            val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            try {
                audioTrack = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBufferSize, 16384) * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build()
                    
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e("MpegL2Renderer", "Failed to initialize AudioTrack: ${e.message}")
            }
        }

        try {
            audioTrack?.write(pcm, 0, length)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun isEnded(): Boolean = isEndedValue

    override fun isReady(): Boolean = true

    override fun onEnabled(joining: Boolean, mayRenderStartOfStream: Boolean) {
        super.onEnabled(joining, mayRenderStartOfStream)
        startDecoderThread()
    }

    override fun onDisabled() {
        super.onDisabled()
        stopDecoderThread()
    }

    override fun onReset() {
        super.onReset()
        stopDecoderThread()
    }

    override fun onStarted() {
        super.onStarted()
        try {
            audioTrack?.play()
        } catch (e: Exception) {}
    }

    override fun onStopped() {
        super.onStopped()
        try {
            audioTrack?.pause()
        } catch (e: Exception) {}
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean) {
        isEndedValue = false
        startDecoderThread()
    }
}

@androidx.media3.common.util.UnstableApi
class MpegL2MappingRenderersFactory(
    private val context: android.content.Context,
    private val audioFixChannels: String = "Stereo"
) : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: android.content.Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: androidx.media3.exoplayer.audio.AudioSink,
        eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
    ) {
        // Add our custom audio renderer that beautifully handles MP2 streams!
        out.add(
            MpegL2SoftwareAudioRenderer(context, audioFixChannels)
        )
        // Add default renderers as fallback
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }
}

private fun isKnownVideoHost(url: String): Boolean {
    val low = url.lowercase()
    return low.contains("youtube.com") || low.contains("youtu.be") ||
            low.contains("vimeo") || low.contains("twitch.tv") ||
            low.contains("dailymotion") || low.contains("trembed") ||
            low.contains("seriesmetro") || low.contains("fembed") ||
            low.contains("upstream") || low.contains("dood") ||
            low.contains("mixdrop") || low.contains("streamtape") ||
            low.contains("voe") || low.contains("vidguard") ||
            low.contains("filemoon") || low.contains("uqload") ||
            low.contains("ok.ru") || low.contains("vk.com") ||
            low.contains("waaw") || low.contains("streamwish") ||
            low.contains("fastream") || low.contains("vidhide") ||
            low.contains("delta") ||
            low.contains("streamvid") || low.contains("streamlare") ||
            low.contains("vidoza") || low.contains("vidlox") ||
            low.contains("speedvideo") || low.contains("mega.co") ||
            low.contains("mega.nz") || low.contains("drive.google") ||
            low.contains("vidsrc") || low.contains("vidplay") ||
            low.contains("mycloud") || low.contains("filelions") ||
            low.contains("streamla") || low.contains("gamovideo") ||
            low.contains("shorby") || low.contains("rapidgator") ||
            low.contains("turbobit") || low.contains("hitfile") ||
            low.contains("gofiles") || low.contains("mediafire")
}

private fun isKnownAdProvider(url: String): Boolean {
    if (isKnownVideoHost(url)) return false
    val lowUrl = url.trim().lowercase()
    
    // Common ad network domains and tracking platforms
    val adHosts = arrayOf(
        "googlesyndication", "googleads", "doubleclick", "popads", "popcash", 
        "adsterra", "exoclick", "mgid", "propellerads", "adform", "bidswitch", 
        "pubmatic", "rubiconproject", "openx", "scorecardresearch", "outbrain", 
        "taboola", "addthis", "amazon-adsystem", "appnexus", "coinhive", 
        "revenuehits", "yllix", "clickadu", "adskeeper", "mobicow", "plugrush", 
        "juicyads", "ero-advertising", "adcash", "yepads", "dynamic-yield", 
        "hotjar", "optimizely", "criteo", "adnxs", "smartadserver", "skadnetwork", 
        "adcolony", "applovin", "unityads", "ironsrc", "vungle", "inmobi", "chartboost",
        "onclickads", "poperads", "popmyads", "exdynsrv", "ad-mav", "admav", "mngbtt",
        "bidgear", "nativeads", "recreativ", "adhigh", "moatads", "bet365", "1xbet",
        "mostbet", "melbet", "pin-up", "casino", "pasterlink", "monetag", "hooliganmedia",
        "popads2", "popunder", "popunder2", "popad", "onclickads", "admav", "bidgear", 
        "smartad", "admixer", "betcris"
    )
    
    for (host in adHosts) {
        if (lowUrl.contains(host)) return true
    }
    
    // Block common ad keywords when accompanied by typical script/ad file formats
    val adKeywords = arrayOf(
        "/ads/", "/ad/", "banner", "popunder", "popup", "adserver", "advert", 
        "tracker", "analytics", "telemetry", "count.php", "click.php", "show_ads",
        "native_ad", "pop_under", "clickunder", "smartad", "popads.php", 
        "popads2.php", "popads.js", "popads2.js", "popunder.js", "pop.js", "ad_overlay"
    )
    
    // Only block directories/keywords if not part of a valid media extension
    val isMediaExtension = lowUrl.contains(".m3u8") || lowUrl.contains(".mp4") || 
            lowUrl.contains(".ts") || lowUrl.contains(".mkv") || lowUrl.contains(".mpd") || 
            lowUrl.contains(".m4s") || lowUrl.contains("/hls/") || lowUrl.contains("/segments/")
            
    if (!isMediaExtension) {
        for (keyword in adKeywords) {
            if (lowUrl.contains(keyword)) {
                // Ignore key terms in secure local contexts
                if (!lowUrl.contains("localhost") && !lowUrl.contains("127.0.0.1")) {
                    return true
                }
            }
        }
    }
    
    return false
}

private fun isAdRedirect(url: String, baseUrl: String, isMainFrame: Boolean = true): Boolean {
    if (!isMainFrame) return false
    if (isKnownVideoHost(url)) return false
    val lowUrl = url.trim().lowercase()
    if (lowUrl.startsWith("intent://") || lowUrl.startsWith("market://") || lowUrl.startsWith("play.google.com")) {
        return true
    }
    
    // Ignore internal actions
    if (lowUrl.startsWith("#") || lowUrl.startsWith("about:") || lowUrl.startsWith("javascript:")) {
        return false
    }

    // Since this is an embed video view, top-level window navigation to anything other than the exact initial webpage
    // or same domain should be blocked as a redirect ad.
    try {
        val baseUri = android.net.Uri.parse(baseUrl)
        val targetUri = android.net.Uri.parse(url)
        val baseHost = baseUri.host
        val targetHost = targetUri.host
        if (baseHost != null && targetHost != null) {
            // Check if subdomain or vice-versa
            if (baseHost != targetHost && !targetHost.endsWith(".$baseHost") && !baseHost.endsWith(".$targetHost")) {
                // Let sub-resources load if it's media, but top frame navigations block!
                val isMedia = lowUrl.contains(".m3u8") || lowUrl.contains(".mp4") || 
                        lowUrl.contains(".ts") || lowUrl.contains(".mpd") || 
                        lowUrl.contains(".mkv") || lowUrl.contains(".m4s") ||
                        lowUrl.contains("/hls/") || lowUrl.contains("/segments/")
                if (!isMedia) {
                    android.util.Log.d("AdBlocker", "Blocking top frame cross-domain redirect: $url (Base: $baseUrl)")
                    return true
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }

    return false
}

private fun injectAdBlockScript(webView: android.webkit.WebView?) {
    val script = """
        (function() {
            // SeriesMetro/BloodersTv Embedded Player Redirect Bypass
            try {
                var currentUrl = window.location.href;
                if (currentUrl.indexOf('seriesmetro') !== -1 || currentUrl.indexOf('blooders') !== -1 || currentUrl.indexOf('pelisplus') !== -1) {
                    var iframes = document.querySelectorAll('iframe');
                    var redirected = false;
                    for (var i = 0; i < iframes.length; i++) {
                        var src = iframes[i].getAttribute('src');
                        if (src && src !== 'about:blank') {
                            var absoluteSrc = src;
                            if (src.indexOf('//') === 0) {
                                absoluteSrc = window.location.protocol + src;
                            } else if (src.indexOf('/') === 0) {
                                absoluteSrc = window.location.origin + src;
                            }
                            
                            if (absoluteSrc.indexOf('seriesmetro') === -1 && absoluteSrc.indexOf('pelisplus') === -1) {
                                console.log('BloodersTv: Redirecting directly to video player host: ' + absoluteSrc);
                                window.location.replace(absoluteSrc);
                                redirected = true;
                                break;
                            } else {
                                if (absoluteSrc !== currentUrl && 
                                    (absoluteSrc.indexOf('trembed') !== -1 || 
                                     absoluteSrc.indexOf('embed') !== -1 || 
                                     absoluteSrc.indexOf('reproductor') !== -1 ||
                                     absoluteSrc.indexOf('player') !== -1)) {
                                    console.log('BloodersTv: Navigating to intermediate player frame: ' + absoluteSrc);
                                    window.location.replace(absoluteSrc);
                                    redirected = true;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (!redirected) {
                        // Watch dynamically for newly added or populated iframes
                        var attempts = 0;
                        var interval = setInterval(function() {
                            attempts++;
                            var dynamicIframes = document.querySelectorAll('iframe');
                            for (var j = 0; j < dynamicIframes.length; j++) {
                                var dsrc = dynamicIframes[j].getAttribute('src');
                                if (dsrc && dsrc !== 'about:blank') {
                                    var dAbsoluteSrc = dsrc;
                                    if (dsrc.indexOf('//') === 0) {
                                        dAbsoluteSrc = window.location.protocol + dsrc;
                                    } else if (dsrc.indexOf('/') === 0) {
                                        dAbsoluteSrc = window.location.origin + dsrc;
                                    }
                                    
                                    if (dAbsoluteSrc.indexOf('seriesmetro') === -1 && dAbsoluteSrc.indexOf('pelisplus') === -1) {
                                        clearInterval(interval);
                                        console.log('BloodersTv: Redirecting dynamically to video player host: ' + dAbsoluteSrc);
                                        window.location.replace(dAbsoluteSrc);
                                        return;
                                    } else {
                                        if (dAbsoluteSrc !== currentUrl && 
                                            (dAbsoluteSrc.indexOf('trembed') !== -1 || 
                                             dAbsoluteSrc.indexOf('embed') !== -1 || 
                                             dAbsoluteSrc.indexOf('reproductor') !== -1 ||
                                             dAbsoluteSrc.indexOf('player') !== -1)) {
                                            clearInterval(interval);
                                            console.log('BloodersTv: Redirecting dynamically to intermediate frame: ' + dAbsoluteSrc);
                                            window.location.replace(dAbsoluteSrc);
                                            return;
                                        }
                                    }
                                }
                            }
                            if (attempts > 30) {
                                clearInterval(interval);
                            }
                        }, 130);
                    }
                }
            } catch(e) {}

            if (window.adBlockerActive) {
                if (typeof window.clearOverlayAds === 'function') {
                    window.clearOverlayAds();
                }
                return;
            }
            window.adBlockerActive = true;

            // Hook createElement to stop ad/popunder scripts from loading dynamically
            try {
                var originalCreateElement = document.createElement;
                document.createElement = function(tagName) {
                    var el = originalCreateElement.apply(this, arguments);
                    if (tagName && tagName.toLowerCase() === 'script') {
                        var originalSetAttribute = el.setAttribute;
                        el.setAttribute = function(name, val) {
                            if (name && name.toLowerCase() === 'src' && val) {
                                var lowVal = val.toLowerCase();
                                var adPatterns = ['popads', 'popunder', 'popcash', 'propellerads', 'exoclick', 'monetag', 'betcris', 'admav', 'hooliganmedia', 'adsterra', 'onclickads'];
                                for (var i = 0; i < adPatterns.length; i++) {
                                    if (lowVal.indexOf(adPatterns[i]) !== -1) {
                                        console.log('AdBlocker: Blocked script load: ' + val);
                                        arguments[1] = 'data:text/javascript;base64,';
                                        break;
                                    }
                                }
                            }
                            return originalSetAttribute.apply(this, arguments);
                        };
                        Object.defineProperty(el, 'src', {
                            get: function() { return el.getAttribute('src'); },
                            set: function(val) { el.setAttribute('src', val); }
                        });
                    } else if (tagName && tagName.toLowerCase() === 'iframe') {
                        var originalSetAttribute = el.setAttribute;
                        el.setAttribute = function(name, val) {
                            if (name && name.toLowerCase() === 'src' && val) {
                                var lowVal = val.toLowerCase();
                                var adPatterns = ['popads', 'popunder', 'popcash', 'propellerads', 'exoclick', 'monetag', 'betcris', 'admav', 'hooliganmedia', 'adsterra', 'onclickads'];
                                for (var i = 0; i < adPatterns.length; i++) {
                                    if (lowVal.indexOf(adPatterns[i]) !== -1) {
                                        console.log('AdBlocker: Blocked iframe load: ' + val);
                                        arguments[1] = 'about:blank';
                                        break;
                                    }
                                }
                            }
                            return originalSetAttribute.apply(this, arguments);
                        };
                        Object.defineProperty(el, 'src', {
                            get: function() { return el.getAttribute('src'); },
                            set: function(val) { el.setAttribute('src', val); }
                        });
                    }
                    return el;
                };
            } catch(e) {}

            // Disable document.write used by old ad scripts
            try {
                document.write = function() { console.log('AdBlocker: Blocked document.write'); };
                document.writeln = function() { console.log('AdBlocker: Blocked document.writeln'); };
            } catch(e) {}

            // Block common annoying JS interfaces and popups
            try { window.open = function() { return { focus: function() {} }; }; } catch(e) {}
            try { window.alert = function() {}; } catch(e) {}
            try { window.confirm = function() { return true; }; } catch(e) {}
            try { window.prompt = function() { return null; }; } catch(e) {}
            try { window.print = function() {}; } catch(e) {}

            // Inject protective CSS styled block directly in header for instant response (no rendering flickers)
            try {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `
                    [id^="div-gpt-ad"], [class^="ad-"], [class$="-ad"],
                    [id*="popunder"], [class*="popunder"], [id*="popup"], [class*="popup"],
                    .popunder, .popup, .adsbox, [class*="ad-container"], [class*="ad-banner"], [class*="ad-wrapper"],
                    iframe[src*="doubleclick"], iframe[src*="propeller"], iframe[src*="exoclick"], iframe[src*="mgid"],
                    div[style*="z-index: 214748364"], div[style*="z-index:214748364"] {
                        display: none !important;
                        visibility: hidden !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        width: 0px !important;
                        height: 0px !important;
                    }
                `;
                document.head.appendChild(style);
            } catch(e) {}

            var isStreamIframe = function(iframe) {
                if (!iframe) return false;
                var src = (iframe.getAttribute('src') || '').toLowerCase();
                if (!src || src === 'about:blank' || src.startsWith('data:')) return false;
                
                var adIframePatterns = [
                    '/ad/', 'googleads', 'popunder', 'clickhelper', 'propeller', 'exoclick', 'mgid', 
                    'doubleclick', 'vast', 'prebid', 'monetag', 'bet365', '1xbet',
                    'casino', 'bet', 'hooligan', 'popads2.php', 'popads.php', 'admav'
                ];
                for (var i = 0; i < adIframePatterns.length; i++) {
                    if (src.indexOf(adIframePatterns[i]) !== -1) {
                        return false;
                    }
                }
                return true; 
            };

            var isRealPlayerOrAncestor = function(el) {
                if (!el) return false;
                
                // Protect the actual HTML5 video node and its ancestors
                var video = document.querySelector('video');
                if (video) {
                    if (el === video || el.contains(video)) {
                        return true;
                    }
                    // Protect any siblings or descendant controls of the video inside its main container (up to 3 levels up from video)
                    var p = video.parentNode;
                    if (p && (p.contains(el) || el.contains(p))) return true;
                    var gp = p ? p.parentNode : null;
                    if (gp && (gp.contains(el) || el.contains(gp))) return true;
                    var ggp = gp ? gp.parentNode : null;
                    if (ggp && (ggp.contains(el) || el.contains(ggp))) return true;
                }
                
                // Protect non-ad stream iframes and their ancestors
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    if (isStreamIframe(iframes[i])) {
                        if (el === iframes[i] || el.contains(iframes[i])) {
                            return true;
                        }
                        // Also protect any descendants or siblings inside the iframe's parent container
                        var ip = iframes[i].parentNode;
                        if (ip && (ip.contains(el) || el.contains(ip))) return true;
                        var igp = ip ? ip.parentNode : null;
                        if (igp && (igp.contains(el) || el.contains(igp))) return true;
                    }
                }
                
                // If it contains ad keywords, it is definitely NOT a protected container
                var lowText = (el.innerText || el.textContent || '').toLowerCase();
                var skipKeywords = [
                    'close ad', 'skip ad', 'saltar anuncio', 'reproducir', 'cerrar anuncio', 
                    'cerrar publicidad', 'close advertisement', 'popads', 'popunder', 'betcris'
                ];
                for (var k = 0; k < skipKeywords.length; k++) {
                    if (lowText.indexOf(skipKeywords[k]) !== -1) {
                        return false; 
                    }
                }
                
                // Fallback protect potential players during load if they don't contain indicators
                var lowId = (el.id || '').toLowerCase();
                var lowClass = (el.className || '').toLowerCase();
                if (lowId.indexOf('player') !== -1 || lowId.indexOf('video') !== -1) return true;
                if (lowClass.indexOf('player') !== -1 || lowClass.indexOf('video') !== -1) return true;
                
                return false;
            };

            var walkAndRemoveAdContainer = function(el) {
                if (!el) return;
                if (isRealPlayerOrAncestor(el)) return;
                
                var current = el;
                for (var depth = 0; depth < 6; depth++) {
                    if (!current || current === document.body || current === document.documentElement) {
                        break;
                    }
                    if (isRealPlayerOrAncestor(current)) {
                        break;
                    }
                    var parent = current.parentNode;
                    if (parent) {
                        if (parent === document.body || parent.tagName === 'BODY' || parent.offsetWidth > window.innerWidth * 0.9) {
                            current.style.display = 'none';
                            try { current.parentNode.removeChild(current); } catch(err) {}
                            return;
                        }
                    }
                    current = parent;
                }
                
                if (el && el.parentNode && !isRealPlayerOrAncestor(el)) {
                    el.style.display = 'none';
                    try { el.parentNode.removeChild(el); } catch(err) {}
                }
            };

            window.clearOverlayAds = function() {
                var badSelectors = [
                    '[id^="div-gpt-ad"]', '[class^="ad-"]', '[class$="-ad"]', '[class*="ad-banner"]',
                    '[class*="ad-container"]', '[class*="ad-wrapper"]', '[class*="ad-slot"]', '[class*="ad-overlay"]',
                    '[id*="popunder"]', '[class*="popunder"]', '[id*="popup"]', '[class*="popup"]',
                    '[class*="overlay"]', '[id*="overlay"]', '[class*="floating-"]', '[class*="banner"]',
                    'a[href*="redirect"]', 'a[target="_blank"]', 'iframe[src*="doubleclick"]',
                    '.video-overlay', '#video-overlay', '.popunder', '.popup', '.adsbox', '.ad-container',
                    '.player-overlay', '.player-ad', '.ad-banner', '.interstitial', '.pop-ads', '.popads',
                    'div[style*="z-index: 21474836"]', 'div[style*="z-index:21474836"]', 
                    'div[style*="z-index: 99999"]', 'div[style*="z-index:99999"]'
                ];

                badSelectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(el) {
                            if (!isRealPlayerOrAncestor(el)) {
                                walkAndRemoveAdContainer(el);
                            }
                        });
                    } catch(e) {}
                });

                // Clear overlays that contain text matching typical ad indicators
                try {
                    var divs = document.querySelectorAll('div, span, p, a, button');
                    var skipKeywords = [
                        'close ad', 'skip ad', 'saltar anuncio', 'reproducir', 'cerrar anuncio', 
                        'cerrar publicidad', 'close advertisement', 'popads', 'popunder', 'betcris',
                        'close ad in', 'comenzar', 'anuncio en', 'anuncio', 'ads', 'publicidad', 'anuncios'
                    ];
                    divs.forEach(function(el) {
                        var txt = (el.innerText || el.textContent || '').toLowerCase();
                        for (var i = 0; i < skipKeywords.length; i++) {
                            if (txt.indexOf(skipKeywords[i]) !== -1) {
                                if (!isRealPlayerOrAncestor(el)) {
                                    walkAndRemoveAdContainer(el);
                                    break;
                                }
                            }
                        }
                    });
                } catch(e) {}

                // Scan all elements for high z-index (excluding video/audio controls)
                try {
                    var allElements = document.querySelectorAll('div, iframe, section, ins');
                    allElements.forEach(function(el) {
                        var style = window.getComputedStyle(el);
                        var zIndex = parseInt(style.zIndex, 10);
                        if (!isNaN(zIndex) && zIndex >= 9999) { // High z-index overlays
                            if (!isRealPlayerOrAncestor(el)) {
                                walkAndRemoveAdContainer(el);
                            }
                        }
                    });
                } catch(e) {}

                // Scan all iframes and block/remove them if they look like ads
                try {
                    var iframes = document.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        if (!isStreamIframe(iframe)) {
                            iframe.style.display = 'none';
                            if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
                        }
                    });
                } catch(e) {}

                // Remove transparent click-intercepting absolute overlays sitting on top of screen
                try {
                    var allDivs = document.querySelectorAll('div');
                    allDivs.forEach(function(div) {
                        var style = window.getComputedStyle(div);
                        if (style.position === 'absolute' || style.position === 'fixed') {
                            var zIndex = parseInt(style.zIndex, 10);
                            if (zIndex > 0) {
                                var width = div.offsetWidth;
                                var height = div.offsetHeight;
                                var isTransparent = style.backgroundColor === 'transparent' || style.backgroundColor === 'rgba(0, 0, 0, 0)' || style.opacity === '0';
                                if (isTransparent && width > 100 && height > 100 && !isRealPlayerOrAncestor(div)) {
                                    div.style.display = 'none';
                                    if (div.parentNode) div.parentNode.removeChild(div);
                                }
                            }
                        }
                    });
                } catch(e) {}

                // Auto play HTML5 video node and auto-click common play button elements
                try {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v && v.paused) {
                            var playPromise = v.play();
                            if (playPromise !== undefined) {
                                playPromise.catch(function(err) {});
                            }
                        }
                    }
                } catch(e) {}

                try {
                    var playSelectors = [
                        'button.vjs-big-play-button', '.jw-display-icon-container', 
                        'button.plyr__control--overlaid', '.playButton', '#playhtml5', 
                        '.play-video', '.play-btn', '.fp-play', '[aria-label="Play"]', 
                        '.jw-icon-playback', '.play', '#player_overlay', 'div.play-button',
                        '.play_button', '.play-overlay', '.play-trigger', '.jw-display-icon-display'
                    ];
                    playSelectors.forEach(function(selector) {
                        try {
                            var el = document.querySelectorAll(selector);
                            for (var j = 0; j < el.length; j++) {
                                var btn = el[j];
                                if (btn && btn.offsetWidth > 0 && btn.offsetHeight > 0 && !btn.hasAttribute('data-auto-clicked')) {
                                    btn.setAttribute('data-auto-clicked', 'true');
                                    btn.click();
                                    console.log('AdBlocker: Auto-clicked play button selector: ' + selector);
                                }
                            }
                        } catch(err) {}
                    });
                } catch(e) {}
            };

            window.clearOverlayAds();
            setInterval(window.clearOverlayAds, 150); // Speed up interval to 150ms!

            // Inhibit all anchor/button click popup redirections to external domains
            document.addEventListener('click', function(e) {
                var target = e.target;
                while (target && target !== document) {
                    if (target.tagName === 'A' || target.hasAttribute('onclick') || target.tagName === 'BUTTON') {
                        var href = target.getAttribute('href') || '';
                        var originalTarget = target.getAttribute('target') || '';
                        if (originalTarget === '_blank') {
                            target.removeAttribute('target');
                        }
                        if (href.startsWith('http') && !href.includes(window.location.hostname)) {
                            e.preventDefault();
                            e.stopPropagation();
                            console.log('Blocked click redirecting to: ' + href);
                            return false;
                        }
                    }
                    target = target.parentNode;
                }
            }, true);

            // Observe dynamic elements to remove them instantly
            try {
                var observer = new MutationObserver(function(mutations) {
                    window.clearOverlayAds();
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true,
                    subtree: true
                });
            } catch(e) {}
        })();
    """.trimIndent()
    webView?.evaluateJavascript(script, null)
}

private fun injectChromeExtensions(webView: android.webkit.WebView?, url: String?) {
    if (webView == null || url == null) return
    val context = webView.context ?: return
    val extensions = com.maratonTv.service.manager.ChromeExtensionManager.getExtensions(context)
    val activeExtensions = extensions.filter { ext ->
        ext.isEnabled && ext.matches.any { pattern ->
            val regexPattern = pattern.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".?")
            try {
                url.matches(Regex(regexPattern, RegexOption.IGNORE_CASE))
            } catch (e: Exception) {
                val domainSegment = pattern.removePrefix("*://").removePrefix("https://").removePrefix("http://").substringBefore("/")
                url.contains(domainSegment, ignoreCase = true)
            }
        }
    }

    if (activeExtensions.isEmpty()) return

    activeExtensions.forEach { ext ->
        android.util.Log.d("ChromeExtension", "Inyectando extensión Chrome: ${ext.name}")
        val scriptToInject = """
            (function() {
                window.chrome = window.chrome || {};
                window.chrome.runtime = window.chrome.runtime || {};
                window.chrome.runtime.id = "${ext.id}";
                window.chrome.runtime.onMessage = window.chrome.runtime.onMessage || {
                    listeners: [],
                    addListener: function(callback) {
                        this.listeners.push(callback);
                    }
                };
                
                try {
                    ${ext.jsCode}
                } catch(e) {
                    console.error("Error running Chrome Extension [${ext.name}]:", e);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(scriptToInject, null)
    }
}

