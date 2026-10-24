package com.voiceludo.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ConnState
import com.voiceludo.app.net.NetworkMonitor

// User ne is connection-status banner ke liye khaas yehi icon diya tha.
private const val NO_CONNECTION_ICON = "https://i.postimg.cc/kggkYCRb/IMG-20260902-WA0005.jpg"

// ============================================================================
// Poori app ke upar (har screen par, MainActivity se) yeh chhota banner dikhata
// hai jab bhi (a) phone ka internet band ho, ya (b) internet to hai lekin
// hamara game-server (backend) tak connection nahi ban raha. Jaise hi wapis
// connect ho jaye, banner khud fade ho kar hat jata hai — user ko kuch karne
// ki zaroorat nahi.
// ============================================================================
@Composable
fun ConnectionStatusOverlay(modifier: Modifier = Modifier) {
    val hasInternet by NetworkMonitor.hasInternet.collectAsState()
    val connState by BackendClient.state.collectAsState()

    // Chhota sa wakfa (background se wapsi, network badalna) hone par banner baar baar na chamke:
    // pehle thora intezaar, tabhi dikhao jab masla sach mein kuch der rahe.
    val rawShow = !hasInternet || connState != ConnState.CONNECTED
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(rawShow, hasInternet) {
        if (rawShow) {
            delay(if (!hasInternet) 1500L else 3500L)
            show = true
        } else {
            show = false
        }
    }
    val message = when {
        !hasInternet -> "No internet — please check your connection"
        connState == ConnState.CONNECTING -> "Connecting to server..."
        else -> "No connection to server"
    }

    AnimatedVisibility(visible = show, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFcc2b2b))
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            AsyncImage(model = NO_CONNECTION_ICON, contentDescription = "no connection", modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
            Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
