package com.voiceludo.app.ui.ludo

// ==== Ludo board ka poora data model — HTML/JS wale asal game se hi hoobahoo liya gaya
// hai (RING coordinates, har color ka start/stretch/yard, safe cells, arrow cells waghera)
// taake board ka layout aur rules bilkul pehle jaisay hi rahein, sirf ab yeh Kotlin mein hai.

enum class LudoColor { RED, GREEN, YELLOW, BLUE }

data class RC(val row: Int, val col: Int)

// 15x15 grid ka bahar wala ring — 52 cells, jahan se sab tokens guzarte hain
val RING: List<RC> = listOf(
    RC(6,1), RC(6,2), RC(6,3), RC(6,4), RC(6,5),
    RC(5,6), RC(4,6), RC(3,6), RC(2,6), RC(1,6), RC(0,6),
    RC(0,7),
    RC(0,8), RC(1,8), RC(2,8), RC(3,8), RC(4,8), RC(5,8),
    RC(6,9), RC(6,10), RC(6,11), RC(6,12), RC(6,13), RC(6,14),
    RC(7,14),
    RC(8,14), RC(8,13), RC(8,12), RC(8,11), RC(8,10), RC(8,9),
    RC(9,8), RC(10,8), RC(11,8), RC(12,8), RC(13,8), RC(14,8),
    RC(14,7),
    RC(14,6), RC(13,6), RC(12,6), RC(11,6), RC(10,6), RC(9,6),
    RC(8,5), RC(8,4), RC(8,3), RC(8,2), RC(8,1), RC(8,0),
    RC(7,0),
    RC(6,0)
)

data class ColorMeta(
    val start: Int,
    val stretch: List<RC>,
    // yard token centers as % of board width/height (0..100), matches HTML layout
    val yard: List<Pair<Float, Float>>
)

val COLOR_META: Map<LudoColor, ColorMeta> = mapOf(
    LudoColor.GREEN to ColorMeta(
        start = 0,
        stretch = listOf(RC(7,1), RC(7,2), RC(7,3), RC(7,4), RC(7,5), RC(7,6)),
        yard = listOf(10.6f to 10.6f, 21.2f to 10.6f, 10.6f to 21.2f, 21.2f to 21.2f)
    ),
    LudoColor.YELLOW to ColorMeta(
        start = 13,
        stretch = listOf(RC(1,7), RC(2,7), RC(3,7), RC(4,7), RC(5,7), RC(6,7)),
        yard = listOf(70.8f to 10.6f, 81.4f to 10.6f, 70.8f to 21.2f, 81.4f to 21.2f)
    ),
    LudoColor.BLUE to ColorMeta(
        start = 26,
        stretch = listOf(RC(7,13), RC(7,12), RC(7,11), RC(7,10), RC(7,9), RC(7,8)),
        yard = listOf(70.8f to 70.8f, 81.4f to 70.8f, 70.8f to 81.4f, 81.4f to 81.4f)
    ),
    LudoColor.RED to ColorMeta(
        start = 39,
        stretch = listOf(RC(13,7), RC(12,7), RC(11,7), RC(10,7), RC(9,7), RC(8,7)),
        yard = listOf(10.6f to 70.8f, 21.2f to 70.8f, 10.6f to 81.4f, 21.2f to 81.4f)
    )
)

// Har color ka apna start + start se 8 aage wala star/safe square
val SAFE_SET: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)

val PLAYER_COLORS_2P = listOf(LudoColor.RED, LudoColor.YELLOW)
val PLAYER_COLORS_4P = listOf(LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE)

// Arrow mode: har color ka apna exit-arm tail(ghar #5)/head(ghar #6)
const val ARROW_TAIL_OFFSET = 4
const val ARROW_HEAD_OFFSET = 5

fun arrowTailSet(): Set<Int> = COLOR_META.values.map { (it.start + ARROW_TAIL_OFFSET) % 52 }.toSet()
fun arrowHeadFor(color: LudoColor): Int = (COLOR_META.getValue(color).start + ARROW_HEAD_OFFSET) % 52

