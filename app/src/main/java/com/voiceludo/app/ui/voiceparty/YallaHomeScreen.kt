package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.audio.SoundManager
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.ui.common.ConfigButtonsOverlay
import com.voiceludo.app.ui.common.ConfigScreenBackground
import com.voiceludo.app.ui.common.ConfigScreenElements
import com.voiceludo.app.ui.common.RemoteArt
import com.voiceludo.app.ui.common.GiftsDialog
import com.voiceludo.app.ui.common.cfgAnimation
import com.voiceludo.app.ui.common.runConfigAction
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore

// Asal HTML (#yallaHome) ka poora lobby screen — topbar (avatar/coins/gems/shop/settings),
// league-rank locked cards, mode grid (2&4 Players / Team), bottom nav (Events/Battle/Chat/Social).
// Sab image assets wahi postimg.cc links hain jo index.html mein the, taake look bilkul match kare.
private const val HOME_BG_IMG = "file:///android_asset/img/lobby_bg.png"
private const val NAV_GLOW_IMG = "file:///android_asset/img/selected-light-green-glow-transparent.png"
private const val PILL_BG_IMG = "file:///android_asset/img/bg-add-coin.png"

// Home screen ka layout ab server (admin panel) se aata hai — AppConfigStore.
// Server na mile to wohi purani baked values chalti hain (AppConfigStore mein).
private fun cfgImg(key: String, default: String): String =
    AppConfigStore.element(key).image.ifBlank { default }

private fun cfgLabel(key: String, default: String): String =
    AppConfigStore.element(key).label.ifBlank { default }

// Kisi bhi icon/card par lagao — panel ke mutabiq offset, size, dikhna/chhupna
// aur animation apply hota hai.
@Composable
private fun Modifier.homeLayout(key: String): Modifier {
    val e = AppConfigStore.element(key)
    return this
        .offset(x = e.x.dp, y = e.y.dp)
        .graphicsLayer(
            scaleX = e.size / 100f,
            scaleY = e.size / 100f,
            alpha = if (e.visible) 1f else 0f
        )
        .cfgAnimation(e.animation)
}

