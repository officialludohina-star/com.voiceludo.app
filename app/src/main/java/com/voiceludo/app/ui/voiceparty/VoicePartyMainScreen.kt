package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.ui.common.ConfigScreenElements
import com.voiceludo.app.ui.common.RemoteArt
import com.voiceludo.app.ui.common.cfgElement
import com.voiceludo.app.ui.common.parseConfigColor
import com.voiceludo.app.net.GoogleSignInHelper
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore
import com.voiceludo.app.ui.common.LoadingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Same jungle/moon background as rest of app
private const val LOGIN_BG = "file:///android_asset/img/login_bg.png"
private const val FB_ICON   = "file:///android_asset/img/facebook_login_btn.png"
private const val GMAIL_ICON = "file:///android_asset/img/gmail_login_btn.png"

// Panel se test kiye gaye final numbers — ab permanent bake kar diye.
// (Width naye button images ke asal aspect-ratio se match ki gayi hai.)
private const val FB_WIDTH = 312
private const val FB_HEIGHT = 64
private const val FB_OFFSET_X = 0
private const val FB_OFFSET_Y = 4
private const val GMAIL_WIDTH = 299
private const val GMAIL_HEIGHT = 68
private const val GMAIL_OFFSET_X = 0
private const val GMAIL_OFFSET_Y = 0

