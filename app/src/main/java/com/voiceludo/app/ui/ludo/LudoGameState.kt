package com.voiceludo.app.ui.ludo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.GameEvent
import com.voiceludo.app.net.GameSnapshot
import com.voiceludo.app.net.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// pos: -1 = yard, 0..50 = ring (color ke apne start se relative), 51..55 = home-stretch,
// 56 = finish (ghar pahunch gaya)
//
// ============================================================================
// Yeh ab poori tarah BEKEND-DRIVEN hai — koi local bot/RNG/fake-player logic
// nahi bacha. Dice roll, token move, capture — sab kuch asal WebSocket bekend
// (BackendClient) par chalta hai; yeh class sirf us server-truth ko Compose UI
// ke liye animate/display karti hai (LudoGameScreen/LudoBoardCanvas isi ko
// dekh kar render karte hain, pehle jaisa hi).
// ============================================================================

// Ek saved-roll ke chip-popup mein dikhne wala option: savedRolls array ke andar ka
// index (jise apply karte waqt splice karna hai) + wo dice value jo user ko dikhti hai.
data class RollOption(val rollIndex: Int, val value: Int)

// Jab kisi movable token par tap ho aur us token ke liye ek se zyada ALAG numbers
// (jaisay saved 6 aur saved 5 dono) legally chal saktay hon, tab yeh popup dikhta hai
// taake player khud chuney ke kaunsa number is token par apply karna hai.
data class RollChoice(val tokenIdx: Int, val options: List<RollOption>)

