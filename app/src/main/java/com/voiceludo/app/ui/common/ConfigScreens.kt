package com.voiceludo.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.net.ButtonCfg
import com.voiceludo.app.net.HTTP_BASE
import com.voiceludo.app.net.ScreenElCfg

// Admin panel se control hone wali cheezein: animations, SVG/image dikhana,
// tap-actions, naye pages (vp_page/<id>), game start/over screen ke layers,
// naye buttons aur gifts ki list.

// ---------------- Animations ----------------

// pulse / bounce / shake / glow / spin / float / fade / swing  (none = kuch nahi)
@Composable
fun Modifier.cfgAnimation(kind: String): Modifier {
    if (kind == "none" || kind.isBlank()) return this
    val t = rememberInfiniteTransition(label = "cfgAnim")
    return when (kind) {
        "pulse" -> {
            val v by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse")
            this.graphicsLayer { scaleX = v; scaleY = v }
        }
        "bounce" -> {
            val v by t.animateFloat(0f, -10f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "bounce")
            this.graphicsLayer { translationY = v * density }
        }
        "shake" -> {
            val v by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "shake")
            this.graphicsLayer { rotationZ = v }
        }
        "glow" -> {
            val v by t.animateFloat(0.65f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
            this.graphicsLayer { alpha = v }
        }
        "spin" -> {
            val v by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
            this.graphicsLayer { rotationZ = v }
        }
        "float" -> {
            val v by t.animateFloat(0f, -6f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "float")
            this.graphicsLayer { translationY = v * density }
        }
        "fade" -> {
            val v by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "fade")
            this.graphicsLayer { alpha = v }
        }
        "swing" -> {
            val v by t.animateFloat(-15f, 15f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "swing")
            this.graphicsLayer { rotationZ = v }
        }
        else -> this
    }
}

fun parseConfigColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

// ---------------- Image / GIF / SVG ----------------

// Apni uploaded SVG (…/a/<id>.svg) WebView mein chalti hai, taake SVG ke andar ki
// apni animation (SMIL/CSS) bhi chale. Baaki sab (png/jpg/gif/webp, bahari svg)
// Coil se dikhte hain.
@Composable
fun RemoteArt(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null
) {
    if (url.startsWith(HTTP_BASE) && url.contains("/a/") && url.endsWith(".svga")) {
        SvgaArt(url, modifier, cover = contentScale == ContentScale.Crop)
    } else if (url.startsWith(HTTP_BASE) && url.contains("/a/") && url.endsWith(".svg")) {
        SvgWebArt(url, modifier, cover = contentScale == ContentScale.Crop)
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

// .svga (YY SVGA animation) — SVGAPlayer-Android se, loop mein chalti hai.
@Composable
private fun SvgaArt(url: String, modifier: Modifier, cover: Boolean) {
    val holder = remember { arrayOfNulls<com.opensource.svgaplayer.SVGAImageView>(1) }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.stopAnimation(true)
            holder[0] = null
        }
    }
    key(url) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                com.opensource.svgaplayer.SVGAImageView(ctx).apply {
                    holder[0] = this
                    loops = 0 // 0 = hamesha chalti rahe
                    clearsAfterStop = false
                    scaleType = if (cover) android.widget.ImageView.ScaleType.CENTER_CROP
                    else android.widget.ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                    val self = this
                    runCatching {
                        com.opensource.svgaplayer.SVGAParser(ctx).decodeFromURL(
                            java.net.URL(url),
                            object : com.opensource.svgaplayer.SVGAParser.ParseCompletion {
                                override fun onComplete(videoItem: com.opensource.svgaplayer.SVGAVideoEntity) {
                                    self.setVideoItem(videoItem)
                                    self.startAnimation()
                                }

                                override fun onError() {}
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SvgWebArt(url: String, modifier: Modifier, cover: Boolean) {
    val holder = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.destroy()
            holder[0] = null
        }
    }
    key(url) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    holder[0] = this
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isClickable = false
                    isFocusable = false
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    val fit = if (cover) "cover" else "contain"
                    loadDataWithBaseURL(
                        "$HTTP_BASE/",
                        "<html><body style=\"margin:0;background:transparent;overflow:hidden\">" +
                            "<img src=\"$url\" style=\"width:100vw;height:100vh;object-fit:$fit\"></body></html>",
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            }
        )
    }
}

// ---------------- Tap actions ----------------

fun runConfigAction(
    action: String,
    navController: NavController,
    context: Context,
    onOpenSettings: () -> Unit = {},
    onOpenGifts: () -> Unit = {}
) {
    when {
        action == "back" -> navController.popBackStack()
        action == "openSettings" -> onOpenSettings()
        action == "openGifts" -> onOpenGifts()
        action == "openShop" ->
            Toast.makeText(context, "Shop jaldi aa raha hai", Toast.LENGTH_SHORT).show()
        action == "openProfile" -> navController.navigate("vp_profile_edit") { launchSingleTop = true }
        action == "openModeSelect" -> navController.navigate("ludo_mode_select") { launchSingleTop = true }
        action == "openHome" -> navController.navigate("vp_home") { launchSingleTop = true }
        action.startsWith("openPage:") -> {
            val id = action.removePrefix("openPage:")
            navController.navigate("vp_page/$id") { launchSingleTop = true }
        }
        action.startsWith("startMode:") -> navController.navigate("ludo_mode_select")
        action.startsWith("url:https://") -> runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(action.removePrefix("url:")))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

// ---------------- Pages / game start / game over ----------------

// Page ka background (rang + image/GIF/SVG). Page panel mein na bana ho to kuch nahi
// dikhta (app ki apni default screen chalti hai).
@Composable
fun ConfigScreenBackground(screenId: String) {
    val sc = AppConfigStore.screen(screenId) ?: return
    // bgColor khali ho to rang nahi lagta — neeche ki app ki apni default background dikhti rehti hai.
    val base = if (sc.bgColor.isNotBlank()) {
        Modifier.fillMaxSize().background(parseConfigColor(sc.bgColor, Color(0xFF0B2A22)))
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier = base) {
        if (sc.bgImage.isNotBlank()) {
            RemoteArt(sc.bgImage, Modifier.fillMaxSize(), ContentScale.Crop)
        }
    }
}

// Page ke items: X/Y screen ke percent (item ka center), W/H dp, rotation, opacity,
// animation aur tap-action — sab panel ke preview jaisa.
@Composable
fun ConfigScreenElements(screenId: String, navController: NavController) {
    val sc = AppConfigStore.screen(screenId) ?: return
    val context = LocalContext.current
    var showGifts by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        sc.elements.filter { it.visible }.forEach { e ->
            key(e.id) {
                val w = e.w.dp
                val h = e.h.dp
                val tap: Modifier = if (e.action.isNotBlank()) {
                    Modifier.clickable {
                        runConfigAction(e.action, navController, context, onOpenGifts = { showGifts = true })
                    }
                } else {
                    Modifier
                }
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * (e.x / 100f) - w / 2, y = maxHeight * (e.y / 100f) - h / 2)
                        .size(w, h)
                        .graphicsLayer(rotationZ = e.rotation.toFloat(), alpha = e.opacity / 100f)
                        .cfgAnimation(e.animation)
                        .then(tap),
                    contentAlignment = Alignment.Center
                ) {
                    ScreenItemContent(e)
                }
            }
        }
    }
    if (showGifts) GiftsDialog(onDismiss = { showGifts = false })
}

