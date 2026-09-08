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
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore

// Asal HTML (#yallaHome) ka poora lobby screen — topbar (avatar/coins/gems/shop/settings),
// league-rank locked cards, mode grid (2&4 Players / Team), bottom nav (Events/Battle/Chat/Social).
// Sab image assets wahi postimg.cc links hain jo index.html mein the, taake look bilkul match kare.
private const val HOME_BG_IMG = "file:///android_asset/img/lobby_bg.png"
private const val NAV_GLOW_IMG = "file:///android_asset/img/selected-light-green-glow-transparent.png"
private const val PILL_BG_IMG = "file:///android_asset/img/bg-add-coin.png"

// ---- Home-page icon layout: har icon ki final position (Left/Right, Up/Down)
// aur Size — tuning panel se confirm ki gayi values yahan permanently bake kar
// di gayi hain (panel ab hata diya gaya hai, koi runtime editing nahi hoti). ----
private data class IconAdjust(
    val x: Int = 0,      // Left/Right, px
    val y: Int = 0,      // Up/Down, px
    val size: Int = 100  // %, 100 = default
)

private val HOME_LAYOUT: Map<String, IconAdjust> = mapOf(
    "avatar" to IconAdjust(x = -8, y = 5, size = 100),
    "coinPill" to IconAdjust(x = -2, y = 7, size = 100),
    "gemPill" to IconAdjust(x = -1, y = 7, size = 100),
    "shop" to IconAdjust(x = 3, y = 8, size = 159),
    "settings" to IconAdjust(x = 2, y = 5, size = 108),
    "modeCard1" to IconAdjust(x = -9, y = 196, size = 104),
    "modeCard2" to IconAdjust(x = 7, y = 195, size = 104),
    "navEvents" to IconAdjust(x = -23, y = 6, size = 93),
    "navBattle" to IconAdjust(x = -32, y = 13, size = 104),
    "navChat" to IconAdjust(x = -19, y = 8, size = 112),
    "navSocial" to IconAdjust(x = -1, y = 11, size = 106)
)

// Modifier extension — kisi bhi icon/card par lagao, HOME_LAYOUT ke mutabiq
// fixed offset + size apply ho jata hai.
private fun Modifier.homeLayout(key: String): Modifier {
    val a = HOME_LAYOUT[key] ?: IconAdjust()
    return this
        .offset(x = a.x.dp, y = a.y.dp)
        .graphicsLayer(scaleX = a.size / 100f, scaleY = a.size / 100f)
}

// Coins/diamonds ab bekend se aate hain (signup/login ke "auth" jawab se, aur
// jeetne/extra-roll khareedne par "wallet" event se). 10,000 se upar amount ko
// short form mein dikhate hain (jaise "10k", "24.5k", "1.2M") — 10,000 se neechay
// pura number ("9,450" waghera).
private fun formatAmount(v: Long): String = when {
    v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
    v >= 10_000 -> {
        val k = v / 1000.0
        if (k == k.toLong().toDouble()) "${k.toLong()}k" else String.format("%.1fk", k)
    }
    else -> "%,d".format(v)
}


@Composable
fun YallaHomeScreen(navController: NavController) {
    var showSettingsDialog by remember { mutableStateOf(false) }
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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF051a0f))) {
        AsyncImage(
            model = HOME_BG_IMG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                TopBar(
                    onAvatarClick = { navController.navigate("vp_profile_edit") { launchSingleTop = true } },
                    onSettingsClick = { showSettingsDialog = true }
                )
                Spacer(Modifier.height(6.dp))
                StatRow()
                Spacer(Modifier.height(28.dp))
                ModeGrid(navController)
            }
            BottomNav()
        }
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
private fun TopBar(onAvatarClick: () -> Unit, onSettingsClick: () -> Unit = {}) {
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
        savedAvatar.isEmpty() -> "file:///android_asset/img/user-icon.png"
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
                AsyncImage(
                    model = avatarModel,
                    contentDescription = "avatar",
                    modifier = Modifier.size(46.dp).clip(CircleShape),
                    contentScale = if (savedAvatar.isEmpty()) ContentScale.Fit else ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Box(modifier = Modifier.homeLayout("coinPill")) {
            Pill(
                iconUrl = "file:///android_asset/img/coin-icon.webp",
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

        Box(modifier = Modifier.homeLayout("gemPill")) {
            Pill(
                iconUrl = "file:///android_asset/img/diamond.png",
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

        AsyncImage(
            model = "file:///android_asset/img/shop-cart.png",
            contentDescription = "shop",
            modifier = Modifier.size(26.dp).homeLayout("shop")
        )
        Spacer(Modifier.width(14.dp))
        AsyncImage(
            model = "file:///android_asset/img/setting-icon.webp",
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
        AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(24.dp))
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
    unlockW: Int = 160, unlockH: Int = 76, unlockOffX: Int = -16, unlockOffY: Int = 2,
    rankW: Int = 180, rankH: Int = 76, rankOffX: Int = 0, rankOffY: Int = 0
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Pehle se chhota kiya gaya aur left side ki taraf rakha gaya — size/position
        // debug panel (gear icon) se live tune kiye gaye numbers yahan baake hain.
        AsyncImage(
            model = "file:///android_asset/img/unlock_level4_badge.webp",
            contentDescription = "Unlock at Level 4",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(unlockW.dp)
                .height(unlockH.dp)
                .offset(x = unlockOffX.dp, y = unlockOffY.dp)
        )
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model = "file:///android_asset/img/rank_level4_badge.webp",
            contentDescription = "Rank, Unlock at Level 4",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .width(rankW.dp)
                .height(rankH.dp)
                .offset(x = rankOffX.dp, y = rankOffY.dp)
        )
    }
}

@Composable
private fun ModeGrid(navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ModeCard(
            imageUrl = "file:///android_asset/img/2-4-players.png",
            label = "2&4 Players",
            gradient = Brush.linearGradient(listOf(Color(0xFFf7b733), Color(0xFFc46b1a))),
            modifier = Modifier.weight(1f).homeLayout("modeCard1")
        ) { navController.navigate("ludo_mode_select") }

        ModeCard(
            imageUrl = "file:///android_asset/img/team.png",
            label = "Team",
            gradient = Brush.linearGradient(listOf(Color(0xFF8a7bd8), Color(0xFF5a4bc4))),
            modifier = Modifier.weight(1f).homeLayout("modeCard2")
        ) { /* TODO: Team mode abhi implement nahi hui */ }
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
        AsyncImage(
            model = imageUrl,
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
private fun BottomNav() {
    var active by remember { mutableStateOf("Events") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem("file:///android_asset/img/Events.png", "Events", active == "Events", Modifier.homeLayout("navEvents")) { active = "Events" }
        NavItem("file:///android_asset/img/Battle.png", "Battle", active == "Battle", Modifier.homeLayout("navBattle")) { active = "Battle" }
        NavItem("file:///android_asset/img/Chat.png", "Chat", active == "Chat", Modifier.homeLayout("navChat")) { active = "Chat" }
        NavItem("file:///android_asset/img/Social.png", "Social", active == "Social", Modifier.homeLayout("navSocial")) { active = "Social" }
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
            AsyncImage(model = iconUrl, contentDescription = label, modifier = Modifier.size(if (active) 56.dp else 46.dp))
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}
