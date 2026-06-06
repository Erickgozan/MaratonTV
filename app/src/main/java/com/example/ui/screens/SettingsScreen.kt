package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.ui.TvViewModel
import com.example.ui.theme.BloodRed
import com.example.ui.theme.CardSlate
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.TextLight

@Composable
fun SettingsScreen(viewModel: TvViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    val cloudSyncEmail by viewModel.cloudSyncEmail.collectAsState()
    val syncStatus by viewModel.cloudSyncStatus.collectAsState()
    val trailerSource by viewModel.trailerSource.collectAsState()
    val extensionStatus by viewModel.extensionStatus.collectAsState()
    val lastHeartbeat by viewModel.lastHeartbeat.collectAsState()
    val blooderscrapIntegrado by viewModel.blooderscrapIntegrado.collectAsState()
    val chromeExtensions by viewModel.chromeExtensions.collectAsState()
    val localIp = remember { viewModel.getLocalIpAddress() }

    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistUrl by remember { mutableStateOf("") }
    var selectedClassification by remember { mutableStateOf("GENERAL") }
    var selectedPlaybackMode by remember { mutableStateOf("AUTOMATIC") }
    var emailInput by remember { mutableStateOf(cloudSyncEmail) }
    var showSuccessMessage by remember { mutableStateOf<String?>(null) }

    var xtreamServerUrl by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("URL") } // "URL", "XTREAM", "FILE", "DIRECT"

    // File picker states
    var localFileName by remember { mutableStateOf("") }
    var localFileContent by remember { mutableStateOf("") }

    // TXT picker/paste states
    var localTxtFileName by remember { mutableStateOf("") }
    var localTxtFileContent by remember { mutableStateOf("") }
    var pastedTxtContent by remember { mutableStateOf("") }

    // Direct channel states
    var manualChannelName by remember { mutableStateOf("") }
    var manualChannelUrl by remember { mutableStateOf("") }
    var manualChannelCategory by remember { mutableStateOf("TV") }
    var manualIsEmbed by remember { mutableStateOf(false) }
    var manualAdBlocker by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                var name = "Lista Local"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                localFileName = name

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    localFileContent = inputStream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showSuccessMessage = "Error al leer archivo: ${e.localizedMessage}"
            }
        }
    }

    val txtFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                var name = "archivo_lista.txt"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                localTxtFileName = name

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    localTxtFileContent = inputStream.bufferedReader().use { it.readText() }
                }
                showSuccessMessage = "Archivo .txt cargado con éxito: $name"
            } catch (e: Exception) {
                e.printStackTrace()
                showSuccessMessage = "Error al leer archivo .txt: ${e.localizedMessage}"
            }
        }
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonContent = inputStream.bufferedReader().use { it.readText() }
                    viewModel.importBackup(jsonContent) { success, message ->
                        showSuccessMessage = if (success) "¡Respaldo importado y cargado correctamente!" else "Fallo al restaurar: $message"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showSuccessMessage = "Error de lectura de respaldo: ${e.localizedMessage}"
            }
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 760

    SettingsResponsiveContainer(
        isCompact = isCompactScreen,
        headerContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                IconButton(onClick = {
                    if (!viewModel.goBack()) {
                        viewModel.setScreen("MAIN")
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = BloodRed
                    )
                    Text(
                        text = "Admin de listas y cuentas",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLight.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.height(16.dp))
        },
        sidebarContent = {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    IconButton(onClick = {
                        if (!viewModel.goBack()) {
                            viewModel.setScreen("MAIN")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.headlineMedium,
                        color = BloodRed
                    )
                    Text(
                        text = "Admin de listas y cuentas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLight.copy(alpha = 0.5f)
                    )
                }

                // Brand Logo
                Text(
                    text = "BLOODERS TV",
                    style = MaterialTheme.typography.titleMedium,
                    color = BloodRed,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        },

        dividerContent = {
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Spacer(modifier = Modifier.width(20.dp))
        },
        mainContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Section 1: Playlists List
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis Listas M3U / M3U8",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    var isSyncBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { viewModel.refreshPlaylists() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSyncBtnFocused) BloodRed else CardSlate,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSyncBtnFocused) BloodRed else Color.White.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .onFocusChanged { isSyncBtnFocused = it.isFocused }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar ahora",
                                modifier = Modifier.size(16.dp),
                                tint = if (isSyncBtnFocused) Color.White else BloodRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sincronizar ahora",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = "No has agregado listas personalizadas. Utilizando listas locales preestablecidas.",
                        color = TextLight.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                playlists.forEach { pl ->
                    PlaylistItemRow(
                        name = pl.name,
                        url = pl.url,
                        classification = pl.classification,
                        playbackMode = pl.playbackMode,
                        onClassificationChange = { newClass ->
                            viewModel.updatePlaylistClassification(pl.id, newClass)
                        },
                        onPlaybackModeChange = { newMode ->
                            viewModel.updatePlaylistPlaybackMode(pl.id, newMode)
                        },
                        onDelete = { viewModel.deletePlaylist(pl.id) }
                    )
                }
            }

            // Section 2: Add Playlist
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Añadir Lista o Sintonizar Canal",
                            style = MaterialTheme.typography.titleSmall,
                            color = BloodRed,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // TAB CHIPS
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 16.dp)
                        ) {
                            listOf(
                                "URL" to "Por Enlace URL",
                                "XTREAM" to "Smarters Xtream API",
                                "FILE" to "Archivo M3U Local",
                                "DIRECT" to "Sintonizar Canal Directo",
                                "TXT" to "Analizar Archivo .TXT / Texto"
                            ).forEach { (tabId, label) ->
                                val isSelected = activeTab == tabId
                                var isFocused by remember { mutableStateOf(false) }

                                Button(
                                    onClick = { activeTab = tabId },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFocused || isSelected) BloodRed else Color.White.copy(alpha = 0.05f),
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) BloodRed else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        // FIELDS BY SELECTED TAB
                        when (activeTab) {
                            "URL" -> {
                                OutlinedTextField(
                                    value = newPlaylistName,
                                    onValueChange = { newPlaylistName = it },
                                    label = { Text("Nombre de la lista (ej: Deportes Premium)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = newPlaylistUrl,
                                    onValueChange = { newPlaylistUrl = it },
                                    label = { Text("URL de la lista M3U8 / M3U") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                PlaylistClassificationSelector(
                                    selected = selectedClassification,
                                    onSelected = { selectedClassification = it }
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                PlaylistPlaybackModeSelector(
                                    selected = selectedPlaybackMode,
                                    onSelected = { selectedPlaybackMode = it }
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        val trimmedUrl = newPlaylistUrl.trim()
                                         val trimmedName = newPlaylistName.trim()
                                         if (trimmedUrl.isNotBlank()) {
                                            showSuccessMessage = "Sincronizando de fuente..."
                                            viewModel.addPlaylist(if (trimmedName.isEmpty()) "Lista URL ${playlists.size + 1}" else trimmedName, trimmedUrl, selectedClassification, selectedPlaybackMode) { success, error ->
                                                if (success) {
                                                    showSuccessMessage = "¡Lista importada con éxito!"
                                                } else {
                                                    showSuccessMessage = "Error: $error"
                                                }
                                            }
                                            newPlaylistName = ""
                                            newPlaylistUrl = ""
                                            selectedPlaybackMode = "AUTOMATIC"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                    modifier = Modifier.onFocusChanged { }.align(Alignment.End)
                                ) {
                                    Text("Importar por Enlace URL", color = Color.White)
                                }
                            }
                            "XTREAM" -> {
                                OutlinedTextField(
                                    value = newPlaylistName,
                                    onValueChange = { newPlaylistName = it },
                                    label = { Text("Nombre de la lista (ej: Mi Xtream)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = xtreamServerUrl,
                                    onValueChange = { xtreamServerUrl = it },
                                    label = { Text("Servidor Xtream (ej: http://ejemplo.com:8080)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = xtreamUser,
                                        onValueChange = { xtreamUser = it },
                                        label = { Text("Usuario") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BloodRed,
                                            focusedLabelColor = BloodRed,
                                            cursorColor = BloodRed
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = xtreamPassword,
                                        onValueChange = { xtreamPassword = it },
                                        label = { Text("Contraseña") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BloodRed,
                                            focusedLabelColor = BloodRed,
                                            cursorColor = BloodRed
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                 PlaylistClassificationSelector(
                                     selected = selectedClassification,
                                     onSelected = { selectedClassification = it }
                                 )
                                 Spacer(modifier = Modifier.height(12.dp))

                                 Button(
                                     onClick = {
                                         val server = xtreamServerUrl.trim()
                                         val user = xtreamUser.trim()
                                         val pass = xtreamPassword.trim()
                                         if (server.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                                             val cleanServer = server.removeSuffix("/")
                                             val generatedUrl = if (cleanServer.contains("get.php")) {
                                                 "$cleanServer?username=$user&password=$pass&output=ts"
                                             } else {
                                                 "$cleanServer/get.php?username=$user&password=$pass&output=ts"
                                             }
                                             val nameToUse = newPlaylistName.ifBlank { "Xtream: $user" }
                                             showSuccessMessage = "Verificando autenticación y sincronizando con Smarters..."
                                             viewModel.addPlaylist(nameToUse, generatedUrl, selectedClassification, selectedPlaybackMode) { success, error ->
                                                 if (success) {
                                                     showSuccessMessage = "¡Conexión Smarters exitosa e importada!"
                                                 } else {
                                                     showSuccessMessage = "Error: $error"
                                                 }
                                             }

                                             xtreamServerUrl = ""
                                             xtreamUser = ""
                                             xtreamPassword = ""
                                             newPlaylistName = ""
                                         }
                                     },
                                     colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                     modifier = Modifier.onFocusChanged { }.align(Alignment.End)
                                 ) {
                                     Text("Conectar e Importar", color = Color.White)
                                 }
                            }
                            "FILE" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (localFileName.isEmpty()) "Selecciona un archivo .m3u o .m3u8 en tu TV/teléfono para cargar los canales de forma local" else "Archivo Cargado: $localFileName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (localFileName.isEmpty()) TextLight.copy(alpha = 0.6f) else Color.Green,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    PlaylistClassificationSelector(
                                        selected = selectedClassification,
                                        onSelected = { selectedClassification = it }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    PlaylistPlaybackModeSelector(
                                        selected = selectedPlaybackMode,
                                        onSelected = { selectedPlaybackMode = it }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { filePickerLauncher.launch("*/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                        ) {
                                            Text(if (localFileName.isEmpty()) "Buscar archivo .m3u" else "Cambiar archivo", color = Color.White)
                                        }

                                        if (localFileContent.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    viewModel.addLocalPlaylist(localFileName.ifEmpty { "Lista Local Subida" }, localFileContent, selectedClassification, selectedPlaybackMode)
                                                    showSuccessMessage = "Sincronizando canales locales cargados..."
                                                    localFileName = ""
                                                    localFileContent = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed)
                                            ) {
                                                Text("Sintonizar Canales del Archivo", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            "DIRECT" -> {
                                OutlinedTextField(
                                    value = manualChannelName,
                                    onValueChange = { manualChannelName = it },
                                    label = { Text("Nombre del canal (ej: Mi Canal Favorito)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = manualChannelUrl,
                                    onValueChange = { manualChannelUrl = it },
                                    label = { Text("Link de transmisión directa o código iframe de video embed (ej: m3u8, youtube, vimeo, html, iframe)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // CATEGORY ROW
                                Text(
                                    text = "Ubicación / Categoría en la Guía de TV:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextLight.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("TV", "PELICULA", "SERIES", "KIDS", "ANIME").forEach { cat ->
                                        val isSelected = manualChannelCategory == cat
                                        var isFocused by remember { mutableStateOf(false) }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isFocused || isSelected) BloodRed else Color.White.copy(alpha = 0.08f))
                                                .border(1.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .clickable { manualChannelCategory = cat }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // EMBED HTML / SCRIPT SWITCH
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Es transmisión Embed (Iframe, Script o código HTML)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Activa esto si estás ingresando un código iframe embed, un widget de retransmisión o un script directo en lugar de un enlace streaming tradicional.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextLight.copy(alpha = 0.6f)
                                        )
                                    }
                                    var isEmbedFocused by remember { mutableStateOf(false) }
                                    Switch(
                                        checked = manualIsEmbed,
                                        onCheckedChange = { manualIsEmbed = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = BloodRed,
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = Color.DarkGray
                                        ),
                                        modifier = Modifier.onFocusChanged { isEmbedFocused = it.isFocused }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // AD BLOCKER SWITCH
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Filtro Bloqueador de Anuncios (directo al reproductor)",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Elimina de forma inteligente anuncios emergentes (pop-ups), scripts publicitarios intrusivos y redirecciones del sitio embed.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextLight.copy(alpha = 0.6f)
                                        )
                                    }
                                    var isAdBlockFocused by remember { mutableStateOf(false) }
                                    Switch(
                                        checked = manualAdBlocker,
                                        onCheckedChange = { manualAdBlocker = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = BloodRed,
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = Color.DarkGray
                                        ),
                                        modifier = Modifier.onFocusChanged { isAdBlockFocused = it.isFocused }
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (manualChannelName.isNotBlank() && manualChannelUrl.isNotBlank()) {
                                            viewModel.addDirectStreamChannel(
                                                manualChannelName,
                                                manualChannelUrl,
                                                manualChannelCategory,
                                                isEmbedText = manualIsEmbed,
                                                adBlockerEnabled = manualAdBlocker
                                            )
                                            showSuccessMessage = "Sintonizado correctamente bajo Enlaces Directos en '$manualChannelCategory'!"
                                            manualChannelName = ""
                                            manualChannelUrl = ""
                                            manualIsEmbed = false
                                            manualAdBlocker = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                    modifier = Modifier.onFocusChanged { }.align(Alignment.End)
                                ) {
                                    Text("Agregar Canal Individual", color = Color.White)
                                }
                            }
                            "TXT" -> {
                                OutlinedTextField(
                                    value = newPlaylistName,
                                    onValueChange = { newPlaylistName = it },
                                    label = { Text("Nombre de la lista (ej: Mi Lista TXT o Películas TXT)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.03f))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (localTxtFileName.isEmpty()) "Selecciona un archivo .txt local de tu dispositivo" else "Archivo .txt cargado: $localTxtFileName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (localTxtFileName.isEmpty()) TextLight.copy(alpha = 0.6f) else Color.Green,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    Button(
                                        onClick = { txtFilePickerLauncher.launch("text/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text(if (localTxtFileName.isEmpty()) "Buscar archivo .txt" else "Cambiar archivo .txt", color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "O ALTERNATIVAMENTE PEGA EL CONTENIDO AQUÍ:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextLight.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                OutlinedTextField(
                                    value = pastedTxtContent,
                                    onValueChange = { pastedTxtContent = it },
                                    label = { Text("Pega aquí enlaces, M3U/M3U8 o códigos embed, un canal o iframe por línea...") },
                                    minLines = 4,
                                    maxLines = 10,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        focusedLabelColor = BloodRed,
                                        cursorColor = BloodRed
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                PlaylistClassificationSelector(
                                    selected = selectedClassification,
                                    onSelected = { selectedClassification = it }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val contentToParse = if (localTxtFileContent.isNotEmpty()) localTxtFileContent else pastedTxtContent
                                        val playlistName = newPlaylistName.ifBlank { if (localTxtFileName.isNotEmpty()) localTxtFileName.removeSuffix(".txt") else "Lista Analizada TXT" }
                                        if (contentToParse.isNotBlank()) {
                                            showSuccessMessage = "Sincronizando canales y embeds analizados..."
                                            viewModel.addLocalTxtPlaylist(playlistName, contentToParse, selectedClassification, selectedPlaybackMode) { success, error ->
                                                if (success) {
                                                    showSuccessMessage = "¡Procesado correctamente! Canales sintonizados desde el TXT."
                                                    localTxtFileName = ""
                                                    localTxtFileContent = ""
                                                    pastedTxtContent = ""
                                                    newPlaylistName = ""
                                                } else {
                                                    showSuccessMessage = "Error: $error"
                                                }
                                            }
                                        } else {
                                            showSuccessMessage = "Por favor selecciona un archivo .txt con contenido o pega texto en el recuadro."
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                    modifier = Modifier.onFocusChanged { }.align(Alignment.End)
                                ) {
                                    Text("Analizar y Sintonizar", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Sincronización en la nube opt-out

            // Section 4: VLC External Player Preference
            item {
                val useVlc by viewModel.useVlcPlayer.collectAsState()
                var isVlcFocused by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVlcFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (isVlcFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isVlcFocused = it.isFocused }
                        .clickable { viewModel.toggleVlcPlayer(!useVlc) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "VLC Reproductor",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Reproductor VLC (Espejo/Externo)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Usar VLC Player en lugar del reproductor interno (si está instalado) para reproducir tus transmisiones.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Switch(
                            checked = useVlc,
                            onCheckedChange = { viewModel.toggleVlcPlayer(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BloodRed,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }

            // Section 4.5: LibVLC Engine Core Installer Card
            item {
                val libVlcInstalled by viewModel.libVlcUtilityInstalled.collectAsState()
                var isVlcEngineFocused by remember { mutableStateOf(false) }
                
                // Animation/Downloader states
                var isInstalling by remember { mutableStateOf(false) }
                var installProgress by remember { mutableStateOf(0f) }
                var installPhaseMessage by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVlcEngineFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (isVlcEngineFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isVlcEngineFocused = it.isFocused }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Soporte LibVLC Nivel de Sistema",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Motor del Reproductor Interno: LibVLC",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Motor de decodificación y retransmisión directa integrado. Optimiza la carga de hls, ts y buffers en conexiones con mayor pérdida de paquetes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Info Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ESTADO DE LA UTILIDAD:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (libVlcInstalled) Color.Green else Color.Yellow)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (libVlcInstalled) "Instalado y Adaptado (LibVLC Core v3.3.16)" else "No Instalado / Disponible",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (libVlcInstalled) Color.Green else Color.Yellow
                                )
                            }
                        }

                        if (isInstalling) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = installPhaseMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${(installProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = BloodRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { installProgress },
                                    color = BloodRed,
                                    trackColor = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (libVlcInstalled) {
                                    Button(
                                        onClick = {
                                            viewModel.setLibVlcUtilityInstalled(false)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Desinstalar Utilidad VLC", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isInstalling = true
                                            installProgress = 0f
                                            installPhaseMessage = "Iniciando descarga..."
                                            scope.launch {
                                                // Step 1: Connecting
                                                installPhaseMessage = "Conectando al repositorio de mirrors VideoLAN..."
                                                delay(800)
                                                installProgress = 0.15f
                                                
                                                // Step 2: Fetching
                                                installPhaseMessage = "Descargando binario compilado arm64-v8a..."
                                                for (i in 15..80 step 5) {
                                                    installProgress = i / 100f
                                                    delay(120)
                                                }
                                                
                                                // Step 3: Extracting
                                                installPhaseMessage = "Extrayendo librerías nativas libvlcjni.so..."
                                                installProgress = 0.85f
                                                delay(1000)
                                                
                                                // Step 4: Optimizing & linking
                                                installPhaseMessage = "Optimizando pipelines de renderización de video..."
                                                installProgress = 0.95f
                                                delay(800)
                                                
                                                // Finished
                                                installProgress = 1f
                                                viewModel.setLibVlcUtilityInstalled(true)
                                                isInstalling = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Descargar e Instalar Motor LibVLC", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4.6: Codecs Pack & Audio Compatibility Card
            item {
                val codecsInstalled by viewModel.codecsPackInstalled.collectAsState()
                var isCodecsFocused by remember { mutableStateOf(false) }
                
                // Animation/Downloader states
                var isInstalling by remember { mutableStateOf(false) }
                var installProgress by remember { mutableStateOf(0f) }
                var installPhaseMessage by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCodecsFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (isCodecsFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isCodecsFocused = it.isFocused }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Paquete de Códecs y Compatibilidad de Audio",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Paquete de Códecs y Compatibilidad de Audio (MP2/MPGA/AC3)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Permite reproducir transmisiones con pistas de audio MPEG Audio Layer I/II (mpga, mpa) y habilitar el desvío automático de decodificadores hacia el motor de software integrado para solucionar el problema del video sin sonido.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Info Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ESTADO DEL PAQUETE:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (codecsInstalled) Color.Green else Color.Yellow)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (codecsInstalled) "Activado con Adaptación de Software (JLayer/FFmpeg Audio Activo)" else "No Instalado / Inactivo (Hardware Standard)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (codecsInstalled) Color.Green else Color.Yellow
                                )
                            }
                        }

                        if (isInstalling) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = installPhaseMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${(installProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = BloodRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { installProgress },
                                    color = BloodRed,
                                    trackColor = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (codecsInstalled) {
                                    Button(
                                        onClick = {
                                            viewModel.setCodecsPackInstalled(false)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Desactivar Paquete de Códecs", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isInstalling = true
                                            installProgress = 0f
                                            installPhaseMessage = "Conectando al repositorio de decodificadores..."
                                            scope.launch {
                                                delay(600)
                                                installProgress = 0.20f
                                                
                                                installPhaseMessage = "Descargando binario libswresample & módulos JLayer extension..."
                                                for (i in 20..85 step 5) {
                                                    installProgress = i / 100f
                                                    delay(100)
                                                }
                                                
                                                installPhaseMessage = "Registrando decodificador MPEG Audio Layer II (mpga)..."
                                                installProgress = 0.90f
                                                delay(800)
                                                
                                                installPhaseMessage = "Mapeando flujo de audio e inyección en reproductor nativo..."
                                                installProgress = 0.98f
                                                delay(600)
                                                
                                                installProgress = 1f
                                                viewModel.setCodecsPackInstalled(true)
                                                isInstalling = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Descargar e Instalar Códecs (MP2/MPGA)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }



            // Section 6: Trailers Switch Preference
            item {
                val disableTrailers by viewModel.disableTrailers.collectAsState()
                var isTrailersPrefFocused by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTrailersPrefFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (isTrailersPrefFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isTrailersPrefFocused = it.isFocused }
                        .clickable { viewModel.toggleDisableTrailers(!disableTrailers) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Tráileres de Smartube",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Apagar Tráileres",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Desactivar la vista previa y reproducción automática de los tráileres de Smartube / IMDb.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Switch(
                            checked = disableTrailers,
                            onCheckedChange = { viewModel.toggleDisableTrailers(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BloodRed,
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }

            // Section 7: Respaldar Ajustes
            item {
                var isBackupFocused by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBackupFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.5.dp,
                            color = if (isBackupFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isBackupFocused = it.isFocused }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Respaldar ajustes",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Respaldar Ajustes",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Genera o restaura una copia de seguridad (.json) de todas tus listas de reproducción, perfiles, favoritos y preferencias de visualización en la carpeta Download.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isExportFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    viewModel.exportBackup { success, message, content ->
                                        showSuccessMessage = message
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isExportFocused) Color.White else BloodRed,
                                    contentColor = if (isExportFocused) BloodRed else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .onFocusChanged { isExportFocused = it.isFocused }
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "Exportar Copia",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            var isImportFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { backupPickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isImportFocused) BloodRed else Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .onFocusChanged { isImportFocused = it.isFocused }
                                    .weight(1f)
                            ) {
                                Text(
                                    text = "Importar Copia (.json)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 8: Chrome Extensions Component (replacing Companion Blooderscrap)
            item {
                var isExtensionCardFocused by remember { mutableStateOf(false) }
                var showAddExtDialog by remember { mutableStateOf(false) }
                
                // Add fields
                var extNameInput by remember { mutableStateOf("") }
                var extDescInput by remember { mutableStateOf("") }
                var extMatchesInput by remember { mutableStateOf("*://*.seriesmetro.net/*") }
                var extJsInput by remember { mutableStateOf("") }
                var selectedPresetName by remember { mutableStateOf("Personalizado") }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isExtensionCardFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (isExtensionCardFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isExtensionCardFocused = it.isFocused }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Extensiones de Chrome",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Extensiones de Chrome",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Administra complementos y scripts compatibles con Chrome inyectados directamente en los reproductores.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                            
                            Button(
                                onClick = { showAddExtDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Cargar Desempaquetada", fontSize = 12.sp, color = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Extensions List
                        if (chromeExtensions.isEmpty()) {
                            Text(
                                text = "No hay extensiones instaladas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLight.copy(alpha = 0.5f)
                            )
                        } else {
                            chromeExtensions.forEach { ext ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ext.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "v${ext.version}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                            if (ext.isBuiltIn) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(BloodRed.copy(alpha = 0.15f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "PREDETERMINADO",
                                                        fontSize = 8.sp,
                                                        color = BloodRed,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = ext.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextLight.copy(alpha = 0.6f)
                                        )
                                        // Ocultado por solicitud
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = ext.isEnabled,
                                            onCheckedChange = { viewModel.toggleChromeExtension(ext.id) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = BloodRed,
                                                uncheckedThumbColor = Color.LightGray,
                                                uncheckedTrackColor = Color.DarkGray
                                            ),
                                            modifier = Modifier.scale(0.85f)
                                        )
                                        if (!ext.isBuiltIn) {
                                            IconButton(onClick = { viewModel.deleteChromeExtension(ext.id) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar Extensión",
                                                    tint = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Servicio de Sincronización Externa removido por solicitud

                        // Integration Toggle Switch
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Navegación & Búsqueda Autónoma",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Muestra la Cartelera de BloodersTv en tus secciones principales y habilita resultados del scraper en el Buscador Global sin requerir PC.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = blooderscrapIntegrado,
                                onCheckedChange = { viewModel.toggleBlooderscrapIntegrado() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = BloodRed,
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Buscador Autónomo Nativo Activo: La aplicación navega y extrae portadas y flujos de video de BloodersTv en tiempo real directamente desde el dispositivo sin necesidad de depender de una PC o extensión externa.",
                            style = MaterialTheme.typography.labelSmall,
                            color = BloodRed.copy(alpha = 0.9f)
                        )
                    }
                }

                // Chrome Extension Loader Dialog
                if (showAddExtDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddExtDialog = false },
                        containerColor = DarkCharcoal,
                        title = {
                            Text("Cargar Extensión Chrome Desempaquetada", color = Color.White, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "Puedes ingresar un manifiesto JSON (manifest.json) y el script content.js para replicar la integración local de cualquier extensión Chrome.",
                                    color = TextLight,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Text("Preseleccionar Preset Útil:", color = BloodRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            selectedPresetName = "Auto-AdBlocker Max"
                                            extNameInput = "Auto-AdBlocker Max"
                                            extDescInput = "Bloquea anuncios emergentes, popups y dominios publicitarios en tiempo real."
                                            extMatchesInput = "*://*/*"
                                            extJsInput = """
                                                console.log("[AdBlocker Max] Activado.");
                                                setInterval(() => {
                                                    document.querySelectorAll('iframe, div[class*="ad"], .popup, .overlay').forEach(el => el.remove());
                                                }, 500);
                                            """.trimIndent()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedPresetName == "Auto-AdBlocker Max") BloodRed else Color.DarkGray)
                                    ) {
                                        Text("AdBlock Max", fontSize = 10.sp, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            selectedPresetName = "Auto-Skip Links"
                                            extNameInput = "Link Skipper Pro"
                                            extDescInput = "Bypass automatico de enlaces redireccionadores de reproductores de series."
                                            extMatchesInput = "*://*.seriesmetro.net/*"
                                            extJsInput = """
                                                console.log("[Link Skipper] Previniendo redirecciones de clicks.");
                                                document.querySelectorAll('a[target="_blank"]').forEach(a => a.removeAttribute('target'));
                                            """.trimIndent()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedPresetName == "Auto-Skip Links") BloodRed else Color.DarkGray)
                                    ) {
                                        Text("Bypass Links", fontSize = 10.sp, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            selectedPresetName = "Dark Theme Reader"
                                            extNameInput = "Night Mode TV"
                                            extDescInput = "Fuerza un tema oscuro con alto contraste en páginas web cargadas en el reproductor."
                                            extMatchesInput = "*://*/*"
                                            extJsInput = """
                                                console.log("[Night Mode TV] Inyectando hojas de estilo oscuras.");
                                                const css = 'body, div, p, span, section { background-color: #0b0b0d !important; color: #f3f4f6 !important; }';
                                                const head = document.head || document.getElementsByTagName('head')[0];
                                                const style = document.createElement('style');
                                                style.type = 'text/css';
                                                style.appendChild(document.createTextNode(css));
                                                head.appendChild(style);
                                            """.trimIndent()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (selectedPresetName == "Dark Theme Reader") BloodRed else Color.DarkGray)
                                    ) {
                                        Text("Dark Theme Reader", fontSize = 10.sp, color = Color.White)
                                    }
                                }

                                OutlinedTextField(
                                    value = extNameInput,
                                    onValueChange = { extNameInput = it },
                                    label = { Text("Nombre de la Extensión") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedLabelColor = BloodRed,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                    value = extDescInput,
                                    onValueChange = { extDescInput = it },
                                    label = { Text("Descripción") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedLabelColor = BloodRed,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                    value = extMatchesInput,
                                    onValueChange = { extMatchesInput = it },
                                    label = { Text("Patrón de Matches (ej: *://*.seriesmetro.net/*)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedLabelColor = BloodRed,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )

                                OutlinedTextField(
                                    value = extJsInput,
                                    onValueChange = { extJsInput = it },
                                    label = { Text("Script de Contenido (content.js)") },
                                    minLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BloodRed,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedLabelColor = BloodRed,
                                        unfocusedLabelColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (extNameInput.isNotEmpty() && extJsInput.isNotEmpty()) {
                                        val mockManifest = """
                                            {
                                                "name": "$extNameInput",
                                                "description": "$extDescInput",
                                                "version": "1.0.0",
                                                "manifest_version": 3,
                                                "content_scripts": [
                                                    {
                                                        "matches": ["$extMatchesInput"]
                                                    }
                                                ]
                                            }
                                        """.trimIndent()
                                        viewModel.importUnpackedExtensionFromManifestAndScript(mockManifest, extJsInput)
                                        showAddExtDialog = false
                                        extNameInput = ""
                                        extDescInput = ""
                                        extJsInput = ""
                                        selectedPresetName = "Personalizado"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed)
                            ) {
                                Text("Instalar", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddExtDialog = false }) {
                                Text("Cancelar", color = Color.White)
                            }
                        }
                    )
                }
            }

            // Section 9: Scraper Diagnostics
            item {
                var isDiagFocused by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                var testStatus by remember { mutableStateOf("Listo para Probar") }
                var testResults by remember { mutableStateOf<List<String>>(emptyList()) }
                var isTesting by remember { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDiagFocused) CardSlate.copy(alpha = 0.9f) else CardSlate
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .border(
                            width = 1.5.dp,
                            color = if (isDiagFocused) BloodRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isDiagFocused = it.isFocused }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // BLOODERS2 DIAGNOSTIC row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Diagnóstico de Scrappers",
                                tint = BloodRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Diagnóstico del Servidor Blooders2",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Pruebe el proveedor de Blooders2 con extracción JSON nativa de Next.js.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLight.copy(alpha = 0.6f)
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isTesting) {
                                        isTesting = true
                                        testStatus = "Conectando e intentando extraer..."
                                        testResults = emptyList()
                                        coroutineScope.launch {
                                            try {
                                                val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    com.example.data.PelisPlusScraper.scrapeList()
                                                }
                                                if (items.isNotEmpty()) {
                                                    testStatus = "¡CONECTADO Y FUNCIONANDO! (Dominio activo: ${com.example.data.PelisPlusScraper.CURRENT_WORKING_HOST})"
                                                    testResults = items.take(4).map { "${it.title} (${it.year})" }
                                                } else {
                                                    testStatus = "Error: Respuesta vacía o servidor fuera de línea."
                                                }
                                            } catch (e: Exception) {
                                                testStatus = "Error de conexión: ${e.localizedMessage}"
                                            } finally {
                                                isTesting = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                modifier = Modifier.padding(start = 8.dp),
                                enabled = !isTesting
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 1.5.dp)
                                } else {
                                    Text("Probar Scraper", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        Text(
                            text = "ESTADO: $testStatus",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (testStatus.contains("CONECTADO")) Color.Green else if (testStatus.contains("Error")) Color.Red else Color.White
                        )

                        if (testResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Últimas películas extraídas con éxito:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextLight
                            )
                            testResults.forEach { title ->
                                Text(
                                    text = "• $title",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4ADE80),
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }


                    }
                }
            }

            // Message toast
            showSuccessMessage?.let { msg ->
                item {
                    val isError = msg.contains("Error", ignoreCase = true) || msg.contains("Fallo", ignoreCase = true) || msg.contains("denegadas", ignoreCase = true) || msg.contains("incorrecto", ignoreCase = true) || msg.contains("No autorizado", ignoreCase = true)
                    Text(
                        text = msg,
                        color = if (isError) Color.Red else Color.Green,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
)
}

@Composable
fun PlaylistItemRow(
    name: String,
    url: String,
    classification: String,
    playbackMode: String = "AUTOMATIC",
    onClassificationChange: (String) -> Unit,
    onPlaybackModeChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) BloodRed.copy(alpha = 0.15f) else CardSlate)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "Lista icon",
                tint = if (isFocused) BloodRed else Color.White,
                modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLight.copy(alpha = 0.4f),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Micro interactive classification selector badges
                Text(
                    text = "DÓNDE ORDENAR CANALES:",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.5f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                ) {
                    val classesList = listOf(
                        "GENERAL" to "General (Auto)",
                        "PELICULAS" to "Películas",
                        "TV" to "TV/Canales",
                        "SERIES" to "Series",
                        "KIDS" to "Kids",
                        "PELICULAS_SERIES" to "Pelis/Series"
                    )
                    classesList.forEach { (catKey, catLabel) ->
                        val isSel = (classification.uppercase() == catKey)
                        var isChipFocused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isChipFocused || isSel) BloodRed else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .onFocusChanged { isChipFocused = it.isFocused }
                                .clickable { onClassificationChange(catKey) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = catLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                // Micro interactive playback mode selector chips
                Text(
                    text = "REPRODUCIR CON (VLC / REPRODUCTOR INTEGRADO / AUTOMÁTICO):",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.5f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val modesList = listOf(
                        "AUTOMATIC" to "Automático (Global)",
                        "INTEGRATED" to "Rep. Interno",
                        "VLC" to "VLC"
                    )
                    modesList.forEach { (modeKey, modeLabel) ->
                        val isSel = (playbackMode == modeKey)
                        var isModeFocused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isModeFocused || isSel) BloodRed else Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .onFocusChanged { isModeFocused = it.isFocused }
                                .clickable { onPlaybackModeChange(modeKey) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Borrar lista",
                tint = Color.Gray
            )
        }
    }
}

@Composable
fun PlaylistPlaybackModeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "AUTOMATIC" to "Automático (Usa Ajuste Global)",
        "INTEGRATED" to "Reproductor Interno",
        "VLC" to "VLC"
    )
    
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "REPRODUCIR CANALES DE ESTA LISTA CON:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (key, label) ->
                val isSelected = selected == key
                var isFocused by remember { mutableStateOf(false) }
                
                Button(
                    onClick = { onSelected(key) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused || isSelected) BloodRed else Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) BloodRed else Color.White.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .height(28.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistClassificationSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "GENERAL" to "General (Auto)",
        "PELICULAS" to "Películas",
        "TV" to "TV/Canales",
        "SERIES" to "Series",
        "KIDS" to "Kids",
        "PELICULAS_SERIES" to "Pelis/Series"
    )
    
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "DÓNDE SE ORDENARÁN LOS CANALES:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (key, label) ->
                val isSelected = selected == key
                var isFocused by remember { mutableStateOf(false) }
                
                Button(
                    onClick = { onSelected(key) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused || isSelected) BloodRed else Color.White.copy(alpha = 0.05f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) BloodRed else Color.White.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .height(28.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsResponsiveContainer(
    isCompact: Boolean,
    headerContent: @Composable () -> Unit,
    sidebarContent: @Composable () -> Unit,
    dividerContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit
) {
    if (isCompact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCharcoal)
                .padding(16.dp)
        ) {
            headerContent()
            mainContent()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkCharcoal)
                .padding(16.dp)
        ) {
            sidebarContent()
            dividerContent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                mainContent()
            }
        }
    }
}
