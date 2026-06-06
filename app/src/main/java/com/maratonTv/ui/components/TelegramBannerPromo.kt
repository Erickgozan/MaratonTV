package com.maratonTv.ui.components

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.maratonTv.R
import kotlinx.coroutines.delay

@Composable
fun TelegramBannerPromo(
    onDismiss: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onDismiss()
    }

    // Capture keys to dismiss banner early when using DPAD controls
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
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
                        val intent = Intent(
                            Intent.ACTION_VIEW,
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
