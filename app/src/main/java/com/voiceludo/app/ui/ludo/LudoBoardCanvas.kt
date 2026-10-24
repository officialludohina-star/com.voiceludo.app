package com.voiceludo.app.ui.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Asal HTML jaisa hi board — wohi Checkerboard-duel background image, aur usi ke
// upar har token apni asal PNG/WEBP image (piece-green/yellow/blue/red) ke sath
// theek 15x15 grid ke row/col hisaab se position hota hai.
//
// NOTE (HTML se hoobahoo match): asal HTML mein board artwork ke 15x15 playing-grid
// ke chaaron taraf 1% ka margin hai (window.BOARD_INSET_X/Y = 1), yani grid poore
// board ke 100% mein nahi balke beech wale 98% mein fit hoti hai (window.BOARD_SPAN_X/Y
// = 98, LUDO_CELL_X/Y = 98/15). Isi liye neeche bhi wahi inset + cell-size formula
// use ho raha hai — warna tokens board artwork ki asal cell-lines se thora hat kar
// (edge ki taraf drift karke) dikhtay.
private const val BOARD_INSET_PCT = 1f
private const val BOARD_SPAN_PCT = 100f - (BOARD_INSET_PCT * 2f)
private const val CELL_PCT = BOARD_SPAN_PCT / 15f // ek cell, board ka % (HTML ke LUDO_CELL_X/Y jaisa)

// Yard/base mein khare token ki size — cell-size ka 115%, HTML ke YARD_TOKEN_SIZE=115 se hoobahoo
private const val YARD_TOKEN_SIZE_PCT = 115f
// Yard slot ka flat +dx/+dy adjustment (board %), HTML ke YARD_SOCKET_ADJUST.slots se hoobahoo
// (chaaron slots ka adjustment barabar hai: dx=3.0, dy=3.0)
private const val YARD_ADJUST = 3f

// Path (ring/stretch) par jitne tokens ek hi cell mein stacked hon, unke hisaab se size aur
// offset — HTML ke window.STACK_CONFIG se hoobahoo (1 token=100% size center mein, 2=88% thora
// upar-neeche, waghera, taake ek cell mein kai tokens ek dusray ko poora chupayen nahi).
private data class StackCfg(val sizePct: Float, val offsets: List<Pair<Float, Float>>)

private val STACK_CONFIG: Map<Int, StackCfg> = mapOf(
    1 to StackCfg(100f, listOf(50f to 50f)),
    2 to StackCfg(88f, listOf(40f to 40f, 60f to 60f)),
    3 to StackCfg(80f, listOf(36f to 36f, 50f to 50f, 64f to 64f)),
    4 to StackCfg(74f, listOf(34f to 34f, 46f to 46f, 58f to 58f, 70f to 70f))
)

// Home-yard covers — board artwork ke andar bake hue 4 tokens ko hide karte hain,
// asal HTML ke .home-cover left%/top% values se hoobahoo (35% x 35%, har color ka apna)
private data class HomeCover(val leftPct: Float, val topPct: Float, val color: Color)

private val HOME_COVERS = listOf(
    HomeCover(2.4f, 2.4f, Color(0xFF1C883C)),   // green
    HomeCover(62.6f, 2.4f, Color(0xFFC39615)),  // yellow
    HomeCover(62.6f, 62.6f, Color(0xFF1C6BBA)), // blue
    HomeCover(2.4f, 62.6f, Color(0xFFB33123))   // red
)

// Arrow mode ke 4 curved + 4 center-diagonal arrow overlays — asal HTML ke
// left%/top%/width%/rotate() values se hoobahoo liye gaye hain
data class ArrowSpot(val leftPct: Float, val topPct: Float, val widthPct: Float, val rotateDeg: Float)

private const val ARROW_CURVED_ICON = "file:///android_asset/img/file-00000000dc8082118a379ac2ac711ac3.png"

// Final tuned values (locked in from the tuning panel — panel is removed now)
private val CURVED_ARROW_SPOTS = listOf(
    ArrowSpot(36.5f, -1.0f, 19.5f, 0f),
    ArrowSpot(81.0f, 36.0f, 20.0f, 89f),
    ArrowSpot(44.0f, 81.0f, 20.0f, 180f),
    ArrowSpot(-1.5f, 43.5f, 21.5f, 270f)
)
private val CENTER_ARROW_SPOTS = listOf(
    ArrowSpot(33.0f, 33.0f, 14.0f, 267f),
    ArrowSpot(53.0f, 33.0f, 14.0f, 4f),
    ArrowSpot(52.0f, 53.0f, 15.0f, 90f),
    ArrowSpot(32.0f, 52.0f, 15.0f, 180f)
)

// Quick/Master mode ke 4 block-cell icons (panel se test kiya hua final: size +8,
// left/right -4, up/down 0 — ab permanent bake, panel hata diya gaya hai)
private val BLOCK_ICON_SPOTS = listOf(
    ArrowSpot(38.6f, -1f, 23f, 0f),
    ArrowSpot(-1f, 39.6f, 22f, 271f),
    ArrowSpot(78.2f, 38.6f, 23f, 89f),
    ArrowSpot(38.6f, 79.2f, 23f, 0f)
)

// Ek path token (ring ya stretch par) ka poora record — render se pehle group/stack
// karne ke liye zaroori sari info isi mein.
private data class PathToken(
    val color: LudoColor,
    val idx: Int,
    val row: Int,
    val col: Int,
    val cellKey: String
)