// Arrow mode: yeh 4 "curved" arrow images (board ke bahar wale edge par) asal mein har
// color ke apne EK "edge" cell ko point karte hain — SIRF usi color ka token yeh use kar
// sakta hai. Is cell par land hote hi token seedha apni home-stretch ke pehle ghar (rel 51)
// tak chala jata hai. Asal HTML ke window.ARROW_EDGE_OWNER se hoobahoo (missing tha, isi
// wajah se curved arrows sirf decoration thay aur kuch karte nahi thay).
val ARROW_EDGE_OWNER: Map<Int, LudoColor> = mapOf(
    22 to LudoColor.BLUE,
    35 to LudoColor.RED,
    48 to LudoColor.GREEN,
    9 to LudoColor.YELLOW
)
val ARROW_EDGE_SET: Set<Int> = ARROW_EDGE_OWNER.keys
const val ARROW_EDGE_ENTRY_REL = 51

// Quick mode: har color ka apna block cell (relative offset), jahan se sirf usi color ka
// token guzar sakta hai — waha aa kar token seedha finish (56) ho jata hai.
const val QUICK_BLOCK_REL = 46

fun quickBlockCellFor(color: LudoColor): Int = (COLOR_META.getValue(color).start + QUICK_BLOCK_REL) % 52

enum class LudoMode { CLASSIC, ARROW, QUICK, MASTER }

// ==== Asal HTML mein jo image URLs use hoti thin, wahi yahan bhi — taake board,
// tokens, dice, rank badges sab bilkul waisay hi dikhein.
const val GAME_BG_IMG = "file:///android_asset/img/game-bg.webp"
// Mode-select aur matching screen ka background (asal HTML ke #ludoModeScreen se) —
// game board wala background isse alag hai (upar GAME_BG_IMG hai).
const val MODE_SCREEN_BG_IMG = "file:///android_asset/img/ludo_mode_bg.webp"
const val RANK_1_ICON = "file:///android_asset/img/rank-no-1.webp"
const val RANK_2_ICON = "file:///android_asset/img/rank-no-2.webp"
const val GAME_BOARD_IMG = "file:///android_asset/img/Checkerboard-duel7bdc5231556b.png"

val TOKEN_IMG: Map<LudoColor, String> = mapOf(
    LudoColor.GREEN to "file:///android_asset/img/piece-green.webp",
    LudoColor.YELLOW to "file:///android_asset/img/piece-yellow.webp",
    LudoColor.BLUE to "file:///android_asset/img/piece-bule.webp",
    LudoColor.RED to "file:///android_asset/img/piece-red.webp"
)

// Dice roll gif + 1-6 face icons — ab local assets se (download_images.sh inhe
// download kar deti hai). Pehle yeh remote postimg.cc URLs the, jiski wajah se
// game ke doraan (jab internet slow/band ho) dice ka number ya rolling-animation
// kabhi load hi nahi hoti thi — is se "roll hui ya nahi" pata nahi chalta tha.
const val DICE_ROLL_GIF = "file:///android_asset/img/dice-roll-animation.gif"

// Matching screen ("Opponents dhoonde ja rahe hain...") ke dauran chalne wali gif —
// HINAX ka diya hua asal link.
const val MATCHING_SEARCH_GIF = "https://i.postimg.cc/zfFtpYyw/match.gif"
const val VS_ICON_IMG = "https://i.postimg.cc/qMtCGcNh/bg-associate-watch-vs.webp"

// Jab internet na ho ya frontend bekend WebSocket se connect na ho pa raha ho —
// yehi icon baar-baar (jahan bhi connection fail/lost dikhana ho) use hota hai.
const val NO_CONNECTION_ICON = "https://i.postimg.cc/hGXpXvTZ/IMG-20260902-WA0005.jpg"

val DICE_FACE_IMG: Map<Int, String> = mapOf(
    1 to "file:///android_asset/img/1.png",
    2 to "file:///android_asset/img/2.png",
    3 to "file:///android_asset/img/3.png",
    4 to "file:///android_asset/img/4.png",
    5 to "file:///android_asset/img/5.png",
    6 to "file:///android_asset/img/6.png"
)

// 4-player mein 1st/2nd/3rd finish karne walon ki profile par yehi rank badge lagta hai
val RANK_BADGE_IMG: Map<Int, String> = mapOf(
    1 to "file:///android_asset/img/room-icon-rank-crown.webp",
    2 to "file:///android_asset/img/1000101592-removebg-preview.png",
    3 to "file:///android_asset/img/file-00000000229c81fd83b90d22a29f7bba.png"
)

const val DEFAULT_AVATAR_IMG = "file:///android_asset/img/user-icon.png"
const val MODE_2P_ICON = "file:///android_asset/img/game-online-2p-uncheck.png"
const val MODE_4P_ICON = "file:///android_asset/img/game-online-4p-uncheck.png"

