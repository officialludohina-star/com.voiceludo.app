package com.voiceludo.app.ui.ludo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import com.voiceludo.app.audio.SoundManager
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
    // Jab koi token yard (ghar) se seedha path ki cell 0 par nikalta hai, is
    // "$color_$idx" key ko thori der ke liye set karte hain taake
    // LudoBoardCanvas usi token par ek pop-in (fade+scale) animation dikha
    // sake — pehle token yard se path mein "achanak" (bina kisi animation ke)
    // nazar aata tha, ab wo saaf tarah se "nikalta hua" dikhta hai.
    var poppingToken = mutableStateOf<String?>(null)
    // Jab token arrow ki wajah se "shortcut" leta hai, is "$color_$idx" key ko
    // set karte hain taake LudoBoardCanvas us waqt sirf isi token ke liye ek
    // lamba, mukammal continuous glide-tween use kare (chhote-chhote alag
    // steps ke bajaye) — taake beech mein kahin bhi token "gayab" (flicker/
    // disappear) na ho, seedha ek hi silsile mein A se B tak nazar aaye.
    var arrowGlidingToken = mutableStateOf<String?>(null)
    // Jab token Magic Rocket cell trigger kare, is "$color_$idx" key ko set
    // karte hain taake LudoBoardCanvas token ko normal step-step chalne ke
    // bajaye seedha ek "udaan" (fly/arc) animation ke sath uski final jagah
    // par le jaye — peeche ek flame/fire trail bhi dikhta hai (jaise asal
    // rocket udta hai).
    var rocketFlyingToken = mutableStateOf<String?>(null)
    // Jin colors ne is match mein kabhi Golden Dice uthaya hai, unki list —
    // profile ke pass hamesha ke liye ek chhota badge dikhane ke liye.
    val goldenDiceHolders = mutableStateListOf<LudoColor>()
    // Har dafa Golden Dice uthein, yeh color set hota hai taake LudoGameScreen
    // ek "udaan" (board se profile tak udh kar jane wali) animation chalaye.
    // Counter isliye hai taake SAME color dobara uthaye to bhi dobara trigger ho.
    var goldenDiceFlightColor = mutableStateOf<LudoColor?>(null)
    var goldenDiceFlightTrigger = mutableStateOf(0)
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
                    // dice_roll/extra_dice sound ab yahan nahi — tap karte hi
                    // (rollDice()/buyExtraRoll() mein) turant baj jati hai,
                    // taake server round-trip ka intezar na karna pade.
                    delay(380)
                    isRolling.value = false
                    diceByColor[c] = e.value
                    diceRolled.value = true
                    delay(250)
                }
                "rollAgain" -> {
                    // Informational — savedRolls chain snapshot se hi sync ho jayegi
                }
                "threeSix" -> {
                    // threeSix.mp3 — 3 lagatar six aane par turn zabardasti khatam
                    SoundManager.playThreeSix()
                }
                "awaitMove" -> {
                    movable.clear()
                    movable.addAll(e.movable)
                }
                "move" -> {
                    val c = e.color?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() } ?: continue
                    // kill/rocket/magic-dice/firework sounds ab yahan turant
                    // nahi bajti — pehle yeh "move" event aate hi baj jati
                    // thin, jabke token abhi chalna/walk karna shuru bhi nahi
                    // hua hota tha (isliye "awaz pehle aati hai" wala masla
                    // hota tha). Ab animateMove() ke andar, asal visual moment
                    // par (jab token waqai us cell tak pohanchta hai) bajti hain.
                    animateMove(c, e)
                }
                "turn" -> {
                    val c = e.color?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() }
                    if (c != null) {
                        rollRequested.value = false
                        currentIdx.value = players.indexOf(c).coerceAtLeast(0)
                        movable.clear()
                        rollChoice.value = null
                        // my_turn.mp3 / opp_turn.mp3 — private, sirf apne
                        // device par (jaisa color yeh device hai us hisaab se).
                        if (c == myColor) SoundManager.playMyTurn() else SoundManager.playOppTurn()
                    }
                }
                "gameOver" -> {
                    gameOver.value = true
                    turnTimerColor.value = null
                    val winnerC = e.winner?.let { runCatching { LudoColor.valueOf(it) }.getOrNull() }
                    winnerColor.value = winnerC
                    winnerText.value = if (winnerC != null) "$winnerC JEET GAYA! \uD83C\uDFC6" else "Game khatam"
                    // cheer.mp3 sirf jeetne wale ki taraf, ludo_fail.mp3 baaqi sab
                    // (jo jeete nahi) ki taraf — har device apna hi sunta hai.
                    if (winnerC == myColor) SoundManager.playWin() else SoundManager.playLose()
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
            // Ghar/yard se nikalna: yard aur path do alag render-trees hain
            // (alag coordinate system), isliye beech ka "chalna" khud
            // animate nahi ho sakta — iski jagah token ko path ki cell 0
            // par pop-in (fade+scale) ke sath "namudar" hote dikhate hain,
            // taake yeh achanak/teleport jaisa na lage.
            val key = "${color}_${e.tokenIndex}"
            poppingToken.value = key
            setPos(0)
            // Ab pop-in animation khud bohot fast (parallel, ~100ms) hai, is liye
            // yahan bhi utna hi kam wait karte hain (thora buffer ke sath) — user
            // ne kaha token yard se nikalte hi foran nazar aana chahiye.
            delay(140)
            poppingToken.value = null
            // BUG FIX: agar exit wali cell (0) khud arrow-mode mein ek arrow
            // nikli (yani e.to != 0), to pehle yahan seedha e.to par "jump"
            // ho jata tha — koi animation hi nahi hoti thi. Ab pop-in ke
            // baad bhi wahi normal step-by-step (ya arrow ho to glide)
            // harkat hoti hai, taake yard se nikalte hi token kabhi bhi
            // achanak "jump" na kare.
            if (e.to > 0) {
                if (e.arrowJumped) {
                    // User ne kaha: pehle arrow-cell tak bhi normal step-step
                    // chalna chahiye (na ke seedha final position par glide) —
                    // sirf arrow-cell se aage wale hisse mein "magic" jump ho.
                    var pos = 0
                    while (pos < e.arrowAt) {
                        pos += 1
                        setPos(pos)
                        SoundManager.playTokenMove()
                        delay(220)
                    }
                    arrowGlidingToken.value = key
                    // firework.mp3 — arrow ki tail cell par pohanchte hi (jump
                    // shuru hone se theek pehle), exact us waqt.
                    SoundManager.playFirework()
                    setPos(e.to)
                    delay(480)
                    arrowGlidingToken.value = null
                } else if (e.rocketBonus) {
                    // Magic Rocket: normal chalne ke bajaye seedha "udh" kar
                    // apni final jagah par pohanchta hai.
                    rocketFlyingToken.value = key
                    SoundManager.playRocket()
                    setPos(e.to)
                    delay(620)
                    rocketFlyingToken.value = null
                } else {
                    var pos = 0
                    while (pos < e.to) {
                        pos += 1
                        setPos(pos)
                        SoundManager.playTokenMove()
                        delay(220)
                    }
                }
            }
        } else if (e.arrowJumped) {
            // Arrow-jump: pehle poora move (from -> to) ek hi lambi glide
            // hoti thi. User ne kaha token yahan bhi step-step chalta dikhe
            // — isliye ab pehle normal step-step se arrow-cell (arrowAt) tak
            // jate hain, aur sirf arrow-cell se aage (asal "magic" wala
            // hissa, arrowAt -> to) ek chhota glide/pop effect hota hai —
            // taake saaf lage ke "yahan arrow lagi, isliye aage nikal gaya",
            // na ke poora rasta hi gayab ho kar tay ho jaye.
            val key = "${color}_${e.tokenIndex}"
            var pos = e.from
            while (pos < e.arrowAt) {
                pos += 1
                setPos(pos)
                SoundManager.playTokenMove()
                delay(220)
            }
            arrowGlidingToken.value = key
            // firework.mp3 — arrow ki tail cell par pohanchte hi.
            SoundManager.playFirework()
            setPos(e.to)
            delay(480)
            arrowGlidingToken.value = null
        } else if (e.rocketBonus) {
            // Magic Rocket: is move ka poora safar normal chal kar tay karne
            // ke bajaye, token seedha "udh" (fly) kar apni final jagah par
            // pohanchta hai — LudoBoardCanvas mein rocketFlyingToken dekh kar
            // ek lambi arc-glide + peeche flame/fire trail dikhata hai. User
            // ne yehi kaha tha: "rocket jidhar jana hota hai wahan se udh kar
            // jis dabbe mein jana hota hai udhar jaye".
            val key = "${color}_${e.tokenIndex}"
            rocketFlyingToken.value = key
            SoundManager.playRocket()
            setPos(e.to)
            delay(620)
            rocketFlyingToken.value = null
        } else {
            // Normal move: har cell ek-ek karke, saaf step-step chal ke.
            var pos = e.from
            while (pos < e.to) {
                pos += 1
                setPos(pos)
                SoundManager.playTokenMove()
                delay(220)
            }
        }
        setPos(e.to)
        isMoving.value = false

        // Golden-Dice magic effect — walk poora hone ke exact waqt par.
        // (Rocket ka sound ab launch ke theek waqt par upar hi bajta hai,
        // taake "pehle awaz baad mein udaan" wala masla na ho.)
        if (e.magicBonus) {
            SoundManager.playMagicDice()
            if (color !in goldenDiceHolders) goldenDiceHolders.add(color)
            goldenDiceFlightColor.value = color
            goldenDiceFlightTrigger.value += 1
        }
        // Token ghar (home) pohanch gaya — firework.
        if (e.reachedHome) SoundManager.playFirework()

        if (e.captured.isNotEmpty()) {
            val g = globalCellOf(color, e.to)
            killerFlashPos.value = g
            killedFlashPos.value = g
            // kill sound bhi ab exact isi waqt (jab flash/visual kill hoti
            // hai), "move" event ke turant baad nahi.
            SoundManager.playKill()
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
        // Turant (server ke jawab ka intezar kiye bagair) — tap karte hi awaz.
        SoundManager.playExtraDice()
        BackendClient.buyExtraRoll()
    }

    // ---- UI se call hone wale actions — sab kuch seedha bekend ko bhej dete hain ----

    fun rollDice() {
        if (gameOver.value || movable.isNotEmpty() || currentColor != myColor || rollRequested.value) return
        rollRequested.value = true
        // Turant (server ke jawab ka intezar kiye bagair) — tap karte hi awaz,
        // pehle yeh "dice" event ke server se wapis aane tak ruk jati thi jo
        // der se/lagged mehsoos hoti thi.
        SoundManager.playDiceRoll()
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