// Asal HTML ke window.MOVABLE_GLOW jaisa hi — glow ring hamesha yellow nahi, balke
// jis token ki bari hai uske apne color mein glow karta hai (green/yellow/blue/red)
private val MOVABLE_GLOW: Map<LudoColor, Pair<Color, Color>> = mapOf(
    LudoColor.GREEN to (Color(0xFF2FBF5A) to Color(0xD92FBF5A)),
    LudoColor.YELLOW to (Color(0xFFFFD400) to Color(0xD9FFD400)),
    LudoColor.BLUE to (Color(0xFF2A8AFF) to Color(0xD92A8AFF)),
    LudoColor.RED to (Color(0xFFFF4A4A) to Color(0xD9FF4A4A))
)

// Asal HTML ke .game-token.movable jaisa hi — jis token ki bari ho (movable) uske
// gird ek pulsing glow-ring dikhta hai (0.7s infinite pulse, scale 1 -> 1.18), taake
// player ko turant pata chale ke ye token abhi chala sakta hai. Modifier.shadow() se
// pehle bharosemand nahi tha (kai devices par colored halo dikhta hi nahi tha), isliye
// ab yeh khud ek animated circle draw karta hai — hamesha reliably dikhta hai.
@Composable
private fun BoxTokenWithGlow(
    tokenSizeDp: Dp,
    animX: Dp,
    animY: Dp,
    isMovable: Boolean,
    isCurrentTurn: Boolean,
    tokenColor: LudoColor,
    imageUrl: String?,
    contentDescription: String,
    onTap: () -> Unit,
    popScale: Float = 1f,
    popAlpha: Float = 1f,
    isRocketFlying: Boolean = false,
    rocketAngle: Float = 0f,
    fxMoving: Boolean = false,
    fxLandTick: Int = 0,
    fxKilled: Boolean = false,
    // Stack mein kai movable token hon to sirf sab se upar wala glow kare
    showGlow: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tokenGlow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "glowScale"
    )
    // Halke se ring ki apni alag, thori dheemi pulse — dono glow layers ek sath
    // exactly sync mein na dhalke thora "living" feel dete hain (asal HTML ke
    // CSS keyframe glow jaisa hi, jahan do shadows alag-alag speed se pulse karte thay).
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ringAlpha"
    )
    // Movable token khud bhi thora upar-neeche "bounce" karta hai (glow ke sath sath),
    // taake turn ke waqt chalne wala token aur bhi zyada nazar aaye. Bounce sirf
    // isMovable hote hue lagta hai — warna token apni fixed jagah par hi rehta hai.
    val bounceT by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "tokenBounce"
    )
    val bounceDy = if (isMovable) -(tokenSizeDp * 0.16f) * bounceT else 0.dp

    // Flame/fire trail — sirf rocket-flight ke dauran, token ke peeche 3
    // dhundhle, ghatte size ke orange-red "shoalay" jo pulse karte hain,
    // taake lage ke waqai rocket ki tarah udh raha hai.
    val flamePulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
        label = "flamePulse"
    )

    // Landing flash — rocket abhi abhi land hui ho (isRocketFlying true se
    // false hui ho) to token ki jagah ek chhota safed "sparkle burst" flash
    // hota hai, jaisa reference video mein tha.
    val landingFlash = remember { Animatable(0f) }
    var wasRocketFlying by remember { mutableStateOf(false) }
    LaunchedEffect(isRocketFlying) {
        if (!isRocketFlying && wasRocketFlying) {
            landingFlash.snapTo(1f)
            landingFlash.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
        wasRocketFlying = isRocketFlying
    }

    // Panel (Game tab) ka "Token ka size %" — token apne center se chhota-bada hota hai
    val tokenScale = GameTuning.tokenScalePct / 100f

    // ---- Token animation FX (panel Game tab > "Token animation"): "none" par kuch nahi badalta ----
    val fxColor = GameTuning.fxColor
    val moveFx = GameTuning.moveFx
    val landFx = GameTuning.landFx
    val killFx = GameTuning.killFx
    val landProg = remember { Animatable(1f) }
    LaunchedEffect(fxLandTick) {
        if (fxLandTick > 0 && landFx != "none") {
            landProg.snapTo(0f)
            landProg.animateTo(1f, tween(GameTuning.fxMs, easing = FastOutSlowInEasing))
        }
    }
    val killProg = remember { Animatable(0f) }
    LaunchedEffect(fxKilled) {
        if (fxKilled && killFx != "none") {
            killProg.snapTo(0f)
            killProg.animateTo(1f, tween(GameTuning.killMs.coerceAtLeast(200), easing = FastOutSlowInEasing))
        } else {
            killProg.snapTo(0f)
        }
    }
    val lp = landProg.value
    val kp = killProg.value
    val fxPulse = if (fxMoving && moveFx == "pulse") 1f + (glowScale - 1f) * 0.45f else 1f
    val fxLandScale = if (landFx == "pop") 1f + 0.4f * (1f - lp) else 1f
    val fxLandDy = if (landFx == "bounce") tokenSizeDp * (-0.7f * kotlin.math.sin(Math.PI.toFloat() * lp)) else 0.dp
    val fxKillScale = when (killFx) { "shrink" -> 1f - 0.85f * kp; "spin" -> 1f - 0.4f * kp; else -> 1f }
    val fxKillAlpha = if (killFx == "fade") 1f - kp else 1f
    val fxKillRot = when (killFx) {
        "shake" -> kotlin.math.sin(kp * 8f * Math.PI.toFloat()) * 18f * (1f - kp * 0.3f)
        "spin" -> 720f * kp
        else -> 0f
    }

    Box(
        modifier = Modifier
            .size(tokenSizeDp * 1.9f)
            .offset(
                x = animX - (tokenSizeDp * 0.45f),
                y = animY + bounceDy + fxLandDy - (tokenSizeDp * 0.45f)
            )
            .graphicsLayer {
                scaleX = popScale * tokenScale * fxPulse * fxLandScale * fxKillScale
                scaleY = popScale * tokenScale * fxPulse * fxLandScale * fxKillScale
                alpha = popAlpha * fxKillAlpha
                rotationZ = fxKillRot
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRocketFlying) {
            val flameColors = listOf(
                Color(0xFFFFD54F) to 0.55f,
                Color(0xFFFF7043) to 0.75f,
                Color(0xFFE64A19) to 0.95f
            )
            flameColors.forEachIndexed { i, (flameColor, sizeFrac) ->
                Box(
                    modifier = Modifier
                        .size(tokenSizeDp * sizeFrac)
                        .offset(
                            y = tokenSizeDp * (0.32f + i * 0.14f) * flamePulse,
                            x = tokenSizeDp * 0.05f * (if (i % 2 == 0) 1f else -1f)
                        )
                        .graphicsLayer { scaleX = flamePulse; scaleY = flamePulse; alpha = 0.85f - i * 0.2f }
                        .background(
                            Brush.radialGradient(listOf(flameColor, flameColor.copy(alpha = 0f))),
                            CircleShape
                        )
                )
            }
        }

        if (landingFlash.value > 0f) {
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * (1f + landingFlash.value * 1.2f))
                    .graphicsLayer { alpha = landingFlash.value }
                    .background(
                        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f))),
                        CircleShape
                    )
            )
        }
        // Token chalte waqt uske gird animation (glow / ring) aur ruk jane par ripple
        if (fxMoving && moveFx == "glow") {
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * 1.7f)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(Brush.radialGradient(listOf(fxColor.copy(alpha = 0.85f), fxColor.copy(alpha = 0f))), CircleShape)
            )
        }
        if (fxMoving && moveFx == "ring") {
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * 1.55f)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha + 0.3f }
                    .border(2.dp, fxColor, CircleShape)
            )
        }
        if (landFx == "ripple" && lp < 1f) {
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * (1f + 1.4f * lp))
                    .graphicsLayer { alpha = 1f - lp }
                    .border(2.dp, fxColor, CircleShape)
            )
        }
        if (isMovable) {
          if (showGlow && GameTuning.turnGlowOn) {
            val (baseStrong, baseSoft) = MOVABLE_GLOW.getValue(tokenColor)
            val glowStrong = GameTuning.turnGlowColor ?: baseStrong
            val glowSoft = GameTuning.turnGlowColor?.copy(alpha = 0.85f) ?: baseSoft
            val glowDia = tokenSizeDp * (GameTuning.turnGlowSizePct / 100f)
            // Outer expanding ring — background ke against sabse pehle nazar aata hai
            Box(
                modifier = Modifier
                    .size(glowDia)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .border(2.dp, glowStrong, CircleShape)
            )
            // Andar wala solid glow halo — token ke seedha peeche, saturated aur zyada bright
            Box(
                modifier = Modifier
                    .size(glowDia)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(
                        Brush.radialGradient(
                            listOf(glowStrong, glowSoft.copy(alpha = 0.5f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
          }
        } else if (isCurrentTurn) {
            // Yeh device khud is token ko chala nahi sakta (kisi aur ki bari hai)
            // lekin sabko yeh pata chalna chahiye ke abhi kis ki bari hai — isliye
            // sirf ek simple, static outline ring (token ka apna color, halka bounce
            // ke bagair) — token ki fill/color kabhi nahi badalti, sirf uske gird
            // ek gol daira nazar aata hai.
            val (ringColor, _) = MOVABLE_GLOW.getValue(tokenColor)
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * 1.35f)
                    .border(2.dp, ringColor.copy(alpha = 0.85f), CircleShape)
            )
        }
        if (isRocketFlying) {
            // User ne kaha rocket ka icon khud saaf nazar aana chahiye (sirf
            // flame nahi) — udaan ke poore dauran token ki jagah asal rocket
            // dikhta hai, land hote hi (isRocketFlying false hote hi) normal
            // token wapis nazar aata hai.
            AsyncImage(
                model = MAGIC_ROCKET_ICON,
                contentDescription = "$contentDescription flying",
                modifier = Modifier
                    .size(tokenSizeDp * 1.5f)
                    .graphicsLayer { rotationZ = rocketAngle }
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(tokenSizeDp)
                    .clip(CircleShape)
                    .background(ludoColorOf(tokenColor))
                    // Pehle yahan hamesha ek MOTA white circle border lagta tha (har
                    // token par, hamesha, chahe kisi ki bhi baari ho) — jis se tokens
                    // "gol gol white daba hua" jaisay dikhte thay, khaas kar red/yellow
                    // par zyada nazar aata (jab image load thori slow ho to yeh white
                    // ring akela hi token ban jata). Ab bohot halka/subtle dark outline
                    // hai — sirf itna ke image na load ho to bhi token ghayab na ho,
                    // lekin koi alag "white circle" nazar nahi aati.
                    .border(0.6.dp, Color.Black.copy(alpha = 0.22f), CircleShape)
                    .clickable(enabled = isMovable, onClick = onTap)
            )
        }
    }
}

@Composable
fun LudoBoardCanvas(
    state: LudoGameState,
    // Board screen par itne degree ghumaya hua hai — popup ko seedha (upright) rakhne ke liye
    uprightDeg: Float = 0f,
    onTokenTap: (LudoColor, Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val boardSizeDp = maxWidth
        // Board ka 1 cell — HTML jaisa hi 1% inset ke sath (poore board ka 15waan hissa nahi)
        val cellDp: Dp = boardSizeDp * (CELL_PCT / 100f)
        // Har token ka board-par center ("COLOR_idx" -> x,y) — number-chunne wala popup isi par khulta hai
        val tokenCenters = HashMap<String, Pair<Dp, Dp>>()

        AsyncImage(
            model = GAME_BOARD_IMG,
            contentDescription = "board",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFE8D9B5)) // board image load na ho to bhi khaali kaali jagah na dikhe
        )

        // Board artwork ke andar bake hue home-yard tokens ko hide karte hain (z-index:1,
        // tokens se neeche) — asal HTML ke .home-cover se hoobahoo
        HOME_COVERS.forEach { hc ->
            val coverSize = boardSizeDp * 0.35f
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .offset(
                        x = boardSizeDp * (hc.leftPct / 100f),
                        y = boardSizeDp * (hc.topPct / 100f)
                    )
                    .background(hc.color, CircleShape)
            )
        }

        // Asal HTML ke .yard-socket jaisa hoobahoo — har yard slot (chahe token ho
        // ya khaali) ke neeche ek "dabba" (sunken/pressed-in gol chhed) dikhta hai:
        // andar se halka andhera aur ek inner-shadow jaisa ring, taake lagay ke
        // token ek gol khanay ke andar tika hua hai. Compose mein asal inset-shadow
        // nahi hoti isliye radial-gradient se woh hi "dab" wala look banaya hai.
        // NOTE: sockets hamesha sab 4 gharon mein dikhte hain (2P mode mein bhi) —
        // chahe woh color is match mein use ho ya na ho. Asal HTML ke renderYardSockets()
        // jaisa hoobahoo: sirf tokens (asal gotiyan) unused colors ke liye gayab hoti
        // hain, dabbe (sockets) hamesha sab 4 colors ke liye render hote hain.
        LudoColor.entries.forEach { color ->
            val density = LocalDensity.current
            COLOR_META.getValue(color).yard.forEach { (yx, yy) ->
                val leftPct = BOARD_INSET_PCT + (yx / 100f) * BOARD_SPAN_PCT + YARD_ADJUST
                val topPct = BOARD_INSET_PCT + (yy / 100f) * BOARD_SPAN_PCT + YARD_ADJUST
                val socketSizeDp = boardSizeDp * 0.065f
                val centerX = boardSizeDp * (leftPct / 100f)
                val centerY = boardSizeDp * (topPct / 100f)
                Box(
                    modifier = Modifier
                        .size(socketSizeDp)
                        .offset(x = centerX - socketSizeDp / 2, y = centerY - socketSizeDp / 2)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x00000000), Color(0x2E000000), Color(0x59000000)),
                                radius = with(density) { socketSizeDp.toPx() / 2f }
                            ),
                            CircleShape
                        )
                        .border(0.6.dp, Color(0x59000000), CircleShape)
                )
            }
        }

        // Arrow mode: curved + center diagonal arrow overlays — asal HTML ke
        // ".board-arrow { filter: brightness(0) ... }" jaisa hi: arrow image chahe
        // original mein kisi bhi rang ki ho, yahan hamesha solid black silhouette
        // dikhti hai. Position/size/rotation ab fixed final values hain (upar
        // CURVED_ARROW_SPOTS / CENTER_ARROW_SPOTS). NOTE: .shadow() jaan-boojh
        // kar nahi lagaya — Compose ka shadow hamesha ek seedha (un-rotated)
        // rectangle box banata hai jo rotated arrows ke peeche ek badsurat white
        // box jaisa dikhta hai.
        if (state.mode == LudoMode.ARROW) {
            (CURVED_ARROW_SPOTS.map { it to ARROW_CURVED_ICON } + CENTER_ARROW_SPOTS.map { it to ARROW_CENTER_ICON })
                .forEach { (spot, icon) ->
                    val iconSize = boardSizeDp * (spot.widthPct / 100f)
                    AsyncImage(
                        model = icon,
                        contentDescription = "arrow",
                        colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
                        modifier = Modifier
                            .size(iconSize)
                            .offset(
                                x = boardSizeDp * (spot.leftPct / 100f),
                                y = boardSizeDp * (spot.topPct / 100f)
                            )
                            .graphicsLayer(rotationZ = spot.rotateDeg)
                    )
                }
        }

        // Quick/Master mode: block-cell icons
        if (state.mode == LudoMode.QUICK || state.mode == LudoMode.MASTER) {
            BLOCK_ICON_SPOTS.forEach { spot ->
                val iconSize = boardSizeDp * (spot.widthPct / 100f)
                AsyncImage(
                    model = QUICK_BLOCK_ICON,
                    contentDescription = "block",
                    modifier = Modifier
                        .size(iconSize)
                        .offset(
                            x = boardSizeDp * (spot.leftPct / 100f),
                            y = boardSizeDp * (spot.topPct / 100f)
                        )
                        .graphicsLayer(rotationZ = spot.rotateDeg)
                )
            }
        }

        // Magic mode: golden-dice aur rocket bonus cells (asal HTML ke #magicIconsLayer se
        // hoobahoo — .magic-icon { width:6%; height:6% }, cell ke exact center par)
        if (state.magicOn) {
            fun magicCenterOffsets(g: Int, iconSizeDp: Dp): Pair<Dp, Dp> {
                val rc = RING[g]
                val cellLeftDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * rc.col
                val cellTopDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * rc.row
                val centerX = cellLeftDp + cellDp / 2
                val centerY = cellTopDp + cellDp / 2
                return (centerX - iconSizeDp / 2) to (centerY - iconSizeDp / 2)
            }
            val magicIconSizeDp = boardSizeDp * 0.06f
            state.magicDiceCells.forEach { g ->
                val (x, y) = magicCenterOffsets(g, magicIconSizeDp)
                AsyncImage(
                    model = MAGIC_DICE_ICON,
                    contentDescription = "magic dice",
                    modifier = Modifier.size(magicIconSizeDp).offset(x = x, y = y)
                )
            }
            state.magicRocketCells.forEach { g ->
                val (x, y) = magicCenterOffsets(g, magicIconSizeDp)
                AsyncImage(
                    model = MAGIC_ROCKET_ICON,
                    contentDescription = "magic rocket",
                    modifier = Modifier.size(magicIconSizeDp).offset(x = x, y = y)
                )
            }
        }

        // ---- Pehla pass: sab tokens ko yard vs path mein baant kar, path walon ko
        // cell-key (HTML ke 'r'+g / color+'s'+pos jaisa) se group kar lete hain, taake
        // ek hi cell mein kitne tokens hain uske hisaab se STACK_CONFIG lagay (HTML jaisa).
        val pathGroups = LinkedHashMap<String, MutableList<PathToken>>()

        state.players.forEach { color ->
            state.tokens.getValue(color).forEachIndexed { i, pos ->
                if (pos in 0..56) {
                    val (rc, key) = when {
                        pos in 0..50 -> {
                            val g = (COLOR_META.getValue(color).start + pos) % 52
                            RING[g] to "r$g"
                        }
                        else -> {
                            // 51..55 = stretch cells, 56 = finish -> HTML jaisa stretch[5] par hi rehta hai
                            val stretchIdx = (pos - 51).coerceIn(0, 5)
                            COLOR_META.getValue(color).stretch[stretchIdx] to "${color}s$pos"
                        }
                    }
                    pathGroups.getOrPut(key) { mutableListOf() }.add(PathToken(color, i, rc.row, rc.col, key))
                }
            }
        }

        // BUG FIX: pehle ek cell ke andar tokens ka stack-slot unke "group mein
        // current index (n)" se milta tha — matlab jab koi ek token us cell se
        // nikalta (ya koi naya aata) to baaki sab tokens ka index shift ho jata
        // aur wo bhi (bina khud chale) idhar-udhar "hil" jate the. Ab har token
        // (color+idx) ko is cell ke andar ek sthiir/permanent slot milta hai jo
        // sirf tab tak rehta hai jab tak wo token khud us cell mein hai — kisi
        // aur token ke aane/jaane se yeh slot kabhi nahi badalta.
        val cellSlotMemory = remember { mutableMapOf<String, MutableMap<String, Int>>() }
        // Jo cell-keys ab khaali ho chuki hain unki memory hata dete hain (leak na ho).
        cellSlotMemory.keys.retainAll(pathGroups.keys)
        val slotOf = HashMap<String, Int>() // "$key|$color|$idx" -> slot
        pathGroups.forEach { (key, group) ->
            val slots = cellSlotMemory.getOrPut(key) { mutableMapOf() }
            val idsNow = group.map { "${it.color}_${it.idx}" }.toSet()
            slots.keys.retainAll(idsNow) // is cell se nikal chuke tokens ki slot chhor do
            group.forEach { t ->
                val id = "${t.color}_${t.idx}"
                val slot = slots.getOrPut(id) {
                    // Sabse chhota free slot (0..3) dhoondo jo abhi kisi aur ke paas na ho.
                    val used = slots.values.toSet()
                    (0..3).first { it !in used }
                }
                slotOf["$key|$id"] = slot
            }
        }

        // ---- Path/stretch tokens: HTML jaisa hi per-cell wrapper + stack offsets
        //
        // BUG FIX: pehle yeh ek NESTED forEach tha — `pathGroups.values.forEach { group ->
        // group.forEach { t -> key(t.color, t.idx) { ... } } }`. Jab koi token
        // apna cell badalta (jaise arrow-jump se cell 4 se seedha cell 9 par), wo
        // ek group ki list se hat kar doosre group ki list mein chala jata — aur
        // Compose ke liye yeh cell-group ka forEach hi token ki composable
        // "jagah" (position in tree) tha. Isliye purana composable (apni chalti
        // hui animateDpAsState animation ke sath) DISPOSE ho jata aur nayi cell
        // ke liye ek BILKUL NAYA composable bin kisi animation ke seedha final
        // position par mount hota — yehi wajah thi token ke "gayab ho kar doosri
        // jagah nikal aane" ki. Ab sab path-tokens ko ek hi FLAT list mein
        // (cell-grouping se bahar) render karte hain, taake `key(color, idx)`
        // hamesha ek hi, sthiir jagah par rahe chahe token kisi bhi cell mein ho
        // — isi se animateDpAsState purani se nayi position tak sahi se glide
        // kar pata hai, cell badalne par bhi.
        // Jis player ki bari hai (aur jo yeh device chala raha hai) uske token stack mein sab se
        // upar draw hote hain (movable sab se aakhir mein) — taake ek cell mein 2-3 token hon to
        // ek token upar nazar aaye aur wahi glow kare. sortedBy stable hai, baaki ka order wahi rehta hai.
        val orderedPathTokens = pathGroups.values.flatten().let { all ->
            if (!GameTuning.stackTopOn) all else all.sortedBy { x ->
                val mine = x.color == state.currentColor && x.color == state.myColor
                when {
                    mine && x.idx in state.movable -> 2
                    mine -> 1
                    else -> 0
                }
            }
        }
        // Har cell ka sab se upar wala movable token (glow sirf usi par)
        val topMovableOfCell = HashMap<String, String>()
        orderedPathTokens.forEach { x ->
            if (x.color == state.currentColor && x.color == state.myColor &&
                !state.isMoving.value && x.idx in state.movable
            ) topMovableOfCell[x.cellKey] = "${x.color}_${x.idx}"
        }
        orderedPathTokens.forEach { t ->
            val group = pathGroups.getValue(t.cellKey)
            val total = group.size.coerceAtMost(4)
            val cfg = if (total <= 1) STACK_CONFIG.getValue(1) else STACK_CONFIG.getValue(4)
            val stackSizePct = GameTuning.stackSizePct(total)
            val spread = if (total <= 1) 1f else GameTuning.stackSpreadPct / 100f
            val cellLeftDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * t.col
            val cellTopDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * t.row

            run {
                val id = "${t.color}_${t.idx}"
                val n = slotOf["${t.cellKey}|$id"] ?: 0
                val off = cfg.offsets[n % cfg.offsets.size]
                val rawTokenSizeDp = cellDp * (stackSizePct / 100f)
                val centerX = cellLeftDp + cellDp * ((50f + (off.first - 50f) * spread) / 100f)
                val centerY = cellTopDp + cellDp * ((50f + (off.second - 50f) * spread) / 100f)
                val targetX = centerX - rawTokenSizeDp / 2
                val targetY = centerY - rawTokenSizeDp / 2
                tokenCenters["${t.color}_${t.idx}"] = centerX to centerY

                val isMovable = t.color == state.currentColor &&
                    state.currentColor == state.myColor &&
                    !state.isMoving.value &&
                    t.idx in state.movable
                // Pehle yeh ring sirf tab nazar aati thi jab dice roll ho chuka ho
                // aur movable set aa chuki ho. User ne kaha jaise hi kisi ki baari
                // aaye turn ke shuru se hi (roll karne se pehle bhi) uski token ke
                // gird uske apne ghar/color wali ring nazar aani chahiye — sirf usi
                // player ko (dusron ko nahi), poori baari ke dauran.
                val isCurrentTurn = t.color == state.currentColor && t.color == state.myColor && !state.isMoving.value

                // key(color, idx) — token ki asal "identity" (color+idx) se bandha hua
                // animation-state, iske bagair jab tokens yard se nikalte ya kisi cell
                // mein group/count badalta (stack change) to Compose galat slot ka
                // purana animateDpAsState reuse kar leta tha, jis se token ki position
                // baar-baar idhar-udhar "jump/change" hoti dikhti thi.
                key(t.color, t.idx) {
                    // Normal step-by-step move ke liye chhota, saaf tween.
                    // Arrow-jump wale token ke liye alag se ek lamba,
                    // mukammal continuous glide-tween use hota hai (neeche
                    // dekhein) taake beech mein kahin flicker/disappear
                    // na ho.
                    val id = "${t.color}_${t.idx}"
                    val isGliding = state.arrowGlidingToken.value == id
                    val isRocketFlying = state.rocketFlyingToken.value == id

                    // Rocket ka icon udaan ki ASAL SEEDHI (diagonal) direction
                    // ke mutabiq ghoomta hai (na ke hamesha fixed 45°) — jaisa
                    // reference video mein tha: rocket seedha apni manzil ki
                    // taraf, seedhi line mein, sahi angle ke sath udta hai.
                    // lastRestX/Y hamesha "ruki hui" (non-flying) position yaad
                    // rakhte hain, taake udaan shuru hote hi ek dafa (remember
                    // key = isRocketFlying) shuru-se-aakhir tak ka angle nikala
                    // ja sake.
                    val lastRestX = remember { mutableStateOf(targetX) }
                    val lastRestY = remember { mutableStateOf(targetY) }
                    if (!isRocketFlying) {
                        lastRestX.value = targetX
                        lastRestY.value = targetY
                    }
                    val rocketAngle = remember(isRocketFlying) {
                        if (isRocketFlying) {
                            val dx = (targetX - lastRestX.value).value
                            val dy = (targetY - lastRestY.value).value
                            // +90: MAGIC_ROCKET_ICON ka asal artwork nose-up hai,
                            // atan2 convention mein 0deg = seedha right — is liye
                            // 90 add karke "up" ko "direction of travel" se align
                            // karte hain.
                            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                        } else 0f
                    }
                    val posTweenMs = when {
                        isRocketFlying -> GameTuning.rocketMs
                        isGliding -> GameTuning.arrowMs
                        else -> GameTuning.tokenGlideMs
                    }
                    val animX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = tween(posTweenMs, easing = GameTuning.tokenEase),
                        label = "tokenX"
                    )
                    val animY by animateDpAsState(
                        targetValue = targetY,
                        animationSpec = tween(posTweenMs, easing = GameTuning.tokenEase),
                        label = "tokenY"
                    )
                    // BUG FIX: pehle jab kisi cell mein token aata/jata (jaise
                    // koi token guzarte hue thodi der ke liye usi cell par
                    // aa jaye) to us cell ke sab tokens ka size (1-token wala
                    // bada layout <-> 2+ tokens wala chhota quadrant layout)
                    // ACHANAK badal jata — "choti-bari" hoti dikhti thi. Ab
                    // size bhi animate hoti hai, taake yeh tabdeeli hamesha
                    // smooth ho, achanak jhatka na lage.
                    val animSize by animateDpAsState(
                        targetValue = rawTokenSizeDp,
                        animationSpec = tween(GameTuning.tokenSizeMs, easing = FastOutSlowInEasing),
                        label = "tokenSize"
                    )
                    // Rocket udaan ke dauran token seedha line mein nahi, balke
                    // thora upar uth kar (jaise waqai udh raha ho) ek arc mein
                    // jata hai — beech mein upar, shuru/aakhir mein zameen ke
                    // barabar.
                    val flightLift = remember { Animatable(0f) }
                    LaunchedEffect(isRocketFlying) {
                        if (isRocketFlying) {
                            val d = GameTuning.rocketMs
                            val lift = -GameTuning.rocketLift.toFloat()
                            flightLift.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = d
                                    0f at 0
                                    lift at (d / 2)
                                    0f at d
                                }
                            )
                        } else {
                            flightLift.snapTo(0f)
                        }
                    }
                    val hopDy = flightLift.value.dp

                    // Yard se path mein pehli dafa nikalne par (poppingToken match
                    // hote hi) chhota sa "pop" (fade-in + scale-up) animation —
                    // taake token achanak (teleport jaisa) nazar aane ke bajaye
                    // saaf tarah "namudar" hota dikhe.
                    val isPopping = state.poppingToken.value == id
                    val popScale = remember { Animatable(1f) }
                    val popAlpha = remember { Animatable(1f) }
                    LaunchedEffect(isPopping) {
                        if (isPopping) {
                            popScale.snapTo(0.2f)
                            popAlpha.snapTo(0.2f)
                            try {
                                // User ne kaha token yard se nikalte hi FORAN nazar aana
                                // chahiye — pehle scale+alpha ek ke baad ek (sequentially,
                                // total ~440ms) chalte thay. Ab dono EK SATH (parallel)
                                // aur bohot kam waqt (100ms) mein poore hote hain — token
                                // teleport jaisa achanak nahi, lekin lagbhag turant nazar
                                // aata hai.
                                coroutineScope {
                                    launch { popScale.animateTo(1f, tween(GameTuning.tokenPopMs, easing = FastOutSlowInEasing)) }
                                    launch { popAlpha.animateTo(1f, tween(GameTuning.tokenPopMs * 8 / 10, easing = FastOutSlowInEasing)) }
                                }
                            } finally {
                                // SAFETY NET: agar yeh coroutine beech mein hi cancel ho
                                // jaye (Compose recomposition/key-churn ki wajah se), to
                                // bhi token hamesha poori tarah nazar aane wali (scale=1,
                                // alpha=1) state par wapis aa jaye. NonCancellable zaroori
                                // hai — warna cancellation ke baad koi bhi suspend call
                                // (snapTo bhi) turant dobara cancel ho kar KUCH NAHI karta,
                                // aur token hamesha ke liye halka/ghost jaisa (jaisa
                                // screenshot mein dikha) reh jata — yehi asal bug tha.
                                withContext(NonCancellable) {
                                    popScale.snapTo(1f)
                                    popAlpha.snapTo(1f)
                                }
                            }
                        }
                    }

                    BoxTokenWithGlow(
                        popScale = popScale.value,
                        popAlpha = popAlpha.value,
                        tokenSizeDp = animSize,
                        animX = animX,
                        animY = animY + hopDy,
                        isMovable = isMovable,
                        showGlow = total <= 1 || topMovableOfCell[t.cellKey] == "${t.color}_${t.idx}",
                        isCurrentTurn = isCurrentTurn,
                        isRocketFlying = isRocketFlying,
                        rocketAngle = rocketAngle,
                        fxMoving = state.movingTokenKey.value == id,
                        fxLandTick = if (state.landedTokenKey.value == id) state.landedTick.value else 0,
                        fxKilled = state.killedFlashPos.value != null &&
                            t.color.name in state.killedColors.value &&
                            state.globalCellOf(t.color, state.tokens.getValue(t.color)[t.idx]) == state.killedFlashPos.value,
                        tokenColor = t.color,
                        imageUrl = TOKEN_IMG[t.color],
                        contentDescription = "${t.color} token ${t.idx}",
                        onTap = { onTokenTap(t.color, t.idx) }
                    )
                }
            }
        }

        // ---- Yard (base) tokens: HTML jaisa hi cell-size ka 115%, yard-socket +3%/+3% adjustment ke sath
        state.players.forEach { color ->
            val list = state.tokens.getValue(color)
            list.forEachIndexed { i, pos ->
                if (pos != -1) return@forEachIndexed
                val (yx, yy) = COLOR_META.getValue(color).yard[i]
                val leftPct = BOARD_INSET_PCT + (yx / 100f) * BOARD_SPAN_PCT + YARD_ADJUST
                val topPct = BOARD_INSET_PCT + (yy / 100f) * BOARD_SPAN_PCT + YARD_ADJUST
                val tokenSizeDp = cellDp * (YARD_TOKEN_SIZE_PCT / 100f)
                val centerX = boardSizeDp * (leftPct / 100f)
                val centerY = boardSizeDp * (topPct / 100f)
                val targetX = centerX - tokenSizeDp / 2
                val targetY = centerY - tokenSizeDp / 2
                tokenCenters["${color}_$i"] = centerX to centerY

                val isMovable = color == state.currentColor &&
                    state.currentColor == state.myColor &&
                    !state.isMoving.value &&
                    i in state.movable
                // BUG FIX: pehle yard tokens ka "current turn" ring sirf isMovable
                // ke barabar tha — matlab yard mein baithe token par glow sirf
                // tabhi aati jab wahi token khud is waqt chal sakta ho (jaise 6
                // aaya ho). Path/board wale tokens ka ring is se alag, behtar
                // tarike se kaam karta tha: jis ki bhi bari ho uske SAARE tokens
                // par ring aati (chahe wo abhi movable ho ya na ho). Ab yard
                // tokens ko bhi wahi consistent rule di hai.
                //
                // PRIVACY + TIMING: yeh ring sirf apne hi tokens par, sirf apni
                // baari mein — ab poori baari ke dauran (roll se pehle bhi)
                // nazar aati hai, taake "meri baari hai" turn shuru hote hi
                // pata chal jaye.
                val isCurrentTurnYard = color == state.currentColor && color == state.myColor && !state.isMoving.value

                key(color, i) {
                    val animX by animateDpAsState(targetValue = targetX, animationSpec = tween(GameTuning.yardMs), label = "yardTokenX")
                    val animY by animateDpAsState(targetValue = targetY, animationSpec = tween(GameTuning.yardMs), label = "yardTokenY")

                    BoxTokenWithGlow(
                        tokenSizeDp = tokenSizeDp,
                        animX = animX,
                        animY = animY,
                        isMovable = isMovable,
                        isCurrentTurn = isCurrentTurnYard,
                        tokenColor = color,
                        imageUrl = TOKEN_IMG[color],
                        contentDescription = "$color token $i",
                        onTap = { onTokenTap(color, i) }
                    )
                }
            }
        }

        // ---- Number chunne ka popup: 2+ alag saved numbers (jaise 6 aur 5) hon aur token par
        // tap ho to poochna usi token ke UPAR hota hai (profile/dice ke paas nahi). Sirf ek number
        // ho to tap par token seedha chal padta hai (LudoGameState.tapToken).
        state.rollChoice.value?.let { choice ->
            val c = tokenCenters["${state.myColor}_${choice.tokenIdx}"]
            if (c != null) {
                // 200dp ka wrapper token ke center par; board ghumne ke ulte rotate kar ke seedha rakha
                Box(
                    modifier = Modifier
                        .offset(x = c.first - 100.dp, y = c.second - 100.dp)
                        .size(200.dp)
                        .graphicsLayer(rotationZ = -uprightDeg)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-40).dp)
                            .background(Color(0xF20A1423), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .border(1.5.dp, Color(0xFFFFCC33), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        choice.options.forEach { opt ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFFFFEC66), Color(0xFFFFB300))))
                                    .clickable { state.chooseRoll(opt) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${opt.value}", color = Color(0xFF5A3100), fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        // ---- Capture flash overlays ----
        // Jab koi token kill kare: happy icon attacker (killer) ke upar 2 sec
        // Jab token kill ho kar ghar jaye: rota hua icon usi cell par 2 sec
        // NOTE: pehle yeh ulta tha (crying killer par, happy killed par) — user
        // ke bataye anusar theek kiya gaya hai.
        val happyIcon = "https://i.postimg.cc/vZTDxWkg/img8-static.png"
        val cryingIcon = "https://i.postimg.cc/T335q8LT/horse-chat.webp"

        listOf(
            state.killerFlashPos.value to happyIcon,
            state.killedFlashPos.value to cryingIcon
        ).forEach { (globalPos, iconUrl) ->
            if (globalPos != null && globalPos in RING.indices) {
                val rc = RING[globalPos]
                val flashLeft = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * rc.col
                val flashTop  = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * rc.row
                Box(
                    modifier = Modifier
                        .offset(x = flashLeft, y = flashTop)
                        .size(cellDp)
                ) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Arrow Tuning Panel removed — final arrow size/position values are now
// locked in directly above (CURVED_ARROW_SPOTS / CENTER_ARROW_SPOTS).
