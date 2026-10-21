package com.voiceludo.app.ui.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.voiceludo.app.ui.common.ConfigScreenBackground
import com.voiceludo.app.ui.common.ConfigScreenElements
import coil.compose.AsyncImage
import com.voiceludo.app.audio.SoundManager
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Asal HTML ke #ludoGameScreen jaisa hi — sab kuch (top icons, settings panel, board,
// har player ka profile+dice) HTML ke % left/top/right values ke mutabiq bilkul
// wahi jagah absolute-position hota hai, taake layout hoobahoo match kare.

private enum class CornerPos { POS_TL, POS_TR, POS_BL, POS_BR }

@Composable
fun LudoGameScreen(navController: NavController, mode: String, players: Int, magic: Boolean = false, betIndex: Int = 0) {
    val ludoMode = LudoMode.valueOf(mode)

    // Asal match-info (mera color, real room, initial server state) — Matching
    // screen ne yeh BackendClient mein rakha tha jab "matched" message aaya tha.
    // Agar kisi wajah se yeh missing ho (jaisay process restart), wapis mode-select
    // par chale jate hain — game bina real room ke shuru nahi ho sakta.
    val matched = remember { BackendClient.consumeLastMatch() }
    if (matched == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val colorList = remember(matched) { matched.players.map { LudoColor.valueOf(it) } }
    val myColor = remember(matched) { LudoColor.valueOf(matched.color) }
    val state = remember(matched) {
        LudoGameState(ludoMode, colorList, magic, myColor).apply {
            applyInitialSnapshot(matched.state)
            applyInitialProfiles(matched.profiles)
        }
    }
    val totalPool = matched.bet * players

    // Safety: game screen par lobby music kabhi nahi bajni chahiye.
    LaunchedEffect(Unit) { SoundManager.stopLobbyMusic() }

    // last_sec.mp3 — turn timer ke aakhri 3 second mein, 1-1 second ki dori se
    // 3 baar "tick" — sirf us player ke apne device par jiski bari/timer hai
    // (opponent ke liye nahi, taake unnecessary shor na ho).
    LaunchedEffect(state.turnTimerKey.value) {
        val total = state.turnTimerSeconds.value
        if (state.turnTimerColor.value != myColor) return@LaunchedEffect
        val waitBefore = (total - 3).coerceAtLeast(0)
        delay(waitBefore * 1000L)
        repeat(3) {
            if (state.turnTimerColor.value != myColor) return@LaunchedEffect
            SoundManager.playLastSecondTick()
            delay(1000)
        }
    }

    // Naye listener ke attach hone se PEHLE hi agar opponent ka naam/DP aa
    // chuka ho (race condition — "matched" ke turant baad "opponentProfile"
    // event, is screen ke apna listener lagane se pehle hi aa jaye), to woh
    // yahan cache se foran uthaya jata hai — isi wajah se pehle kabhi-kabhi
    // DP/naam poori game mein kabhi load hi nahi hota tha.
    LaunchedEffect(matched) {
        BackendClient.consumeCachedOpponentProfiles().forEach { p ->
            state.onOpponentProfile(p.color, p.name, p.avatar)
        }
    }

    // Server se aane wale "events"/"opponentLeft" is room ke liye yahan process hote
    // hain, aur screen se nikalte hi room chhod diya jata hai (koi fake bot ab
    // takeover nahi karta jaisa pehle local sim mein hota tha).
    var opponentLeftMsg by remember { mutableStateOf<String?>(null) }
    // "wallet" event ka message (jeetne par "pot credit ho gaya", extra-roll
    // khareedne ki confirmation, waghera) — kuch second ke liye ek banner dikhata hai.
    var walletMsg by remember { mutableStateOf<String?>(null) }
    DisposableEffect(matched) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Events -> { state.onConnectionRestored(); state.onServerEvents(msg.events, msg.state) }
                is ServerMessage.OpponentLeft -> {
                    opponentLeftMsg = "${msg.color} game chhod gaya"
                    SoundManager.playOppLeft()
                }
                is ServerMessage.OpponentProfile -> state.onOpponentProfile(msg.color, msg.name, msg.avatar)
                is ServerMessage.TurnTimer -> state.onTurnTimer(msg.color, msg.seconds)
                is ServerMessage.Wallet -> msg.message?.let { walletMsg = it }
                // Roll reject ho (jaise turn already badal chuka) to dice button
                // hamesha ke liye disabled na reh jaye — flag wapis khol dete hain.
                is ServerMessage.Err -> state.rollRequested.value = false
                // Apna khud ka connection toota — "Reconnecting…" overlay + 30s countdown
                // shuru; BackendClient khud reconnect hote hi "resume" bhej dega.
                is ServerMessage.ConnectionClosed -> state.onConnectionLost()
                // Bekend ne poora room/game state wapis de diya — seedha usi se sync
                // kar lete hain, koi dobara navigate/re-match nahi karna.
                is ServerMessage.Resumed -> {
                    state.applyInitialSnapshot(msg.state)
                    state.applyInitialProfiles(msg.profiles)
                    state.onConnectionRestored()
                }
                is ServerMessage.OpponentDisconnected -> walletMsg = "${msg.color} ka connection chala gaya — reconnect ka intezaar"
                is ServerMessage.OpponentReconnected -> walletMsg = "${msg.color} wapis aa gaya"
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose {
            BackendClient.removeListener(listener)
            BackendClient.leaveRoom()
        }
    }

    // Apna "Exit" tap karne par game jaan-boojh kar chhod ke wapis mode-select par
    val requestExit by state.requestExit
    LaunchedEffect(requestExit) {
        if (requestExit) {
            navController.popBackStack()
        }
    }
    // walletMsg kuch der dikha kar khud gayab ho jata hai (naya message aaye ya na aaye)
    LaunchedEffect(walletMsg) {
        if (walletMsg != null) {
            kotlinx.coroutines.delay(3000)
            walletMsg = null
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showBetInfo by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(true) }

    // BUG FIX: pehle board hamesha fixed rehta tha (GREEN=TL, YELLOW=TR, BLUE=BR,
    // RED=BL) chahe "mera" color kuch bhi ho — isi wajah se ek phone par apna ghar
    // neeche dikhta tha aur dusre phone par (jahan color alag tha) upar dikhta tha.
    // Ab poora board + har player ka profile/dice-box itna rotate kar dete hain ke
    // MERA color hamesha bottom-left (RED ki jagah) par aaye — jaisay asal Ludo apps
    // mein hota hai (har player apna ghar hamesha neeche hi dekhta hai). Colors ka
    // aapas ka clockwise order (Green->Yellow->Blue->Red->Green) bilkul wahi rehta
    // hai, bas poora set ghoom jata hai.
    val colorClockwise = listOf(LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE, LudoColor.RED)
    val cornerClockwise = listOf(CornerPos.POS_TL, CornerPos.POS_TR, CornerPos.POS_BR, CornerPos.POS_BL)
    val rotationSteps = remember(matched) {
        val myIdx = colorClockwise.indexOf(myColor)
        val redIdx = colorClockwise.indexOf(LudoColor.RED) // RED ki native jagah hi "bottom" hai
        ((redIdx - myIdx) % 4 + 4) % 4
    }
    fun visualCornerFor(color: LudoColor): CornerPos {
        val nativeIdx = colorClockwise.indexOf(color)
        return cornerClockwise[(nativeIdx + rotationSteps) % 4]
    }

    // Kisi bhi color ke profile-box ki (BoxWithConstraints ke top-left se)
    // asal absolute jagah — Golden Dice ki "udaan" animation ka target point
    // nikalne ke liye, forEach ke bahar bhi reusable.
    fun profileAbsoluteOffset(color: LudoColor, screenW: Dp, screenH: Dp): Pair<Dp, Dp> {
        val pos = visualCornerFor(color)
        val spec = when (pos) {
            CornerPos.POS_BL -> PosSpec(1f, 79f, -8f, -10f, false)
            CornerPos.POS_TL -> PosSpec(1f, 10f, -6f, 12f, false)
            CornerPos.POS_TR -> PosSpec(1f, 10f, 2f, 16f, true)
            CornerPos.POS_BR -> PosSpec(1f, 79f, 6f, -14f, true)
        }
        val xOffset = screenW * (spec.baseX / 100f) + spec.translateX.dp
        val yOffset = screenH * (spec.baseY / 100f) + spec.translateY.dp
        val absoluteX = if (spec.alignEnd) screenW - xOffset else xOffset
        return absoluteX to yOffset
    }

    // #ludoGameScreen: position:fixed; inset:0; background game-bg.webp cover
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val scope = rememberCoroutineScope()

        AsyncImage(
            model = GAME_BG_IMG,
            contentDescription = "background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Admin panel (Screens > Game board) ka background aur decor — board ke PEECHE rehta hai
        ConfigScreenBackground("game")
        ConfigScreenElements("game", navController)

        // .game-board-wrap: position:absolute; top:50%; left:50%; translate(-50%,-50%);
        // width:100vw; height:auto (square board image)
        // rotationZ: poora board itna ghumaya jata hai ke MERA color hamesha bottom-left
        // (neeche) par nazar aaye — chahe asal (server-truth) color kuch bhi ho.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer(rotationZ = rotationSteps * 90f)
        ) {
            LudoBoardCanvas(state) { _, idx ->
                if (state.currentColor == state.myColor && !state.isMoving.value) {
                    state.tapToken(idx)
                }
            }
        }

        // Golden Dice "udaan": jab koi color Golden Dice uthaye, uska icon
        // board ke beech se uth kar seedha us player ki profile tak udh kar
        // jata hai — pohanchte hi profile ke pass hamesha ke liye chhota badge
        // reh jata hai (upar PlayerProfileBox mein).
        val goldenFlight = remember { Animatable(0f) }
        var goldenFlightColor by remember { mutableStateOf<LudoColor?>(null) }
        LaunchedEffect(state.goldenDiceFlightTrigger.value) {
            if (state.goldenDiceFlightTrigger.value > 0) {
                goldenFlightColor = state.goldenDiceFlightColor.value
                goldenFlight.snapTo(0f)
                goldenFlight.animateTo(1f, animationSpec = tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                goldenFlightColor = null
            }
        }
        goldenFlightColor?.let { flyColor ->
            val (targetX, targetY) = profileAbsoluteOffset(flyColor, screenW, screenH)
            val startX = screenW / 2
            val startY = screenH / 2
            val t = goldenFlight.value
            val curX = startX + (targetX - startX) * t
            // Halka arc-lift beech mein (jaise waqai udh raha ho, seedhi line mein nahi)
            val arcLift = 50.dp * (4f * t * (1f - t))
            val curY = startY + (targetY - startY) * t - arcLift
            AsyncImage(
                model = MAGIC_DICE_ICON,
                contentDescription = "golden dice flying",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = curX - 20.dp, y = curY - 20.dp)
                    .size(40.dp)
            )
        }

        // .game-top-icons-wrap: position:absolute; top:8px; left:0; width:100%; height:44px
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 8.dp)
                .fillMaxWidth()
                .height(44.dp)
        ) {
            AsyncImage(
                model = TOP_BAR_BG_IMG,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp))
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = SETTINGS_ICON, contentDescription = "settings",
                    modifier = Modifier.size(40.dp).clickable { SoundManager.playClick(); showSettings = !showSettings }
                )
                AsyncImage(
                    model = BET_INFO_ICON, contentDescription = "bet info",
                    modifier = Modifier.size(44.dp).clickable { SoundManager.playClick(); showBetInfo = true }
                )
            }
        }

        // .game-settings-panel: position:absolute; top:56px; left:10px (asal jagah), lekin
        // ab user isay drag kar ke jahan marzi le ja sakta hai (panelDragOffset yaad rakhta
        // hai) — kyunke ye panel board ke oopar kaafi jagah gher leta tha aur fixed jagah
        // par disturb karta tha.
        var panelDragOffset by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        if (showSettings) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (with(density) { 10.dp.toPx() } + panelDragOffset.x).roundToInt(),
                            (with(density) { 56.dp.toPx() } + panelDragOffset.y).roundToInt()
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            panelDragOffset += dragAmount
                        }
                    }
                    .background(Color(0xF20F1E37), RoundedCornerShape(12.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsOption(
                    icon = if (soundOn) SOUND_ICON else SOUND_OFF_ICON,
                    label = if (soundOn) "On" else "Off",
                    onClick = {
                        // BUG FIX: pehle yeh sirf ek local UI toggle tha — icon
                        // "Off" dikhata tha lekin SoundManager ko kabhi pata hi
                        // nahi chalta tha, is liye sound asal mein kabhi mute
                        // nahi hoti thi. Ab SoundManager.muted bhi sath mein
                        // set hota hai. Order jaan-boojh kar aisa hai: mute
                        // karte hi turant khamosh (khud is click ki awaz bhi
                        // nahi), aur wapis on karte hi turant ek click sunayi
                        // deta hai (confirm karne ke liye ke sound wapis chalu
                        // ho gayi).
                        soundOn = !soundOn
                        SoundManager.muted = !soundOn
                        SoundManager.playClick()
                    }
                )
                SettingsOption(
                    icon = EXIT_ICON,
                    label = "Exit",
                    onClick = { SoundManager.playClick(); navController.popBackStack() }
                )
            }
        }

        // .game-players-wrap: position:absolute; inset:0 — har player ka apna
        // profile + dice box, ab bhi rotation-aware corner par (dekho upar
        // visualCornerFor) — taake "mera" box board ke rotated ghar ke sath hi
        // match kare, sirf 2 corners tak mehdood nahi (2P mein bhi rotation
        // lagta hai taake dono phone consistent nazar aayein).
        val positions: List<CornerPos> = colorList.map { visualCornerFor(it) }

        colorList.forEachIndexed { i, color ->
            val pos = positions[i]
            val isSelf = color == state.myColor
            // Server se aaya asal naam/DP (matched.profiles / opponentProfile) —
            // agar kisi wajah se abhi tak na aaya ho (naya opponent, race condition)
            // to fallback "Aap"/"Player N" dikhata hai jaisa pehle tha.
            val serverProfile = state.profiles[color]
            val name = serverProfile?.name?.takeIf { it.isNotBlank() }
                ?: if (isSelf) "Aap" else "Player ${i + 1}"
            val avatarUrl = serverProfile?.avatar?.takeIf { it.isNotBlank() }

            val spec = when (pos) {
                CornerPos.POS_BL -> PosSpec(1f, 79f, -8f, -10f, false)
                CornerPos.POS_TL -> PosSpec(1f, 10f, -6f, 12f, false)
                CornerPos.POS_TR -> PosSpec(1f, 10f, 2f, 16f, true)
                CornerPos.POS_BR -> PosSpec(1f, 79f, 6f, -14f, true)
            }

            val xOffset = screenW * (spec.baseX / 100f) + spec.translateX.dp
            val yOffset = screenH * (spec.baseY / 100f) + spec.translateY.dp
            val cornerAlign = if (spec.alignEnd) Alignment.TopEnd else Alignment.TopStart
            val xForOffset = if (spec.alignEnd) -xOffset else xOffset

            Row(
                modifier = Modifier
                    .align(cornerAlign)
                    .offset(x = xForOffset, y = yOffset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val profile = @Composable {
                    PlayerProfileBox(
                        name = name,
                        avatarUrl = avatarUrl,
                        color = color,
                        rank = state.rankBadge[color],
                        // Yeh player ki abhi 12-second turn timer chal rahi hai to
                        // ring dikhao — game khatam hote hi (ya kisi aur ki timer
                        // start hote hi) yeh apne aap gayab ho jati hai.
                        showTimerRing = !state.gameOver.value && state.turnTimerColor.value == color,
                        timerSeconds = state.turnTimerSeconds.value,
                        timerKey = state.turnTimerKey.value,
                        hasGoldenDice = color in state.goldenDiceHolders
                    )
                }
                val dice = @Composable {
                    // Sirf jis player ki abhi bari hai uska hi dice-box dikhta hai —
                    // baaki sab players ka dice box gayab rehta hai jab tak unki apni
                    // bari na aa jaye (tab woh khud-b-khud reveal ho jata hai). Isi
                    // wajah se doosron ka koi dice/arrow bhi dikhne ka sawal nahi rehta.
                    if (color != state.currentColor) {
                        Box(modifier = Modifier.size(50.dp))
                    } else {
                        Box {
                            // Jis player ki abhi bari hai uske dice-box ke oopar ek green
                            // arrow bounce karta hai (HTML jaisa) — taake turn kiski hai
                            // yeh turant, saaf saaf nazar aaye. User ne kaha: yeh sirf
                            // usi banday ko dikhe jiski asal mein baari hai — baaki
                            // players (jo abhi sirf dekh rahe hain) ko yeh arrow nazar
                            // NA aaye, chahe unhe pata ho ke kiski baari hai (dice-box
                            // aur token-glow se to sabko pata chal hi jata hai).
                            if (!state.gameOver.value && isSelf) {
                                TurnArrow()
                            }
                            PlayerDiceBox(
                                // Har player apna alag stored dice-value dikhata hai (HTML jaisa) —
                                // rolling-gif sirf usi color ke box mein chalti hai jiski abhi turn hai.
                                diceValue = state.diceByColor[color] ?: 1,
                                rolling = state.isRolling.value,
                                isClickable = isSelf,
                                // BUG FIX: pehle yahan ek galat extra check tha
                                // "state.currentIdx.value == 0" — jiski wajah se dice
                                // SIRF us player ke liye clickable hota tha jiska
                                // turn-order mein index 0 ho. Baaki colors (jaise
                                // yellow, agar woh index 0 par na ho) ka dice hamesha
                                // disabled reh jata tha — na tap hota tha, na token
                                // move hoti thi. Yahan hum already color==currentColor
                                // wale branch ke andar hain, to bas isSelf + normal
                                // game-state checks hi kaafi hain.
                                enabled = isSelf && !state.gameOver.value &&
                                    state.movable.isEmpty() && !state.isRolling.value && !state.isMoving.value &&
                                    !state.rollRequested.value,
                                onClick = { scope.launch { state.rollDice() } }
                            )
                            // Asal HTML ke gameDiceNum_${idx} badge jaisa — is turn mein ab tak
                            // jama hue saved-rolls numbers ("6+4" jaisay) dice-box ke upar dikhata hai
                            if (state.savedRolls.isNotEmpty()) {
                                Text(
                                    state.savedRolls.joinToString("+"),
                                    color = Color(0xFF5A3100),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-8).dp)
                                        .background(
                                            Brush.verticalGradient(listOf(Color(0xFFFFEC66), Color(0xFFFFB300))),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            // Diamonds se extra dice-roll khareedne wala badge — sirf apni bari
                            // mein, aur sirf jab tak is game mein is color ke liye lock (1000
                            // diamond cap) nahi ho chuka (cost > 0). Cost badge par hi dikhta
                            // hai (2, phir 4, 8, 16... — har purchase pichli se double).
                            val extraCost = if (isSelf) state.extraRollNextCost[color] ?: 0L else 0L
                            if (isSelf && extraCost > 0 && !state.gameOver.value && !state.isRolling.value && !state.isMoving.value) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xF2143c64))
                                        .clickable { scope.launch { state.buyExtraRoll() } }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    AsyncImage(
                                        model = "file:///android_asset/img/diamond.png",
                                        contentDescription = "extra roll",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        "$extraCost",
                                        color = Color(0xFF6EC3FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
                // Player 3 (pos-tr) aur Player 4 (pos-br) ke liye dice pehle, profile baad me
                if (pos == CornerPos.POS_TR || pos == CornerPos.POS_BR) {
                    dice(); profile()
                } else {
                    profile(); dice()
                }
            }
        }

        // Neeche wala status/hint bar hata diya gaya hai (user ki request par) — ab board
        // par "Apki baari hai — dice roll karein" jaisa koi message nahi dikhta. Turn ka
        // pata ab sirf active player ke dice-box ke oopar wale green arrow (neeche) aur
        // token ke glow se chalta hai.

        // "wallet" event ka message (jeetna/pot-credit/extra-roll confirmation) aur
        // "opponent left" — dono ek chhota banner top-center par, kuch second ke liye.
        if (!state.gameOver.value) {
            (walletMsg ?: opponentLeftMsg)?.let { msg ->
                Text(
                    msg,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xF2143c64))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Asal HTML ke #rollChoicePopup jaisa — jab ek movable token ke liye ek se
        // zyada ALAG saved number (jaisay 6 aur 4 dono) legal hon, player yahan se
        // chuney ke kaunsa number is token par apply karna hai.
        state.rollChoice.value?.let { choice ->
            val popupScope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .background(Color(0xF20A1423), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFFFFCC33), RoundedCornerShape(10.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                choice.options.forEach { opt ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFFFFEC66), Color(0xFFFFB300))))
                            .clickable { popupScope.launch { state.chooseRoll(opt) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${opt.value}", color = Color(0xFF5A3100), fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }

        if (state.gameOver.value) {
            // Admin panel ("Game over screen") ka background aur icons — result card ke neeche
            ConfigScreenBackground("result")
            ConfigScreenElements("result", navController)
            val winnerC = state.winnerColor.value
            val winnerName = winnerC?.let { state.profiles[it]?.name?.takeIf(String::isNotBlank) ?: it.name } ?: "?"
            val loserNames = state.players.filter { it != winnerC }
                .map { state.profiles[it]?.name?.takeIf(String::isNotBlank) ?: it.name }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xF20A1423), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFFFFCC33), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "\uD83C\uDFC6 $winnerName JEET GAYA!",
                    color = Color(0xFFFFD400),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                if (winnerC != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "+$totalPool coins jeete",
                        color = Color(0xFF6EE86E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                if (loserNames.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Lost: ${loserNames.joinToString(", ")}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { SoundManager.playClick(); navController.popBackStack() }) { Text("Back to Mode Select") }
            }
        }

        // Trophy/bet-info icon tap par yeh dikhata hai — kitne coins laga kar yeh
        // game shuru hua tha aur total pool kitna hai.
        if (showBetInfo) {
            AlertDialog(
                onDismissRequest = { showBetInfo = false },
                title = { Text("Bet Info", fontWeight = FontWeight.Black) },
                text = {
                    Column {
                        Text(
                            "You played this game with a bet of ${matched.bet} coins.",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Entry Coins: ${matched.bet}",
                            color = Color(0xFF0a7a42), fontWeight = FontWeight.Black, fontSize = 14.sp
                        )
                        Text(
                            "Total Pool ($players players): $totalPool",
                            color = Color(0xFF0a7a42), fontWeight = FontWeight.Black, fontSize = 14.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { SoundManager.playClick(); showBetInfo = false }) { Text("Close") }
                }
            )
        }

        // ---- Net drop overlay: "Reconnecting… Ns" (game peeche dikhti rehti hai,
        // isi bar-jaise banner ke sath). Server apni taraf se pending player ki
        // turn khud auto-play karta rehta hai isi dauran. ----
        val connectionLost by state.connectionLost
        val reconnectSeconds by state.reconnectSecondsLeft
        if (connectionLost) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .background(Color(0xFF222222).copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = NO_CONNECTION_ICON,
                        contentDescription = "no connection",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Reconnecting… ${reconnectSeconds}s",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
            }
        }

        // Grace window guzar gayi aur wapis connect nahi hua — Exit/Connect popup
        val showReconnectChoice by state.showReconnectChoice
        if (showReconnectChoice) {
            AlertDialog(
                onDismissRequest = { /* deliberately no-op — user ko choose karna hai */ },
                icon = {
                    AsyncImage(model = NO_CONNECTION_ICON, contentDescription = "no connection", modifier = Modifier.size(36.dp))
                },
                title = { Text("Connection lost", fontWeight = FontWeight.Black) },
                text = { Text("Could not reconnect within $RECONNECT_GRACE_SECONDS seconds. Try again or leave the game?") },
                confirmButton = {
                    TextButton(onClick = { SoundManager.playClick(); state.retryConnect() }) { Text("Connect") }
                },
                dismissButton = {
                    TextButton(onClick = { SoundManager.playClick(); state.exitGame() }) { Text("Exit") }
                }
            )
        }

        // ==== TEMP: Rocket flight tuning panel ====
        // User rocket ki udaan (duration/lift/pre-gap) khud live adjust kar ke
        // dekhna chahte hain, phir final numbers bata denge jo hum seedha
        // RocketTuning.kt ke defaults mein lock kar denge (aur yeh panel hata
        // denge) — bilkul pehle wale block-icon tuner jaisa.
        var showRocketTuner by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 90.dp, start = 4.dp)
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC000000))
                .clickable { SoundManager.playClick(); showRocketTuner = !showRocketTuner },
            contentAlignment = Alignment.Center
        ) { Text("\uD83D\uDE80", fontSize = 15.sp) }

        if (showRocketTuner) {
            var durVal by remember { mutableStateOf(RocketTuning.durationMs.value / 10f) }
            var liftVal by remember { mutableStateOf(RocketTuning.liftDp.value.toFloat()) }
            var gapVal by remember { mutableStateOf(RocketTuning.preGapMs.value.toFloat()) }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 124.dp, start = 4.dp, end = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xEE111111))
                    .padding(10.dp)
            ) {
                Text("Rocket Tuner", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)

                Text("Duration: ${(durVal * 10).toInt()}ms", color = Color(0xFFFFD400), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Slider(
                    value = durVal, valueRange = 1f..200f,
                    onValueChange = { durVal = it; RocketTuning.durationMs.value = (it * 10).toInt() },
                    modifier = Modifier.width(240.dp)
                )

                Text("Lift: ${liftVal.toInt()}dp", color = Color(0xFFFFD400), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Slider(
                    value = liftVal, valueRange = 1f..200f,
                    onValueChange = { liftVal = it; RocketTuning.liftDp.value = it.toInt() },
                    modifier = Modifier.width(240.dp)
                )

                Text("Pre-gap: ${gapVal.toInt()}ms", color = Color(0xFFFFD400), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Slider(
                    value = gapVal, valueRange = 1f..200f,
                    onValueChange = { gapVal = it; RocketTuning.preGapMs.value = it.toInt() },
                    modifier = Modifier.width(240.dp)
                )

                Text(
                    // Yeh final line copy kar ke bhej dein — hum seedha
                    // RocketTuning.kt mein lock kar denge.
                    "duration=${(durVal * 10).toInt()} lift=${liftVal.toInt()} gap=${gapVal.toInt()}",
                    color = Color(0xFF6EFF8F), fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private data class PosSpec(val baseX: Float, val baseY: Float, val translateX: Float, val translateY: Float, val alignEnd: Boolean)

@Composable
private fun SettingsOption(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

// .game-info-box: width:66px — profile pic (44dp circle) + name label. Naam/DP ab
// server se aaye asal profile se aate hain (BackendClient ke "matched"/"opponentProfile"
// se) — avatarUrl null ho to DEFAULT_AVATAR_IMG fallback hoti hai.
@Composable
private fun PlayerProfileBox(
    name: String,
    avatarUrl: String?,
    color: LudoColor,
    rank: Int?,
    showTimerRing: Boolean,
    timerSeconds: Int,
    timerKey: Int,
    hasGoldenDice: Boolean = false
) {
    Column(
        modifier = Modifier.width(66.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            // 12-second countdown ring — bekend ke "turnTimer" event se shuru hoti
            // hai; ring poori tarah khaali (0%) hone tak progress karti hai, taake
            // player ko saaf pata chale ke kitna waqt bacha hai roll/move karne ke
            // liye. timerKey badalte hi (naya turn/naya timer) animation fresh se
            // shuru ho jati hai (key() wrapper isi ke liye hai).
            if (showTimerRing) {
                key(timerKey) {
                    val progress = remember { Animatable(1f) }
                    LaunchedEffect(timerKey) {
                        progress.snapTo(1f)
                        progress.animateTo(0f, animationSpec = tween(timerSeconds * 1000, easing = LinearEasing))
                    }
                    Canvas(modifier = Modifier.size(50.dp)) {
                        val stroke = 3.dp.toPx()
                        drawArc(
                            color = Color(0xFF2ECC40),
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(avatarUrl ?: DEFAULT_AVATAR_IMG)
                    .crossfade(250)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .background(ludoColorOf(color))
            )
            if (rank != null) {
                AsyncImage(
                    model = RANK_BADGE_IMG[rank],
                    contentDescription = "rank $rank",
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-6).dp)
                )
            }
            // Is player ne is match mein Golden Dice uthaya hai — chhota badge
            // ID/profile ke gird, taake sabko pata chale kisne "golden dice"
            // liya hai. Udaan wali animation khatam hone ke theek baad yeh
            // yahan namudar hota hai aur poori match ke dauran laga rehta hai.
            if (hasGoldenDice) {
                AsyncImage(
                    model = MAGIC_DICE_ICON,
                    contentDescription = "golden dice",
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-2).dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .padding(1.dp)
                )
            }
        }
        Text(
            name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 5.dp)
                .width(66.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// Current-turn indicator — dice-box ke bilkul oopar ek green bouncing arrow (neeche
// ki taraf ishara karta hua), jis se pata chalta hai ke abhi kis ki bari hai. Asal
// HTML ke turn-highlight jaisa hi concept, sirf yahan ek simple bouncing glyph hai.
// BoxScope receiver zaroori hai kyunke Modifier.align() sirf Box ke andar hi milta hai.
@Composable
private fun BoxScope.TurnArrow() {
    val infiniteTransition = rememberInfiniteTransition(label = "turnArrow")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "turnArrowBounce"
    )
    Text(
        "\u25BC",
        color = Color(0xFF2ECC40),
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-22).dp - (4.dp * bounce))
    )
}

// .game-dice-box: 50x50, rgba white 0.12 bg, 10dp rounded corners
@Composable
private fun PlayerDiceBox(diceValue: Int, rolling: Boolean, isClickable: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .then(if (isClickable) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Asal HTML ke ".game-dice-box img.rolling { transform:scale(1.5); }" jaisa hi —
        // rolling ke dauran dice image 1.5x badi dikhti hai
        AsyncImage(
            model = if (rolling) DICE_ROLL_GIF else DICE_FACE_IMG[diceValue],
            contentDescription = "dice",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = if (rolling) 1.5f else 1f, scaleY = if (rolling) 1.5f else 1f)
        )
    }
}
