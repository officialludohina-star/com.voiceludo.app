package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.navigation.NavController
import coil.compose.AsyncImage

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
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0a1f15))) {
        AsyncImage(
            model = LOGIN_BG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Subtle dark overlay so buttons are readable
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

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
                width = GMAIL_WIDTH,
                height = GMAIL_HEIGHT,
                offsetX = GMAIL_OFFSET_X,
                offsetY = GMAIL_OFFSET_Y
            )
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
                policyText.getStringAnnotations("policy", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 36.dp)
        )
    }
}

@Composable
private fun BigIconLoginButton(
    iconUrl: String,
    label: String,
    onClick: () -> Unit,
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
            .clip(RoundedCornerShape(50))
            .clickable { com.voiceludo.app.audio.SoundManager.playClick(); onClick() }
    ) {
        // Naye button images (Login with Gmail / Login with Facebook) mein text
        // pehle se bana hua hai, is liye alag se Text() draw karne ki zaroorat nahi —
        // sirf poori image Fit se dikha dete hain taake text kabhi crop na ho.
        AsyncImage(
            model = iconUrl,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