class LudoGameState(
    val mode: LudoMode,
    val players: List<LudoColor>,
    val magicOn: Boolean,
    // Is device par jo actual insaan khel raha hai uska color — bekend ke "matched"
    // message se milta hai. Sirf isi color ke tokens/dice is device se control hote hain.
    val myColor: LudoColor
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    val tokens = mutableStateMapOf<LudoColor, MutableList<Int>>().apply {
        players.forEach { c -> put(c, mutableStateListOf(-1, -1, -1, -1)) }
    }

    var currentIdx = mutableStateOf(0)
    val diceByColor = mutableStateMapOf<LudoColor, Int>().apply { players.forEach { put(it, 1) } }
    var diceRolled = mutableStateOf(false)
    var isRolling = mutableStateOf(false)
    // Tap se lekar server ke "dice" (ya "error") jawab tak — is dauran button
    // ko turant dobara-tap se bachata hai. Pehle sirf isRolling par bharosa
    // tha, jo server ka jawab aane tak false hi rehta tha — is beech tez tez
    // tap karne se ek se zyada "roll" request chali jati thi, jisse kabhi
    // kabhi dice ghalat/jaldi roll hone jaisa lagta tha.
    var rollRequested = mutableStateOf(false)
    var isMoving = mutableStateOf(false)
    var movable = mutableStateListOf<Int>()
    var killerFlashPos = mutableStateOf<Int?>(null)
    var killedFlashPos = mutableStateOf<Int?>(null)

    var gameOver = mutableStateOf(false)
    var winnerText = mutableStateOf("")
    // Raw winner color (winnerText jaisa formatted string nahi) — LudoGameScreen
    // isay profiles + matched.bet ke sath mila kar poora winner/loser result
    // screen banata hai (naam + jeeti hui bet amount ke sath).
    var winnerColor = mutableStateOf<LudoColor?>(null)

    var savedRolls = mutableStateListOf<Int>()
    var rollChoice = mutableStateOf<RollChoice?>(null)

    val finishOrder = mutableStateListOf<LudoColor>()
    val rankBadge = mutableStateMapOf<LudoColor, Int>() // color -> rank (1,2,3)

    val currentColor: LudoColor get() = players[currentIdx.value]
    val dice: Int get() = diceByColor.getValue(currentColor)

    val magicDiceCells = mutableStateListOf<Int>()
    val magicRocketCells = mutableStateListOf<Int>()

    // Har color ke liye agli extra-roll ki diamond-cost — snapshot se sync hoti
    // rehti hai (0 = us player ke liye is game mein lock ho chuka).
    val extraRollNextCost = mutableStateMapOf<LudoColor, Long>()

    // ---- Net/connection drop UI: "Reconnecting…" overlay RECONNECT_GRACE_SECONDS
    // (bekend ke ReconnectGraceSeconds se match) countdown se shuru hoti hai.
    // Agar itne second guzar jayen aur wapis connect na ho, to "Exit / Connect"
    // popup dikhta hai — Exit game chhod deta hai (forfeit), Connect dobara try
    // karta hai (naya window). Asal enforcement (kab tak reconnect allowed hai)
    // hamesha bekend par hoti hai — yeh sirf display/local retry-trigger hai.
    // (Pehle yahan hardcoded 30 tha jabke bekend ka asal window 60 second hai —
    // is se "time khatam" popup waqt se pehle dikh jata tha aur user ghalti se
    // Exit dabakar bet haar sakta tha jabke server abhi bhi wapis aane ka
    // intezaar kar raha hota.) ----
    var connectionLost = mutableStateOf(false)
    var reconnectSecondsLeft = mutableStateOf(RECONNECT_GRACE_SECONDS)
    var showReconnectChoice = mutableStateOf(false)
    private var reconnectJob: kotlinx.coroutines.Job? = null

    fun onConnectionLost() {
        if (gameOver.value) return
        connectionLost.value = true
        showReconnectChoice.value = false
        startReconnectCountdown()
    }

    private fun startReconnectCountdown() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (s in RECONNECT_GRACE_SECONDS downTo 1) {
                reconnectSecondsLeft.value = s
                // Har ~4 second mein khud hi reconnect try karte rehte hain (sirf
                // countdown dikhana kaafi nahi, asal koshish bhi honi chahiye) —
                // net wapis aate hi yeh apne aap successful ho jayegi.
                if (s % 4 == 0) BackendClient.reconnect()
                delay(1000)
                if (!connectionLost.value) return@launch
            }
            if (connectionLost.value) showReconnectChoice.value = true
        }
    }

    // Server se "resumed" ya koi bhi naya "events" mil jana khud connection theek
    // hone ka saboot hai.
    fun onConnectionRestored() {
        if (!connectionLost.value) return
        reconnectJob?.cancel()
        connectionLost.value = false
        showReconnectChoice.value = false
        reconnectSecondsLeft.value = RECONNECT_GRACE_SECONDS
    }

    // "Connect" button — network dobara check karke ek aur poora window try karo
    fun retryConnect() {
        showReconnectChoice.value = false
        BackendClient.reconnect()
        startReconnectCountdown()
    }

    // "Exit" button — game jaan-boojh kar chhod dena (forfeit) — asal "leave" call
    // aur navigation LudoGameScreen khud sambhalta hai is flag ko dekh kar.
    var requestExit = mutableStateOf(false)
    fun exitGame() {
        showReconnectChoice.value = false
        requestExit.value = true
    }

    // ---- Har player ka naam + DP — "matched" message ke profiles map se shuru hoti
    // hai, aur "opponentProfile" event se real-time update hoti rehti hai (jab koi
    // player mid-game apna naam/photo badle). ----
    val profiles = mutableStateMapOf<LudoColor, Profile>()

    fun applyInitialProfiles(map: Map<String, Profile>) {
        map.forEach { (colorName, p) ->
            runCatching { LudoColor.valueOf(colorName) }.getOrNull()?.let { profiles[it] = p }
        }
    }

    fun onOpponentProfile(colorName: String, name: String, avatar: String) {
        runCatching { LudoColor.valueOf(colorName) }.getOrNull()?.let { profiles[it] = Profile(name, avatar) }
    }

    // ---- Turn timer — bekend har turn (roll ya pending-move) shuru hote hi 12-second
    // duration bhejta hai; yahan sirf us duration ko store karte hain, PlayerProfileBox
    // isi se apna khud ka countdown-ring animation chalata hai (turnTimerKey badalte
    // hi animation restart ho jati hai, taake har naye turn/timer par ring fresh se
    // shuru ho). Agar 12 second tak player roll/move na kare, bekend khud uski taraf
    // se action le leta hai — yahan sirf visual countdown hai, asal enforcement server
    // par hai. ----
    var turnTimerColor = mutableStateOf<LudoColor?>(null)
    var turnTimerSeconds = mutableStateOf(12)
    var turnTimerKey = mutableStateOf(0)

    fun onTurnTimer(colorName: String, seconds: Int) {
        val c = runCatching { LudoColor.valueOf(colorName) }.getOrNull() ?: return
        turnTimerColor.value = c
        turnTimerSeconds.value = seconds
        turnTimerKey.value += 1
    }

    // ---- Server se aane wale "events"/"matched" messages yahan process hote hain ----

    // Naya match milte hi (koi dice roll hone se pehle) initial state seedha, bina
    // animation ke, apply karta hai.
    fun applyInitialSnapshot(snap: GameSnapshot) {
        syncFromSnapshot(snap)
    }

    // Server ke "events" message par LudoGameScreen isay call karta hai — events ko
    // ek ek karke animate karte hue play karta hai, phir final snapshot se sync kar
    // ke pakka karta hai ke state hoobahoo server jaisi hi hai.
    fun onServerEvents(events: List<GameEvent>, finalSnapshot: GameSnapshot) {
        scope.launch { playEvents(events, finalSnapshot) }
    }

    private suspend fun playEvents(events: List<GameEvent>, finalSnapshot: GameSnapshot) {
        for (e in events) {
            when (e.type) {
                "dice" -> {
                    val c = e.color?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() } ?: continue
                    rollRequested.value = false
                    isRolling.value = true
                    // Pehle 950ms, phir 550ms tha — user ne dobara kaha dice roll
                    // aur thora tez ho, ab aur kam kar diya.
                    delay(380)
                    isRolling.value = false
                    diceByColor[c] = e.value
                    diceRolled.value = true
                    delay(250)
                }
                "rollAgain" -> {
                    // Informational — savedRolls chain snapshot se hi sync ho jayegi
                }
                "awaitMove" -> {
                    movable.clear()
                    movable.addAll(e.movable)
                }
                "move" -> {
                    val c = e.color?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() } ?: continue
                    animateMove(c, e)
                }
                "turn" -> {
                    val c = e.color?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() }
                    if (c != null) {
                        rollRequested.value = false
                        currentIdx.value = players.indexOf(c).coerceAtLeast(0)
                        movable.clear()
                        rollChoice.value = null
                    }
                }
                "gameOver" -> {
                    gameOver.value = true
                    turnTimerColor.value = null
                    val winnerC = e.winner?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() }
                    winnerColor.value = winnerC
                    winnerText.value = if (winnerC != null) "$winnerC JEET GAYA! \uD83C\uDFC6" else "Game khatam"
                }
            }
        }
        // Safety sync — chahe koi field upar miss ho gayi ho, yahan hamesha
        // server ki asal truth se poori tarah match ho jati hai.
        syncFromSnapshot(finalSnapshot)
    }

    private suspend fun animateMove(color: LudoColor, e: GameEvent) {
        val t = tokens.getValue(color)
        if (e.tokenIndex !in t.indices) return
        // Master mode: agar yeh ek "joint pair" ka move hai to partner token
        // hamesha sath (usi cell par) hota hai — usay bhi primary token ke
        // sath sath, har step par, move karwaya jayega taake dono ek sath
        // chalte dikhein, na ke sirf tapped token move ho aur partner baad
        // mein achanak (bina animation) snap ho jaye.
        val partnerIdx = e.jointTokenIndex?.takeIf { it in t.indices }
        fun setPos(pos: Int) {
            t[e.tokenIndex] = pos
            partnerIdx?.let { t[it] = pos }
        }
        isMoving.value = true

        if (e.from == -1) {
            setPos(0)
            delay(230)
        } else if (e.arrowJumped) {
            // Arrow-jump: user ka feedback — instant teleport (purana) ajeeb
            // lagta hai, token ko dikhna chahiye ke wo arrow se guzar kar
            // agay nikal raha hai. Ab poori doori (chhote se bade step) tez
            // raftar se cross karte hain (normal chalne se kaafi tez —
            // "shortcut" ka ehsaas — lekin instant ghayab-nazar nahi hota).
            var pos = e.from
            while (pos < e.to) {
                pos += 1
                setPos(pos)
                delay(55)
            }
            delay(150)
        } else {
            // Normal move: pehle 150ms/step tha, thora slow kar diya taake
            // token step-step chalta hua saaf nazar aaye, jump jaisa na lage.
            var pos = e.from
            while (pos < e.to) {
                pos += 1
                setPos(pos)
                delay(210)
            }
        }
        setPos(e.to)
        isMoving.value = false

        if (e.captured.isNotEmpty()) {
            val g = globalCellOf(color, e.to)
            killerFlashPos.value = g
            killedFlashPos.value = g
            delay(1200)
            killerFlashPos.value = null
            killedFlashPos.value = null
        } else if (e.arrowJumped) {
            delay(160)
        }
    }

    // Server ki Snapshot se poori local state (tokens/dice/turn/movable/saved-rolls/
    // gameOver/finish-order/rank) ko sach maan kar update karta hai.
    private fun syncFromSnapshot(snap: GameSnapshot) {
        players.forEach { c ->
            val serverPos = snap.tokens[c.name] ?: return@forEach
            val local = tokens.getValue(c)
            for (i in local.indices) {
                val newVal = serverPos.getOrElse(i) { local[i] }
                if (local[i] != newVal) local[i] = newVal
            }
        }
        players.forEach { c ->
            snap.diceByColor[c.name]?.let { diceByColor[c] = it }
        }
        val newIdx = players.indexOf(
            runCatching { LudoColor.valueOf(snap.currentColor) }.getOrNull() ?: currentColor
        )
        if (newIdx >= 0) currentIdx.value = newIdx

        savedRolls.clear()
        savedRolls.addAll(snap.savedRolls)
        movable.clear()
        movable.addAll(snap.movable)

        gameOver.value = snap.gameOver
        if (snap.gameOver && snap.winner != null && winnerText.value.isEmpty()) {
            val winnerC = runCatching { LudoColor.valueOf(snap.winner) }.getOrNull()
            winnerColor.value = winnerC
            winnerText.value = "${snap.winner} JEET GAYA! \uD83C\uDFC6"
        }

        finishOrder.clear()
        finishOrder.addAll(snap.finishOrder.mapNotNull { runCatching { LudoColor.valueOf(it) }.getOrNull() })
        rankBadge.clear()
        snap.rankBadge.forEach { (k, v) ->
            runCatching { LudoColor.valueOf(k) }.getOrNull()?.let { rankBadge[it] = v }
        }

        magicDiceCells.clear(); magicDiceCells.addAll(snap.magicDiceCells)
        magicRocketCells.clear(); magicRocketCells.addAll(snap.magicRocketCells)

        extraRollNextCost.clear()
        snap.extraRollNextCost.forEach { (k, v) ->
            runCatching { LudoColor.valueOf(k) }.getOrNull()?.let { extraRollNextCost[it] = v }
        }
    }

    // ---- UI se call hota hai jab self diamonds se ek extra dice-roll khareedna
    // chahe (sirf current-turn player hi bhej sakta hai, cost server khud nikalta hai) ----
    fun buyExtraRoll() {
        if (gameOver.value || currentColor != myColor) return
        BackendClient.buyExtraRoll()
    }

    // ---- UI se call hone wale actions — sab kuch seedha bekend ko bhej dete hain ----

    fun rollDice() {
        if (gameOver.value || movable.isNotEmpty() || currentColor != myColor || rollRequested.value) return
        rollRequested.value = true
        BackendClient.roll()
    }

    // ---- UI se call hota hai jab self kisi movable token par tap kare ----
    fun tapToken(tokenIdx: Int) {
        if (currentColor != myColor || tokenIdx !in movable || isMoving.value) return
        val legalIdxs = legalRollsForToken(currentColor, tokenIdx)
        if (legalIdxs.isEmpty()) return
        val distinctValues = legalIdxs.map { savedRolls[it] }.distinct()
        if (distinctValues.size == 1) {
            BackendClient.move(tokenIdx, distinctValues[0])
        } else {
            val options = distinctValues.map { v -> RollOption(legalIdxs.first { savedRolls[it] == v }, v) }
            rollChoice.value = RollChoice(tokenIdx, options)
        }
    }

    // ---- UI se call hota hai jab self popup mein se koi ek number chune ----
    fun chooseRoll(option: RollOption) {
        val choice = rollChoice.value ?: return
        rollChoice.value = null
        BackendClient.move(choice.tokenIdx, option.value)
    }

    private fun legalRollsForToken(color: LudoColor, tokenIdx: Int): List<Int> {
        val pos = tokens.getValue(color)[tokenIdx]
        val legal = mutableListOf<Int>()
        savedRolls.forEachIndexed { i, dv ->
            if (pos == -1) {
                if (dv == 6) legal.add(i)
            } else if (pos + dv <= 56) {
                legal.add(i)
            }
        }
        return legal
    }

    fun globalCellOf(color: LudoColor, pos: Int): Int =
        (COLOR_META.getValue(color).start + pos) % 52
}