// Mode-select / bet-select screen ke icons (asal HTML se hoobahoo)
const val TICK_ICON = "file:///android_asset/img/tick.webp"
const val HOT_BADGE_ICON = "file:///android_asset/img/fragment-Rule2.webp"
const val BET_MINUS_ICON = "file:///android_asset/img/regression-coin-min-check.webp"
const val BET_PLUS_ICON = "file:///android_asset/img/regression-coin-add-check.webp"
const val COIN_ICON = "file:///android_asset/img/coin-v.png"
const val GOLDEN_DICE_ICON = "file:///android_asset/img/icon-golden-dice.webp"

val BET_OPTIONS = listOf(500, 2000, 10000, 20000, 50000, 100000, 200000, 250000, 500000, 1000000, 2000000, 3000000, 5000000)

// Admin panel (Bets tab) ki values: settings "bets_2" (2 Player) / "bets_4" (4 Player), ya purani "bets".
// Panel mein kuch set na ho to upar wali purani list hi chalti hai.
fun betOptions(players: Int): List<Int> {
    val store = com.voiceludo.app.net.AppConfigStore
    val raw = store.setting("bets_$players", store.setting("bets", ""))
    val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sorted()
    return if (parsed.isEmpty()) BET_OPTIONS else parsed
}

// Top bar + settings panel ke icons (asal HTML se hoobahoo)
const val SETTINGS_ICON = "file:///android_asset/img/system.webp"
const val BET_INFO_ICON = "file:///android_asset/img/file-000000000d188208a6a8a0921cdc4517.png"
const val SOUND_ICON = "file:///android_asset/img/room-ic-input-sound.webp"
const val SOUND_OFF_ICON = "file:///android_asset/img/room-ic-input-sound-off.webp"
const val EXIT_ICON = "file:///android_asset/img/btn-associate-exit.webp"

// Mode-select screen: "Start" button image (text already baked into the image)
// aur mode-box (Classic/Arrow/Quick/Master) ke select/unselect backgrounds —
// user ki di hui asal images, seedha assets se load hoti hain.
const val START_BUTTON_IMG = "file:///android_asset/img/btn-start.png"
const val MODE_BTN_SELECT_IMG = "file:///android_asset/img/mode-btn-select.webp"
const val MODE_BTN_UNSELECT_IMG = "file:///android_asset/img/mode-btn-unselect.webp"
// .game-top-icons-wrap ka background bar image (asal HTML se hoobahoo)
const val TOP_BAR_BG_IMG = "file:///android_asset/img/IMG-20260825-WA0006.jpg"

// Bet +/- button ke "disabled" (uncheck) variants — jab betIndex min/max par ho
const val BET_MINUS_ICON_DISABLED = "file:///android_asset/img/regression-coin-min-uncheck.webp"
const val BET_PLUS_ICON_DISABLED = "file:///android_asset/img/regression-coin-add-uncheck.webp"

// Magic mode ke bonus-cell icons (board par dice/rocket bonus dikhane ke liye)
const val MAGIC_DICE_ICON = "file:///android_asset/img/golden-dice-big.webp"
const val MAGIC_ROCKET_ICON = "file:///android_asset/img/rocket.webp"

// Arrow mode ka center diagonal arrow overlay, Quick mode ka block-cell icon (board par)
const val ARROW_CENTER_ICON = "file:///android_asset/img/1000100827-removebg-preview.png"
const val QUICK_BLOCK_ICON = "file:///android_asset/img/file-000000002f0c8211aadf26f528f26971.png"

// Bekend (hub.go: "const ReconnectGraceSeconds = 30") itni der tak disconnect
// hue player ki jagah room mein reserve rakhta hai. Yeh value yahan bhi WAHI
// honi chahiye — warna app "time khatam, Exit ya Connect chunein" popup
// jald/der se dikhayegi jabke server ka asal window kuch aur hoga.
const val RECONNECT_GRACE_SECONDS = 30

fun ludoColorOf(c: LudoColor): androidx.compose.ui.graphics.Color = when (c) {
    LudoColor.RED -> androidx.compose.ui.graphics.Color(0xFFE53935)
    LudoColor.GREEN -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    LudoColor.YELLOW -> androidx.compose.ui.graphics.Color(0xFFF9A825)
    LudoColor.BLUE -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
}
