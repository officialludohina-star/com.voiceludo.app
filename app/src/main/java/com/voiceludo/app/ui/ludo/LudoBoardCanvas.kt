package com.voiceludo.app.ui.ludo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    popAlpha: Float = 1f
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
    Box(
        modifier = Modifier
            .size(tokenSizeDp * 1.9f)
            .offset(
                x = animX - (tokenSizeDp * 0.45f),
                y = animY + bounceDy - (tokenSizeDp * 0.45f)
            )
            .graphicsLayer {
                scaleX = popScale
                scaleY = popScale
                alpha = popAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        if (isMovable) {
            val (glowStrong, glowSoft) = MOVABLE_GLOW.getValue(tokenColor)
            // Outer expanding ring — background ke against sabse pehle nazar aata hai
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * 1.55f)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .border(2.dp, glowStrong, CircleShape)
            )
            // Andar wala solid glow halo — token ke seedha peeche, saturated aur zyada bright
            Box(
                modifier = Modifier
                    .size(tokenSizeDp * 1.55f)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(
                        Brush.radialGradient(
                            listOf(glowStrong, glowSoft.copy(alpha = 0.5f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
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
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(tokenSizeDp)
                .clip(CircleShape)
                .background(ludoColorOf(tokenColor))
                // Safety border: agar kabhi token ki asal image (piece-*.webp)
                // load na ho paye, to token apne hi color ke background wale
                // cell mein bilkul blend ho kar "ghayab" jaisa lagta tha
                // (jaise yellow token yellow home-path par). Ab hamesha ek
                // white outline hota hai taake token kisi bhi background par
                // saaf nazar aaye, image load ho ya na ho.
                .border(1.5.dp, Color.White, CircleShape)
                .clickable(enabled = isMovable, onClick = onTap)
        )
    }
}

@Composable
fun LudoBoardCanvas(state: LudoGameState, onTokenTap: (LudoColor, Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val boardSizeDp = maxWidth
        // Board ka 1 cell — HTML jaisa hi 1% inset ke sath (poore board ka 15waan hissa nahi)
        val cellDp: Dp = boardSizeDp * (CELL_PCT / 100f)

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
        pathGroups.values.flatten().forEach { t ->
            val group = pathGroups.getValue(t.cellKey)
            val total = group.size.coerceAtMost(4)
            val cfg = if (total <= 1) STACK_CONFIG.getValue(1) else STACK_CONFIG.getValue(4)
            val cellLeftDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * t.col
            val cellTopDp = boardSizeDp * (BOARD_INSET_PCT / 100f) + cellDp * t.row

            run {
                val id = "${t.color}_${t.idx}"
                val n = slotOf["${t.cellKey}|$id"] ?: 0
                val off = cfg.offsets[n % cfg.offsets.size]
                val rawTokenSizeDp = cellDp * (cfg.sizePct / 100f)
                val centerX = cellLeftDp + cellDp * (off.first / 100f)
                val centerY = cellTopDp + cellDp * (off.second / 100f)
                val targetX = centerX - rawTokenSizeDp / 2
                val targetY = centerY - rawTokenSizeDp / 2

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
                    val isGliding = id in state.arrowGlidingTokens.value
                    val posTweenMs = if (isGliding) 480 else 160
                    val animX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = tween(posTweenMs, easing = FastOutSlowInEasing),
                        label = "tokenX"
                    )
                    val animY by animateDpAsState(
                        targetValue = targetY,
                        animationSpec = tween(posTweenMs, easing = FastOutSlowInEasing),
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
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tokenSize"
                    )
                    val hopDy = 0.dp

                    // Yard se path mein pehli dafa nikalne par (poppingToken match
                    // hote hi) chhota sa "pop" (fade-in + scale-up) animation —
                    // taake token achanak (teleport jaisa) nazar aane ke bajaye
                    // saaf tarah "namudar" hota dikhe.
                    val isPopping = id in state.poppingTokens.value
                    val popScale = remember { Animatable(1f) }
                    val popAlpha = remember { Animatable(1f) }
                    LaunchedEffect(isPopping) {
                        if (isPopping) {
                            popScale.snapTo(0.2f)
                            popAlpha.snapTo(0.2f)
                            popScale.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                            popAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                        }
                    }

                    BoxTokenWithGlow(
                        popScale = popScale.value,
                        popAlpha = popAlpha.value,
                        tokenSizeDp = animSize,
                        animX = animX,
                        animY = animY + hopDy,
                        isMovable = isMovable,
                        isCurrentTurn = isCurrentTurn,
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
                    val animX by animateDpAsState(targetValue = targetX, animationSpec = tween(230), label = "yardTokenX")
                    val animY by animateDpAsState(targetValue = targetY, animationSpec = tween(230), label = "yardTokenY")

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