@Composable
fun VoicePartyMainScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleBusy by remember { mutableStateOf(false) }   // account chooser / Firebase step
    var loading by remember { mutableStateOf(false) }      // server ke jawab ka intezaar
    var googleEmail by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    // Server se 10 second tak jawab na aaye to spinner band karke error dikhao
    LaunchedEffect(loading) {
        if (loading) {
            delay(10000)
            if (loading) {
                loading = false
                errorText = "Timeout: no response from server in 10 seconds. Please check your network."
            }
        }
    }

    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Auth -> {
                    if (loading) {
                        loading = false
                        AccountStore.saveSession(context, "gmail", googleEmail.ifBlank { "Google account" })
                        SessionStore.save(context, msg.playerId, msg.authToken, msg.name, msg.avatar, msg.coins, msg.diamonds, msg.gameId)
                        navController.navigate("vp_home")
                    }
                }
                is ServerMessage.Err -> {
                    if (loading) {
                        loading = false
                        errorText = if (msg.message.isNotBlank()) msg.message else "Server error"
                    }
                }
                is ServerMessage.ConnectionClosed -> {
                    if (loading) {
                        loading = false
                        errorText = "Could not connect to server: ${msg.reason}"
                    }
                }
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose { BackendClient.removeListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0a1f15))) {
        // Admin panel (Screens > Login screen) ka wallpaper: rang / image / GIF / SVG.
        // Panel mein kuch na lagao to yehi purana default background chalta hai.
        val loginCfg = AppConfigStore.screen("login")
        if (loginCfg != null && loginCfg.bgColor.isNotBlank()) {
            Box(Modifier.fillMaxSize().background(parseConfigColor(loginCfg.bgColor, Color(0xFF0a1f15))))
        }
        if (loginCfg != null && loginCfg.bgImage.isNotBlank()) {
            RemoteArt(loginCfg.bgImage, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            AsyncImage(
                model = LOGIN_BG,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Subtle dark overlay so buttons are readable
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        // Panel ke items jo "Icons ke PEECHE" set hain
        ConfigScreenElements("login", navController, "back")

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Facebook login button
            BigIconLoginButton(
                iconUrl = FB_ICON,
                label = "Login with Facebook",
                onClick = { navController.navigate("vp_facebook_login") },
                cfgKey = "loginFb",
                width = FB_WIDTH,
                height = FB_HEIGHT,
                offsetX = FB_OFFSET_X,
                offsetY = FB_OFFSET_Y
            )
            // Gmail signup button
            BigIconLoginButton(
                iconUrl = GMAIL_ICON,
                label = "Login with Gmail",
                onClick = { navController.navigate("vp_gmail_login") },
                cfgKey = "loginGmail",
                width = GMAIL_WIDTH,
                height = GMAIL_HEIGHT,
                offsetX = GMAIL_OFFSET_X,
                offsetY = GMAIL_OFFSET_Y
            )
            // Continue with Google — seedha main screen par (asal Google login, Firebase)
            val gCfg = AppConfigStore.element("loginGoogle")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(GMAIL_WIDTH.dp)
                    .height(54.dp)
                    .cfgElement("loginGoogle")
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (gCfg.image.isBlank()) Color.White else Color.Transparent)
                    .clickable(enabled = gCfg.visible && !loading && !googleBusy) {
                        com.voiceludo.app.audio.SoundManager.playClick()
                        errorText = ""
                        googleBusy = true
                        scope.launch {
                            GoogleSignInHelper.signIn(
                                activityContext = context,
                                onSuccess = { firebaseToken, mail ->
                                    googleBusy = false
                                    googleEmail = mail
                                    loading = true
                                    BackendClient.socialLogin(firebaseToken)
                                },
                                onError = { msg ->
                                    googleBusy = false
                                    errorText = msg
                                }
                            )
                        }
                    }
            ) {
                if (gCfg.image.isNotBlank()) {
                    RemoteArt(gCfg.image, Modifier.fillMaxSize(), ContentScale.Fit, "Continue with Google")
                } else {
                    Text(
                        gCfg.label.ifBlank { "G  Continue with Google" },
                        color = Color(0xFF3c4043),
                        fontWeight = FontWeight.Bold,
                        fontSize = (if (gCfg.fontSize > 0) gCfg.fontSize else 15).sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (errorText.isNotEmpty()) {
                Text(
                    errorText, color = Color(0xFFcc3333), fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFfdecec), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
            }
        }

        // Panel ke items jo icons ke AAGE hain
        ConfigScreenElements("login", navController, "front")

        if (loading || googleBusy) {
            LoadingOverlay(if (googleBusy) "Connecting to Google..." else "Logging in...")
        }

        // Privacy Policy link — login buttons ke neeche
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val policyText = androidx.compose.ui.text.buildAnnotatedString {
            append("By continuing, you agree to our ")
            pushStringAnnotation(tag = "policy", annotation = com.voiceludo.app.net.HTTP_BASE + "/privacy")
            withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD54F), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                append("Privacy Policy")
            }
            pop()
        }
        androidx.compose.foundation.text.ClickableText(
            text = policyText,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            onClick = { offset ->
                if (AppConfigStore.element("loginPolicy").visible) {
                    policyText.getStringAnnotations("policy", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .cfgElement("loginPolicy")
                .padding(start = 28.dp, end = 28.dp, bottom = 36.dp)
        )
    }
}

@Composable
private fun BigIconLoginButton(
    iconUrl: String,
    label: String,
    onClick: () -> Unit,
    cfgKey: String,
    width: Int,
    height: Int,
    offsetX: Int,
    offsetY: Int
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .offset(x = offsetX.dp, y = offsetY.dp)
            .cfgElement(cfgKey)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = AppConfigStore.element(cfgKey).visible) { com.voiceludo.app.audio.SoundManager.playClick(); onClick() }
    ) {
        // Naye button images (Login with Gmail / Login with Facebook) mein text
        // pehle se bana hua hai, is liye alag se Text() draw karne ki zaroorat nahi —
        // sirf poori image Fit se dikha dete hain taake text kabhi crop na ho.
        // Panel mein is button ki image (PNG / GIF / SVG) badli ho to wahi, warna app ki apni
        RemoteArt(
            AppConfigStore.element(cfgKey).image.ifBlank { iconUrl },
            Modifier.fillMaxSize(),
            ContentScale.Fit,
            label
        )
    }
}