@Composable
fun YallaHomeScreen(navController: NavController) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGifts by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // "Arafta" lobby background music — pehle sirf matching (opponent-dhoondte)
    // screen par chalti thi. User ne kaha ke Home/Lobby screen (yeh screen) par
    // bhi chalni chahiye — taake Home se Matching tak ek hi continuous mahol
    // (music) rahe. Screen chhodte hi (kisi doosre screen par jaate hi) band
    // ho jati hai; agar wapis Home par aaye to phir se shuru ho jati hai.
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.voiceludo.app.audio.SoundManager.startLobbyMusic(context)
        onDispose { com.voiceludo.app.audio.SoundManager.stopLobbyMusic() }
    }

    // Home khulte hi apna on-device saved naam/avatar (agar avatar pehle se
    // upload ho kar hosted URL ban chuka ho) ek dafa bekend ko sync kar dete
    // hain — taake agla match milte hi opponent ko sahi naam/DP dikhe, chahe
    // profile pichli baar app khuli thi tab edit hui ho.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (BackendClient.playerId != null) {
            // BUG FIX: pehle yahan is device ki local profile (khali avatar samet)
            // bhi bekend ko bhej dete thay — agar kisi wajah se is device par
            // avatarUri abhi http URL na bana ho (jaise dobara login/on-off ke
            // baad, ya login server se hui lekin local SharedPreferences purani/
            // khali thi) to yeh bekend par pehle se saved DP/naam ko khali se
            // overwrite kar deta tha — "logout/on-off ke baad DP/naam gayab" wala
            // asal masla yahi tha. Ab pehle bekend (server) ka apna naam/avatar
            // (Auth response se, BackendClient.myName/myAvatar) hi is device ki
            // local ProfileStore mein utar dete hain — taake local copy hamesha
            // server ke sath sync rahe, aur khali avatar se bekend ko kabhi
            // overwrite na karein.
            if (BackendClient.myName.isNotBlank() || BackendClient.myAvatar.isNotBlank()) {
                val local = ProfileStore.get(context)
                if (BackendClient.myName.isNotBlank() && BackendClient.myName != local.name) {
                    ProfileStore.saveName(context, BackendClient.myName)
                }
                if (BackendClient.myAvatar.isNotBlank() && BackendClient.myAvatar != local.avatarUri) {
                    ProfileStore.saveAvatar(context, BackendClient.myAvatar)
                }
            }
            val p = ProfileStore.get(context)
            val avatarForBackend = p.avatarUri.takeIf { it.startsWith("http") } ?: BackendClient.myAvatar
            BackendClient.updateProfile(p.name, avatarForBackend)
        }
    }

    // Har home icon/button ke liye: panel mein "Tap par kaam" set ho to wohi chalta hai,
    // warna button ka purana default kaam (fallback). Panel mein chhupaya hua button tap nahi hota.
    val tap: (String, () -> Unit) -> Unit = { key, fallback ->
        val e = AppConfigStore.element(key)
        if (e.visible) {
            if (e.action.isNotBlank()) {
                runConfigAction(
                    e.action, navController, context,
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenGifts = { showGifts = true }
                )
            } else {
                fallback()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF051a0f))) {
        AsyncImage(
            model = HOME_BG_IMG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Admin panel (Screens > Home) ka background — panel mein na bana ho to upar ki default image chalti hai
        ConfigScreenBackground("home")
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TopBar(
                    onAvatarClick = { tap("avatar") { navController.navigate("vp_profile_edit") { launchSingleTop = true } } },
                    onSettingsClick = { tap("settings") { showSettingsDialog = true } },
                    onShopClick = { tap("shop") { } },
                    onCoinClick = { tap("coinPill") { } },
                    onGemClick = { tap("gemPill") { } }
                )
                Spacer(Modifier.height(6.dp))
                StatRow(tap = tap)
                Spacer(Modifier.height(28.dp))
                ModeGrid(tap = { key, fallback -> tap(key, fallback) }, navController = navController)
            }
            BottomNav(tap = tap)
        }
        // Admin panel (Screens > Home) ke extra icons / SVG / text / buttons
        ConfigScreenElements("home", navController)
        // Panel se banaye gaye naye buttons (position/size/animation/action sab server se)
        ConfigButtonsOverlay(
            navController = navController,
            onOpenSettings = { showSettingsDialog = true },
            onOpenGifts = { showGifts = true }
        )
    }

    if (showGifts) {
        GiftsDialog(onDismiss = { showGifts = false })
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onLoggedOut = {
                showSettingsDialog = false
                navController.navigate("vp_main") { popUpTo(0) }
            }
        )
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit, onLoggedOut: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = AccountStore.getSession(context)
    val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    val methodLabel = when (session?.first) {
        "gmail" -> "Gmail"
        "mobile" -> "Mobile"
        "facebook" -> "Facebook"
        else -> "Guest (login nahi hai)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.Black) },
        text = {
            Column {
                SettingsInfoRow("Device", deviceName)
                SettingsInfoRow("Logged in ID", session?.second ?: "—")
                SettingsInfoRow("Login method", methodLabel)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SoundManager.playClick()
                    AccountStore.clearSession(context)
                    SessionStore.clear(context)
                    onLoggedOut()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFcc3333))
            ) { Text("Logout", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { SoundManager.playClick(); onDismiss() }) { Text("Close") }
        }
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 14.sp, color = Color(0xFF222222), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TopBar(
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onShopClick: () -> Unit = {},
    onCoinClick: () -> Unit = {},
    onGemClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Home ka avatar hamesha profile screen mein save ki hui photo dikhata hai
    // (HTML ke #homeAvatarImg jaisa) — agar kuch save nahi hui to default icon.
    val savedAvatar = ProfileStore.get(context).avatarUri

    // Live coins/diamonds — BackendClient mein already login/signup ke waqt aa
    // chuke hote hain (yahan screen khulte hi unki current value le lete hain),
    // phir "wallet" event (jeetne par ya extra-roll khareedne par) aane par yeh
    // state khud update ho jata hai — koi manual refresh ki zaroorat nahi.
    var coins by remember { mutableStateOf(BackendClient.coins) }
    var diamonds by remember { mutableStateOf(BackendClient.diamonds) }
    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Auth -> { coins = msg.coins; diamonds = msg.diamonds }
                is ServerMessage.Wallet -> {
                    msg.coins?.let { coins = it }
                    msg.diamonds?.let { diamonds = it }
                }
                is ServerMessage.Matched -> coins = msg.coins
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose { BackendClient.removeListener(listener) }
    }
    val avatarModel: Any = when {
        savedAvatar.isEmpty() -> cfgImg("avatar", "file:///android_asset/img/user-icon.png")
        savedAvatar.startsWith("http") -> savedAvatar
        else -> java.io.File(savedAvatar)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(46.dp).homeLayout("avatar").clickable { SoundManager.playClick(); onAvatarClick() }) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF22c55e), Color(0xFFa349ff)))),
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel is String) {
                    RemoteArt(
                        avatarModel,
                        Modifier.size(46.dp).clip(CircleShape),
                        if (savedAvatar.isEmpty()) ContentScale.Fit else ContentScale.Crop,
                        "avatar"
                    )
                } else {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = "avatar",
                        modifier = Modifier.size(46.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.width(6.dp))

        Box(modifier = Modifier.homeLayout("coinPill").clickable { SoundManager.playClick(); onCoinClick() }) {
            Pill(
                iconUrl = cfgImg("coinPill", "file:///android_asset/img/coin-icon.webp"),
                value = formatAmount(coins),
                valueColor = Color(0xFFffd700),
                addBg = Color.Transparent,
                addContent = {
                    AsyncImage(
                        model = "file:///android_asset/img/plus-coin.webp",
                        contentDescription = "add",
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(modifier = Modifier.homeLayout("gemPill").clickable { SoundManager.playClick(); onGemClick() }) {
            Pill(
                iconUrl = cfgImg("gemPill", "file:///android_asset/img/diamond.png"),
                value = formatAmount(diamonds),
                valueColor = Color(0xFF00e5ff),
                addBg = Color.Transparent,
                addContent = {
                    AsyncImage(
                        model = "file:///android_asset/img/plus.png",
                        contentDescription = "add",
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        }

        Spacer(Modifier.weight(1f))

        RemoteArt(
            url = cfgImg("shop", "file:///android_asset/img/shop-cart.png"),
            contentDescription = "shop",
            modifier = Modifier.size(26.dp).homeLayout("shop").clickable { SoundManager.playClick(); onShopClick() }
        )
        Spacer(Modifier.width(14.dp))
        RemoteArt(
            url = cfgImg("settings", "file:///android_asset/img/setting-icon.webp"),
            contentDescription = "settings",
            modifier = Modifier
                .size(34.dp)
                .homeLayout("settings")
                .clickable { SoundManager.playClick(); onSettingsClick() }
        )
    }
}

@Composable
private fun Pill(
    iconUrl: String,
    value: String,
    valueColor: Color,
    addBg: Color,
    addContent: @Composable () -> Unit
) {
    Box {
        AsyncImage(
            model = PILL_BG_IMG,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize().clip(RoundedCornerShape(20.dp))
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        RemoteArt(url = iconUrl, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(addBg),
            contentAlignment = Alignment.Center
        ) { addContent() }
        }
    }
}

@Composable
private fun StatRow(
    tap: (String, () -> Unit) -> Unit = { _, _ -> },
    unlockW: Int = 160, unlockH: Int = 76, unlockOffX: Int = -16, unlockOffY: Int = 2,
    rankW: Int = 180, rankH: Int = 76, rankOffX: Int = 0, rankOffY: Int = 0
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Pehle se chhota kiya gaya aur left side ki taraf rakha gaya — size/position
        // debug panel (gear icon) se live tune kiye gaye numbers yahan baake hain.
        RemoteArt(
            url = cfgImg("unlockBadge", "file:///android_asset/img/unlock_level4_badge.webp"),
            contentDescription = "Unlock at Level 4",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(unlockW.dp)
                .height(unlockH.dp)
                .offset(x = unlockOffX.dp, y = unlockOffY.dp)
                .homeLayout("unlockBadge")
                .clickable { tap("unlockBadge") { } }
        )
        Spacer(Modifier.width(8.dp))
        RemoteArt(
            url = cfgImg("rankBadge", "file:///android_asset/img/rank_level4_badge.webp"),
            contentDescription = "Rank, Unlock at Level 4",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(rankW.dp)
                .height(rankH.dp)
                .offset(x = rankOffX.dp, y = rankOffY.dp)
                .homeLayout("rankBadge")
                .clickable { tap("rankBadge") { } }
        )
    }
}

@Composable
private fun ModeGrid(navController: NavController, tap: (String, () -> Unit) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ModeCard(
            imageUrl = cfgImg("modeCard1", "file:///android_asset/img/2-4-players.png"),
            label = cfgLabel("modeCard1", "2&4 Players"),
            gradient = Brush.linearGradient(listOf(Color(0xFFf7b733), Color(0xFFc46b1a))),
            modifier = Modifier.weight(1f).homeLayout("modeCard1")
        ) { tap("modeCard1") { navController.navigate("ludo_mode_select") } }

        ModeCard(
            imageUrl = cfgImg("modeCard2", "file:///android_asset/img/team.png"),
            label = cfgLabel("modeCard2", "Team"),
            gradient = Brush.linearGradient(listOf(Color(0xFF8a7bd8), Color(0xFF5a4bc4))),
            modifier = Modifier.weight(1f).homeLayout("modeCard2")
        ) { tap("modeCard2") { /* Team mode abhi nahi — panel mein "Tap par kaam" se koi page/screen khol sakte ho */ } }
    }
}

@Composable
private fun ModeCard(
    imageUrl: String,
    label: String,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable { SoundManager.playClick(); onClick() }
    ) {
        RemoteArt(
            url = imageUrl,
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(344f / 220f),
            contentScale = ContentScale.Crop
        )
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun BottomNav(tap: (String, () -> Unit) -> Unit) {
    var active by remember { mutableStateOf("Events") }
    // Panel mein icon par "Tap par kaam" (jaise Page kholo) set ho to wohi chalta hai.
    val go: (String) -> Unit = { key -> tap(key) { } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(cfgImg("navEvents", "file:///android_asset/img/Events.png"), cfgLabel("navEvents", "Events"), active == "Events", Modifier.homeLayout("navEvents")) { active = "Events"; go("navEvents") }
        NavItem(cfgImg("navBattle", "file:///android_asset/img/Battle.png"), cfgLabel("navBattle", "Battle"), active == "Battle", Modifier.homeLayout("navBattle")) { active = "Battle"; go("navBattle") }
        NavItem(cfgImg("navChat", "file:///android_asset/img/Chat.png"), cfgLabel("navChat", "Chat"), active == "Chat", Modifier.homeLayout("navChat")) { active = "Chat"; go("navChat") }
        NavItem(cfgImg("navSocial", "file:///android_asset/img/Social.png"), cfgLabel("navSocial", "Social"), active == "Social", Modifier.homeLayout("navSocial")) { active = "Social"; go("navSocial") }
    }
}

@Composable
private fun NavItem(iconUrl: String, label: String, active: Boolean = false, layoutModifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = layoutModifier.clickable { SoundManager.playClick(); onClick() }) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                AsyncImage(
                    model = NAV_GLOW_IMG,
                    contentDescription = null,
                    modifier = Modifier.size(width = 78.dp, height = 57.dp)
                )
            }
            RemoteArt(url = iconUrl, contentDescription = label, modifier = Modifier.size(if (active) 56.dp else 46.dp))
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

// Coins / diamonds ko hazaron ke comma ke sath dikhata hai (jaise 10000 -> "10,000").
private fun formatAmount(n: Long): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(n)
