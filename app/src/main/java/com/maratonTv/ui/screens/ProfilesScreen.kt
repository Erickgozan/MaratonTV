package com.maratonTv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
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
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.window.Dialog
import com.maratonTv.R
import com.maratonTv.data.Profile
import com.maratonTv.ui.TvViewModel
import com.maratonTv.ui.theme.BloodRed
import com.maratonTv.ui.theme.CardSlate
import com.maratonTv.ui.theme.DarkCharcoal
import com.maratonTv.ui.theme.TextLight

@Composable
fun ProfilesScreen(viewModel: TvViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    var isManageMode by remember { mutableStateOf(false) }
    
    // Dialog state for adding
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var selectedAddAvatarUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150") }
    var newProfilePin by remember { mutableStateOf("") }
    var newProfileM3uUrl by remember { mutableStateOf("") }
    var newProfileEpgUrl by remember { mutableStateOf("") }

    // Dialog state for editing
    var showEditDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var editProfileName by remember { mutableStateOf("") }
    var selectedEditAvatarUrl by remember { mutableStateOf("") }
    var editProfilePin by remember { mutableStateOf("") }
    var editProfileM3uUrl by remember { mutableStateOf("") }
    var editProfileEpgUrl by remember { mutableStateOf("") }

    // Dialog state for PIN validation
    var showPinPromptDialog by remember { mutableStateOf(false) }
    var targetPinProfile by remember { mutableStateOf<Profile?>(null) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Launcher to select a custom profile picture from gallery and save to permanent internal files dir
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "profile_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(file).use { out ->
                        inputStream.use { inp ->
                            inp.copyTo(out)
                        }
                    }
                    val localPath = file.absolutePath
                    if (showAddDialog) {
                        selectedAddAvatarUrl = localPath
                    } else if (showEditDialog) {
                        selectedEditAvatarUrl = localPath
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val avatars = listOf(
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
        "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150",
        "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=150",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150",
        "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150",
        "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
        "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=150"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E1010), // Deep red central glow Atmosphere
                        DarkCharcoal
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            // Header with BLOODERS TV Logo and Brand Style
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.blooders_app_logo_1779688823340),
                    contentDescription = "Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(54.dp)
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "BLOODERS ",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "TV",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black, 
                        fontStyle = FontStyle.Italic
                    ),
                    color = BloodRed
                )
            }

            Text(
                text = if (isManageMode) "Administrar Perfiles • Elige un perfil para modificar de inmediato" else "¿Quién está viendo hoy?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium, 
                    letterSpacing = 0.5.sp
                ),
                color = if (isManageMode) BloodRed else TextLight.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Centered Row of Profiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        isManageMode = isManageMode,
                        onClick = {
                            if (isManageMode) {
                                editingProfile = profile
                                editProfileName = profile.name
                                selectedEditAvatarUrl = profile.avatarUrl
                                editProfilePin = profile.pinCode ?: ""
                                editProfileM3uUrl = profile.customM3uUrl ?: ""
                                editProfileEpgUrl = profile.customEpgUrl ?: ""
                                showEditDialog = true
                            } else {
                                if (!profile.pinCode.isNullOrEmpty()) {
                                    targetPinProfile = profile
                                    enteredPin = ""
                                    pinError = false
                                    showPinPromptDialog = true
                                } else {
                                    viewModel.selectProfile(profile)
                                }
                            }
                        }
                    )
                }

                AddProfileCard(onClick = { 
                    newProfileName = ""
                    newProfilePin = ""
                    newProfileM3uUrl = ""
                    newProfileEpgUrl = ""
                    selectedAddAvatarUrl = avatars.first()
                    showAddDialog = true 
                })
            }

            // Bottom administrative action buttons (professional style)
            var isBtnFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { isManageMode = !isManageMode },
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isBtnFocused) BloodRed else Color.White.copy(alpha = 0.25f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isManageMode) BloodRed.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isManageMode) BloodRed else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .onFocusChanged { isBtnFocused = it.isFocused }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isManageMode) Icons.Default.Check else Icons.Default.Settings,
                        contentDescription = "Configurar",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isManageMode) "Listo" else "Administrar Perfiles",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        }

        // --- ADD DIALOG ---
        if (showAddDialog) {
            Dialog(
                onDismissRequest = { showAddDialog = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.95f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSlate)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Añadir Nuevo Perfil",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { showAddDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        // Scrollable Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = newProfileName,
                                onValueChange = { newProfileName = it },
                                label = { Text("Nombre del Perfil") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = newProfilePin,
                                onValueChange = { newProfilePin = it },
                                label = { Text("PIN / Contraseña de Seguridad (Opcional)") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = newProfileM3uUrl,
                                onValueChange = { newProfileM3uUrl = it },
                                label = { Text("Lista IPTV de este perfil (Opcional JSON, M3U o M3U8)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = newProfileEpgUrl,
                                onValueChange = { newProfileEpgUrl = it },
                                label = { Text("Guía de Programación (EPG URL Opcional)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )

                            Text(
                                "Selecciona una Foto de Perfil:",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(avatars) { url ->
                                    val isSelected = url == selectedAddAvatarUrl
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) BloodRed else Color.Transparent)
                                            .clickable { selectedAddAvatarUrl = url }
                                            .border(
                                                width = if (isSelected) 3.dp else 1.5.dp,
                                                color = if (isSelected) BloodRed else Color.White.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            )
                                            .padding(3.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(url),
                                            contentDescription = "Avatar Opción",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, BloodRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Subir de Galería", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Subir de Galería", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }

                                if (!avatars.contains(selectedAddAvatarUrl) && selectedAddAvatarUrl.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(BloodRed)
                                            .border(width = 3.dp, color = BloodRed, shape = CircleShape)
                                            .padding(3.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(selectedAddAvatarUrl),
                                            contentDescription = "Avatar Personalizado",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        // Footer (Fixed at bottom)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Cancelar", color = TextLight, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    if (newProfileName.isNotBlank()) {
                                        viewModel.createProfile(
                                            name = newProfileName,
                                            avatarUrl = selectedAddAvatarUrl,
                                            pinCode = if (newProfilePin.isBlank()) null else newProfilePin,
                                            customM3uUrl = if (newProfileM3uUrl.isBlank()) null else newProfileM3uUrl,
                                            customEpgUrl = if (newProfileEpgUrl.isBlank()) null else newProfileEpgUrl
                                        )
                                        showAddDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Crear Perfil", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- EDIT / MODIFY DIALOG ---
        if (showEditDialog && editingProfile != null) {
            Dialog(
                onDismissRequest = { showEditDialog = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.95f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSlate)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modificar Perfil",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { showEditDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        // Scrollable Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = editProfileName,
                                onValueChange = { editProfileName = it },
                                label = { Text("Nombre del Perfil") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            )

                            OutlinedTextField(
                                value = editProfilePin,
                                onValueChange = { editProfilePin = it },
                                label = { Text("PIN / Contraseña de Seguridad (Vacío para quitar)") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = editProfileM3uUrl,
                                onValueChange = { editProfileM3uUrl = it },
                                label = { Text("Lista IPTV de este perfil (JSON, M3U o M3U8)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = editProfileEpgUrl,
                                onValueChange = { editProfileEpgUrl = it },
                                label = { Text("Guía de Programación (EPG URL Opcional)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = BloodRed,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedLabelColor = BloodRed,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = BloodRed
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )

                            Text(
                                "Selecciona una Foto de Perfil:",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(avatars) { url ->
                                    val isSelected = url == selectedEditAvatarUrl
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) BloodRed else Color.Transparent)
                                            .clickable { selectedEditAvatarUrl = url }
                                            .border(
                                                width = if (isSelected) 3.dp else 1.5.dp,
                                                color = if (isSelected) BloodRed else Color.White.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            )
                                            .padding(3.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(url),
                                            contentDescription = "Avatar Opción",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, BloodRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Subir de Galería", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Subir de Galería", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }

                                if (!avatars.contains(selectedEditAvatarUrl) && selectedEditAvatarUrl.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(BloodRed)
                                            .border(width = 3.dp, color = BloodRed, shape = CircleShape)
                                            .padding(3.dp)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(selectedEditAvatarUrl),
                                            contentDescription = "Avatar Personalizado",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Delete Option with security design
                            var deleteConfirmRequested by remember { mutableStateOf(false) }

                            if (!deleteConfirmRequested) {
                                Button(
                                    onClick = { deleteConfirmRequested = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Eliminar Perfil", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Red.copy(alpha = 0.1f))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "¿Eliminar este perfil? Se perderán todos sus favoritos.",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                editingProfile?.let {
                                                    viewModel.deleteProfile(it)
                                                }
                                                showEditDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                        ) {
                                            Text("Sí, Eliminar", color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                        TextButton(onClick = { deleteConfirmRequested = false }) {
                                            Text("Cancelar", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Footer (Fixed at bottom)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showEditDialog = false }) {
                                Text("Cancelar", color = TextLight, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    if (editProfileName.isNotBlank() && editingProfile != null) {
                                        viewModel.updateProfile(
                                            editingProfile!!.copy(
                                                name = editProfileName,
                                                avatarUrl = selectedEditAvatarUrl,
                                                pinCode = if (editProfilePin.isBlank()) null else editProfilePin,
                                                customM3uUrl = if (editProfileM3uUrl.isBlank()) null else editProfileM3uUrl,
                                                customEpgUrl = if (editProfileEpgUrl.isBlank()) null else editProfileEpgUrl
                                            )
                                        )
                                        showEditDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- PASSWORD PIN PROMPT DIALOG ---
        if (showPinPromptDialog && targetPinProfile != null) {
            AlertDialog(
                onDismissRequest = { showPinPromptDialog = false },
                title = {
                    Text(
                        "Perfil Protegido",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Ingresa el PIN de acceso para el perfil ${targetPinProfile?.name}:",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { 
                                enteredPin = it 
                                pinError = false
                            },
                            label = { Text("PIN / Contraseña") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = if (pinError) Color.Red else BloodRed,
                                unfocusedBorderColor = if (pinError) Color.Red else Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = if (pinError) Color.Red else BloodRed,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = BloodRed
                            ),
                            singleLine = true,
                            isError = pinError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (pinError) {
                            Text(
                                "PIN Incorrecto, por favor vuelve a intentarlo.",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (enteredPin == targetPinProfile?.pinCode) {
                                showPinPromptDialog = false
                                targetPinProfile?.let { viewModel.selectProfile(it) }
                            } else {
                                pinError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BloodRed)
                    ) {
                        Text("Acceder", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinPromptDialog = false }) {
                        Text("Cancelar", color = TextLight)
                    }
                },
                containerColor = CardSlate,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
fun ProfileCard(profile: Profile, isManageMode: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(12.dp)
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        width = if (isFocused) 4.dp else 1.5.dp,
                        color = if (isFocused) BloodRed else Color.White.copy(alpha = 0.15f)
                    ),
                    shape = CircleShape
                )
                .background(CardSlate)
        ) {
            Image(
                painter = rememberAsyncImagePainter(profile.avatarUrl),
                contentDescription = profile.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // Edit Overlay icon when screen is on profile administration mode
            if (isManageMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .background(BloodRed, CircleShape)
                            .padding(6.dp)
                    )
                }
            }
        }

        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isFocused) BloodRed else TextLight,
            modifier = Modifier.padding(top = 10.dp),
            maxLines = 1
        )
    }
}

@Composable
fun AddProfileCard(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(12.dp)
            .width(130.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        width = if (isFocused) 4.dp else 1.5.dp,
                        color = if (isFocused) BloodRed else Color.White.copy(alpha = 0.2f)
                    ),
                    shape = CircleShape
                )
                .background(CardSlate),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Agregar Perfil",
                tint = if (isFocused) BloodRed else TextLight.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp)
            )
        }

        Text(
            text = "Agregar Perfil",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isFocused) BloodRed else TextLight,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