@Composable
private fun ScreenItemContent(e: ScreenElCfg) {
    when (e.type) {
        "text" -> Text(
            e.text,
            color = parseConfigColor(e.color, Color.White),
            fontSize = e.fontSize.coerceAtLeast(8).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        "button" -> {
            if (e.src.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF7B733), Color(0xFFC46B1A)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        e.text,
                        color = parseConfigColor(e.color, Color.White),
                        fontSize = e.fontSize.coerceAtLeast(8).sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    RemoteArt(e.src, Modifier.fillMaxSize())
                    if (e.text.isNotBlank()) {
                        Text(
                            e.text,
                            color = parseConfigColor(e.color, Color.White),
                            fontSize = e.fontSize.coerceAtLeast(8).sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        else -> if (e.src.isNotBlank()) RemoteArt(e.src, Modifier.fillMaxSize())
    }
}

// Naya page (route: vp_page/<id>) — kisi bhi icon ke "Page kholo" action se khulta hai.
@Composable
fun ConfigPageScreen(navController: NavController, pageId: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B2A22))) {
        ConfigScreenBackground(pageId)
        ConfigScreenElements(pageId, navController)
        if (AppConfigStore.screen(pageId) == null) {
            Text(
                "Yeh page abhi available nahi hai",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

// ---------------- Panel ke naye buttons (home screen par) ----------------

// X/Y screen ki width/height ka percent hain, size dp mein — bilkul panel ke preview jaisa.
@Composable
fun ConfigButtonsOverlay(
    navController: NavController,
    onOpenSettings: () -> Unit,
    onOpenGifts: () -> Unit
) {
    val buttons: List<ButtonCfg> = AppConfigStore.config.buttons.filter { it.visible }
    if (buttons.isEmpty()) return
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        buttons.forEach { b ->
            val size = b.size.dp
            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * (b.x / 100f) - size / 2,
                        y = maxHeight * (b.y / 100f) - size / 2
                    )
                    .size(size)
                    .cfgAnimation(b.animation)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        runConfigAction(b.action, navController, context, onOpenSettings, onOpenGifts)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (b.image.isNotBlank()) {
                    RemoteArt(b.image, Modifier.fillMaxSize(), contentDescription = b.label)
                } else {
                    Text(
                        b.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---------------- Gifts ----------------

// Panel ke Gifts tab ki list. (Gift bhejna agla step hai — abhi sirf dikhte hain.)
@Composable
fun GiftsDialog(onDismiss: () -> Unit) {
    val gifts = AppConfigStore.config.gifts.filter { it.enabled }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Band karo") } },
        title = { Text("Gifts") },
        text = {
            if (gifts.isEmpty()) {
                Text("Abhi koi gift nahi hai.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(gifts, key = { it.id }) { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp).cfgAnimation(g.animation),
                                contentAlignment = Alignment.Center
                            ) {
                                if (g.image.isNotBlank()) {
                                    RemoteArt(g.image, Modifier.fillMaxSize(), contentDescription = g.name)
                                } else {
                                    Text("\uD83C\uDF81", fontSize = 28.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(g.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "${g.price} ${if (g.currency == "diamonds") "diamonds" else "coins"}",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
