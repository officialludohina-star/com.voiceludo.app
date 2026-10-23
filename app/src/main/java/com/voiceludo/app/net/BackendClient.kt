package com.voiceludo.app.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

// ============================================================================
// Asal bekend (README.md ke mutabiq): "signup, login, aur game sab kuch ek hi
// WebSocket connection ke andar hota hai". Yeh client bhi bilkul waisa hi hai —
// ek hi persistent /ws connection app ke poore session mein (login se le kar
// game khatam hone tak) khula rehta hai. Koi bhi fake/local player/bot ab
// istemal nahi hota — matchmaking, dice, moves sab is server se aate hain.
// ============================================================================

private const val WS_URL = "wss://voice-party-bekend-sarver-production.up.railway.app/ws"
// Avatar upload REST endpoint isi bekend host par hai, sirf wss:// ki jagah
// https:// aur path /avatar (WS_URL se hi derive kar lete hain taake host
// do jagah maintain na karna pare).
internal val HTTP_BASE = WS_URL.removeSuffix("/ws").replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")

// Bekend ke "profiles" map / "opponentProfile" event / "auth" response mein aane
// wala har player ka naam + avatar (avatar hamesha ek hosted URL hoti hai, kabhi
// base64 nahi — README ke mutabiq).
data class Profile(val name: String, val avatar: String)

data class GameEvent(
    val type: String,
    val color: String? = null,
    val value: Int = 0,
    val tokenIndex: Int = 0,
    val from: Int = 0,
    val to: Int = 0,
    val captured: List<String> = emptyList(),
    val arrowJumped: Boolean = false,
    // arrowAt — jis cell par arrow lagi thi (jump se theek pehle) — client
    // isay use kar ke pehle wahan tak normal step-step chalta hai, phir sirf
    // wahan se aage (jumped-to `to` tak) hi disappear/reappear ka "magic"
    // effect dikhata hai. Poora move ek hi lambi glide nahi hoti.
    val arrowAt: Int = 0,
    val magicBonus: Boolean = false,
    // rocketBonus — true jab is move par Magic Rocket cell trigger hui ho
    // (magicBonus sirf Golden Dice ke liye hai).
    val rocketBonus: Boolean = false,
    val reachedHome: Boolean = false,
    val movable: List<Int> = emptyList(),
    val winner: String? = null,
    val finishOrder: List<String> = emptyList(),
    val message: String? = null,
    // Master mode: agar yeh move ek "joint pair" ka hai to backend partner
    // token ka index bhi bhejta hai — dono tokens sath move hote hain.
    val jointTokenIndex: Int? = null
)

data class GameSnapshot(
    val mode: String,
    val players: List<String>,
    val tokens: Map<String, List<Int>>,
    val currentColor: String,
    val diceByColor: Map<String, Int>,
    val savedRolls: List<Int>,
    val movable: List<Int>,
    val gameOver: Boolean,
    val winner: String?,
    val finishOrder: List<String>,
    val rankBadge: Map<String, Int>,
    val magicOn: Boolean,
    val magicDiceCells: List<Int>,
    val magicRocketCells: List<Int>,
    // Har color ke liye agli extra-roll ki diamond-cost (2,4,8,10,16,24, phir har
    // baar double) — 0 ka matlab hai us player ke liye is game mein 1000-diamond
    // cap tak pahunch kar lock ho chuka.
    val extraRollNextCost: Map<String, Long>
)

sealed class ServerMessage {
    data class Auth(
        val playerId: String,
        // gameId — user-facing unique numeric ID (jaise "738294156"), bekend
        // se milta hai. Profile screen ke neeche yeh dikhaya jata hai (copy
        // button ke saath) taake dost isay add kar sakein.
        val gameId: Long,
        val authToken: String,
        val coins: Long,
        val diamonds: Long,
        val name: String,
        val avatar: String
    ) : ServerMessage()
    data class Err(val message: String) : ServerMessage()
    data class Waiting(val message: String) : ServerMessage()
    data class Matched(
        val roomId: String,
        val color: String,
        val players: List<String>,
        val mode: String,
        val bet: Int,
        val coins: Long,
        val state: GameSnapshot,
        // color -> {name, avatar} har player ka, is se opponent ki profile
        // (naam + DP) game screen par dikhai ja sakti hai.
        val profiles: Map<String, Profile>
    ) : ServerMessage()
    data class Events(val events: List<GameEvent>, val state: GameSnapshot) : ServerMessage()
    // Net drop ke baad wapis connect hone par (BackendClient khud "resume" bhej
    // deta hai agar hum kisi active game mein thay) — poora room/game state
    // Matched jaisa hi wapis milta hai, koi navigation/re-match nahi hota.
    data class Resumed(
        val roomId: String, val color: String, val players: List<String>, val mode: String,
        val bet: Int, val coins: Long, val diamonds: Long, val state: GameSnapshot,
        val profiles: Map<String, Profile>
    ) : ServerMessage()
    // Opponent ka connection toot gaya — bekend seconds batata hai (reconnect
    // grace window) jitni der tak wo wapis aa sakta hai bina haarne ke.
    data class OpponentDisconnected(val color: String, val seconds: Int) : ServerMessage()
    data class OpponentReconnected(val color: String) : ServerMessage()
    data class Wallet(val color: String?, val coins: Long?, val diamonds: Long?, val message: String?) : ServerMessage()
    data class OpponentLeft(val color: String) : ServerMessage()
    // Kisi opponent ne mid-game apna naam/avatar badla (updateProfile bheja) —
    // isay real-time update karne ke liye.
    data class OpponentProfile(val color: String, val name: String, val avatar: String) : ServerMessage()
    // In-game chat — doosre player ne message likha, foran screen par dikhane ke liye.
    data class Chat(val color: String, val name: String, val text: String) : ServerMessage()
    // Kisi player ne apna mic on/off kiya — us ki profile par icon badalne ke liye.
    data class Mic(val color: String, val on: Boolean) : ServerMessage()
    // Har turn (roll ya pending-move) shuru hote hi bekend yeh bhejta hai — us
    // player ki profile par 12-second countdown ring dikhane ke liye.
    data class TurnTimer(val color: String, val seconds: Int) : ServerMessage()
    // Yeh account kisi doosre phone/device par login/signup hua — bekend ne is
    // (purane) connection ko turant band kar diya hai, 1 ID = 1 device rule ke tehat.
    data class ForceLogout(val message: String) : ServerMessage()
    object ConnectionOpened : ServerMessage()
    data class ConnectionClosed(val reason: String) : ServerMessage()
}

typealias ServerListener = (ServerMessage) -> Unit

// ConnState — poori app (ConnectionStatusOverlay, SplashAutoLoginScreen) isay
// dekh kar decide karti hai ke "internet nahi hai" vs "internet hai lekin
// hamare game-server tak nahi pohanch rahe" mein farak dikhana hai.
enum class ConnState { CONNECTING, CONNECTED, DISCONNECTED }

object BackendClient {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    private val listeners = CopyOnWriteArrayList<ServerListener>()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ConnState.DISCONNECTED)
    val state: kotlinx.coroutines.flow.StateFlow<ConnState> = _state
    // Signup/login ke baad connect hone se pehle bheji gayi cheezein isi queue
    // mein rehti hain, socket open hote hi flush ho jati hain.
    private val pending = mutableListOf<JSONObject>()

    // Session — jab tak app khuli hai / socket connected hai, yehi asal account hai.
    var playerId: String? = null; private set
    // gameId — user-facing numeric ID (Game ID), profile screen par copy ke
    // liye dikhaya jata hai. 0 matlab abhi tak nahi mila (auth response ka
    // intezar hai).
    var gameId: Long = 0; private set
    var authToken: String? = null; private set
    var coins: Long = 0; private set
    var diamonds: Long = 0; private set
    // Server-truth apna naam/avatar (auth response se, aur updateProfile bheje
    // jaane ke baad hum khud optimistically yahan update kar dete hain).
    var myName: String = ""; private set
    var myAvatar: String = ""; private set

    // Matching screen match milte hi yahan store kar deti hai, Game screen isay
    // consume kar leti hai — is se navigation route args mein room/color/state
    // jaisi cheezein ghusane ki zaroorat nahi parti.
    private var lastMatch: ServerMessage.Matched? = null
    fun consumeLastMatch(): ServerMessage.Matched? {
        val m = lastMatch
        lastMatch = null
        return m
    }

    // Opponent ka naam/DP "opponentProfile" event se seedha yahan bhi cache ho
    // jata hai (color -> latest OpponentProfile), chahe Game screen abhi tak
    // apna listener attach kar hi na paya ho. Isse woh "kabhi load hi nahi
    // hoti" wali race khatam ho jati hai — jab bhi Game screen mount ho, yeh
    // pehle se aaya hua data foran consume kar leti hai (dekho
    // consumeCachedOpponentProfiles neeche).
    private val opponentProfileCache = mutableMapOf<String, ServerMessage.OpponentProfile>()
    fun consumeCachedOpponentProfiles(): List<ServerMessage.OpponentProfile> =
        opponentProfileCache.values.toList()
    fun clearOpponentProfileCache() { opponentProfileCache.clear() }

    // inGame — jab tak yeh true hai, agar socket kabhi bhi (net jaane se) toot
    // kar wapis khule to onOpen khud "resume" bhej deta hai (naya login/signup
    // nahi maangta). "matched"/"resumed" par true hota hai, leaveRoom() ya
    // forceLogout par false.
    @Volatile private var inGame = false

    fun addListener(l: ServerListener) { listeners.add(l) }
    fun removeListener(l: ServerListener) { listeners.remove(l) }

    // OkHttp ke WebSocketListener callbacks (onOpen/onMessage/onClosed/onFailure)
    // hamesha OkHttp ke apne background thread par chalte hain, UI/main thread
    // par nahi. Agar listeners (jaise GmailLoginScreen) yahan se seedha Compose
    // state badalne ya navController.navigate() call karne ki koshish karte, to
    // "must be called on main thread" exception aata — jo OkHttp turant
    // connection failure samajh kar socket band kar deta ("ConnectionClosed"
    // dikhta, chahe server ne bilkul sahi jawab diya ho). Ab yahan hi ek dafa
    // main thread par switch kar dete hain, taake sab listeners hamesha safe
    // rahen aur unhe khud is baat ka khayal na rakhna pade.
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun notify(msg: ServerMessage) {
        if (msg is ServerMessage.OpponentProfile) {
            opponentProfileCache[msg.color] = msg
        }
        mainHandler.post {
            listeners.forEach { it(msg) }
        }
    }

    // Har naye socket ko ek "generation" number milta hai. reconnect() purane socket
    // ko cancel karta hai, to uska onFailure/onClosed baad mein aa kar NAYE socket ki
    // reference (socket = null) aur connection state bigaad deta tha — phir doosra
    // connection khul jata. Ab purane socket ke callbacks (generation mismatch) ignore hote hain.
    private val socketLock = Any()
    private var socketGen = 0

    fun ensureConnected() {
        synchronized(socketLock) {
        if (socket != null) return
        val gen = ++socketGen
        _state.value = ConnState.CONNECTING
        // ZAROORI FIX: pehle "authed" sirf onOpen() (async, connection asal
        // mein khulne ke baad) ke andar reset hota tha. Lekin naya socket
        // reference isi function mein SYNCHRONOUSLY (neeche) set ho jata hai
        // — matlab agar isi turant baad koi doosri jagah (jaise "join") send()
        // ko call kare, socket != null ho chuka hota lekin authed abhi bhi
        // PURANI (mar chuki) connection ki "true" value par atka hota, jab tak
        // onOpen async chal kar ise reset na kare. Is dauran send()'s gate
        // (`!authed && ...`) galat tarah pass ho jata aur "join" seedha bhej
        // dia jata — asal loginWithToken/resume se bhi pehle — server isay
        // unauthenticated samajh kar "pehle signup/login karein" bhej deta
        // (chahe user asal mein login ho). Ab authed yahin, turant, naya
        // connection banate hi false kar dete hain — taake beech ka yeh
        // race-window kabhi na khule.
        authed = false
        val request = Request.Builder().url(WS_URL).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnState.CONNECTED
                authed = false
                // Har (re)connection par bekend ek NAYA connection object samajhta hai —
                // is par khud-b-khud authenticated nahi hota, chahe hamare paas pehle se
                // valid auth_token kyun na ho. Pehle sirf mid-game "resume" hota tha, is
                // liye agar connection kisi aur waqt (game ke bahar, jaise matching screen
                // par jaane se pehle) reconnect hoti to naya connection UN-authenticated
                // reh jata aur agli request (jaise "join") par "pehle signup ya login
                // karein" error aata — chahe user asal mein login hi kyun na ho. Ab agar
                // active game hai to "resume", warna (per session token maujood ho to)
                // "loginWithToken" — dono cases mein naya connection turant re-authenticate
                // ho jata hai. Baaki koi bhi queued message (jaise "join") send()'s
                // authed-gate ki wajah se yahan turant nahi jata — "auth" mil te hi
                // flushPending() khud bhej degi.
                val token = authToken
                if (token != null) {
                    val type = if (inGame) "resume" else "loginWithToken"
                    webSocket.send(JSONObject().put("type", type).put("auth_token", token).toString())
                } else {
                    // Koi saved token hi nahi — matlab login/signup screen khud
                    // "login"/"signup" bhejegi (dono bootstrap types hain, seedha
                    // ja sakti hain), baaki kuch bhejne ki zaroorat nahi.
                }
                notify(ServerMessage.ConnectionOpened)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { notify(it) }
            }

            // Server ke BINARY messages = doosre players ki live awaz (voice.go):
            // [1 byte: bolne wale ka slot][payload]. Yeh seedha VoiceEngine ko jata hai.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (gen != socketGen) return
                com.voiceludo.app.audio.VoiceEngine.onIncoming(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("BackendClient", "ws failure: ${t.message}")
                synchronized(socketLock) {
                    if (gen != socketGen) return
                    socket = null
                }
                _state.value = ConnState.DISCONNECTED
                notify(ServerMessage.ConnectionClosed(t.message ?: "connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(socketLock) {
                    if (gen != socketGen) return
                    socket = null
                }
                _state.value = ConnState.DISCONNECTED
                notify(ServerMessage.ConnectionClosed(reason))
            }
        })
        }
    }

    // authed — true sirf jab is (current) connection par "auth" mil chuka ho.
    // Naya connection open hote hi false ho jata hai jab tak resume/loginWithToken
    // ka jawab na aa jaye — is dauran koi bhi doosra message (jaise "join")
    // bheja gaya to seedha nahi jata, balke isi tarah queue ho jata hai jaise
    // socket band ho — taake kabhi bhi "pehle signup ya login karein" na aaye
    // jab user asal mein login hi ho.
    @Volatile private var authed = false
    private val authBootstrapTypes = setOf("login", "signup", "resume", "loginWithToken", "resetPassword", "requestPasswordReset")

    private fun send(obj: JSONObject) {
        ensureConnected()
        val ws = socket
        val type = obj.optString("type")
        if (ws == null || (!authed && type !in authBootstrapTypes)) {
            synchronized(pending) { pending.add(obj) }
        } else {
            ws.send(obj.toString())
        }
    }

    // Auth confirm hote hi (parse() ke "auth" case se) jo bhi is dauran queue
    // hui thi (jaise "join"), ab bhej dete hain.
    private fun flushPending() {
        val ws = socket ?: return
        synchronized(pending) {
            pending.forEach { ws.send(it.toString()) }
            pending.clear()
        }
    }

    fun signup(email: String, password: String) {
        send(JSONObject().put("type", "signup").put("email", email).put("password", password))
    }

    fun login(email: String, password: String) {
        send(JSONObject().put("type", "login").put("email", email).put("password", password))
    }

    // Forgot Password — email par pehle app khud (EmailService) OTP bhej kar
    // verify kar chuki hoti hai, phir yeh call bekend par us email ke account
    // ka naya password set kar deta hai. Jawab "auth" hi aata hai (naya
    // session token + coins/diamonds ke sath) — matlab reset hote hi user
    // turant logged-in bhi ho jata hai, dobara login karne ki zaroorat nahi.
    fun resetPassword(email: String, newPassword: String) {
        send(JSONObject().put("type", "resetPassword").put("email", email).put("password", newPassword))
    }

    fun join(mode: String, bet: Int, players: Int, magic: Boolean) {
        clearOpponentProfileCache()
        send(
            JSONObject()
                .put("type", "join")
                // BUG FIX: LudoMode enum.name UPPERCASE deta hai ("ARROW", "QUICK",
                // "MASTER") lekin backend sirf lowercase ("arrow","quick","master")
                // pehchanta hai — match na hone par backend chup chaap Classic mode
                // laga deta tha, isliye Arrow/Quick/Master ke special rules (jaise
                // arrow-tail par extra roll) kabhi apply hi nahi ho rahe thay.
                .put("mode", mode.lowercase())
                .put("bet", bet)
                .put("players", players)
                .put("magic", magic)
        )
    }

    fun roll() {
        send(JSONObject().put("type", "roll"))
    }

    fun move(token: Int, value: Int = 0) {
        send(JSONObject().put("type", "move").put("token", token).put("value", value))
    }

    fun buyExtraRoll() {
        send(JSONObject().put("type", "buyExtraRoll"))
    }

    // Live voice: mic ka ek frame (binary). Sirf tab bhejta hai jab connection login ho chuka ho
    // aur game room mein hon; warna chupchap drop (server bhi room ke bahar ki awaz drop karta hai).
    fun sendVoiceFrame(data: ByteArray) {
        if (!authed || !inGame) return
        socket?.send(data.toByteString())
    }

    // In-game chat: text server ko jata hai, wahan se foran room ke doosre players ko.
    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        send(JSONObject().put("type", "chat").put("text", t))
    }

    // Apna mic on/off status doosre players ko batata hai (taake un ki screen par icon sahi dikhe).
    fun sendMicState(on: Boolean) {
        send(JSONObject().put("type", "mic").put("on", on))
    }

    fun leaveRoom() {
        inGame = false
        send(JSONObject().put("type", "leave"))
    }

    // reconnect() — user "Connect" button tap kare (network wapis check karne
    // ke liye) ya app khud dobara koshish karna chahe. Purana dead socket hata
    // kar naya connection kholta hai — onOpen() khud "resume" bhej dega
    // (inGame abhi tak true hai to).
    fun reconnect() {
        synchronized(socketLock) {
            socketGen++
            socket?.cancel()
            socket = null
        }
        ensureConnected()
    }

    // autoLogin() — app khulte hi (koi active game nahi, sirf pehle se saved
    // session token) khud-b-khud login karta hai. "resume" se alag hai (wo
    // sirf mid-game disconnect ke liye kaam karta hai) — bekend par isi ke
    // liye naya "loginWithToken" message type hai. Callback ek hi dafa,
    // Auth ya Err milte hi, chalta hai.
    fun autoLogin(authToken: String, onResult: (success: Boolean, message: String?) -> Unit) {
        var responded = false
        lateinit var listener: ServerListener
        listener = { msg ->
            if (!responded) {
                when (msg) {
                    is ServerMessage.Auth -> { responded = true; removeListener(listener); onResult(true, null) }
                    is ServerMessage.Err -> { responded = true; removeListener(listener); onResult(false, msg.message) }
                    is ServerMessage.ConnectionClosed -> {
                        // connect hi na ho saka — timeout se pehle hi bata dete hain
                        responded = true; removeListener(listener); onResult(false, msg.reason)
                    }
                    else -> {}
                }
            }
        }
        addListener(listener)
        send(JSONObject().put("type", "loginWithToken").put("auth_token", authToken))
        // Safety-net — server kabhi jawab hi na de to hamesha ke liye latka na rahe.
        mainHandler.postDelayed({
            if (!responded) { responded = true; removeListener(listener); onResult(false, "timeout") }
        }, 10000)
    }

    // Naam/avatar badalne ke liye — avatar hamesha pehle uploadAvatar() se mile
    // hosted URL ke sath bhejna hai, kabhi bhi local file path/base64 nahi.
    // Optimistically apni local copy (myName/myAvatar) bhi turant update kar
    // dete hain, taake apni hi screen par turant naya naam/photo dikhe.
    fun updateProfile(name: String, avatar: String) {
        myName = name
        myAvatar = avatar
        send(JSONObject().put("type", "updateProfile").put("name", name).put("avatar", avatar))
    }

    // Profile photo upload — README ke mutabiq POST /avatar?token=<auth_token>,
    // body = raw image bytes, Content-Type: image/jpeg|png|webp, max 3MB.
    // Response: {"url": "https://.../avatars/xxx.jpg"} — yehi URL phir
    // updateProfile() mein bheja jata hai. Kabhi bhi base64/data-URI nahi bhejte.
    // Callback hamesha main thread par aata hai.
    fun uploadAvatar(bytes: ByteArray, mimeType: String, onResult: (url: String?, error: String?) -> Unit) {
        val token = authToken
        if (token == null) {
            mainHandler.post { onResult(null, "login zaroori hai") }
            return
        }
        val url = "$HTTP_BASE/avatar?token=$token"
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).header("Content-Type", mimeType).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                mainHandler.post { onResult(null, e.message ?: "upload fail ho gaya") }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use { resp ->
                    val text = resp.body?.string()
                    if (!resp.isSuccessful || text == null) {
                        mainHandler.post { onResult(null, text ?: "upload fail ho gaya (${resp.code})") }
                        return
                    }
                    val resultUrl = try { JSONObject(text).optString("url").ifBlank { null } } catch (e: Exception) { null }
                    mainHandler.post {
                        if (resultUrl != null) onResult(resultUrl, null) else onResult(null, "invalid response")
                    }
                }
            }
        })
    }

    private fun jsonArrayToIntList(a: JSONArray?): List<Int> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.getInt(it) }
    }

    private fun jsonArrayToStringList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun parseSnapshot(obj: JSONObject?): GameSnapshot? {
        obj ?: return null
        val tokensObj = obj.optJSONObject("tokens")
        val tokens = mutableMapOf<String, List<Int>>()
        tokensObj?.keys()?.forEach { k -> tokens[k] = jsonArrayToIntList(tokensObj.optJSONArray(k)) }
        val diceObj = obj.optJSONObject("diceByColor")
        val dice = mutableMapOf<String, Int>()
        diceObj?.keys()?.forEach { k -> dice[k] = diceObj.optInt(k, 1) }
        val rankObj = obj.optJSONObject("rankBadge")
        val rank = mutableMapOf<String, Int>()
        rankObj?.keys()?.forEach { k -> rank[k] = rankObj.optInt(k) }
        val extraCostObj = obj.optJSONObject("extraRollNextCost")
        val extraCost = mutableMapOf<String, Long>()
        extraCostObj?.keys()?.forEach { k -> extraCost[k] = extraCostObj.optLong(k) }
        val winner = obj.optString("winner")
        return GameSnapshot(
            mode = obj.optString("mode"),
            players = jsonArrayToStringList(obj.optJSONArray("players")),
            tokens = tokens,
            currentColor = obj.optString("currentColor"),
            diceByColor = dice,
            savedRolls = jsonArrayToIntList(obj.optJSONArray("savedRolls")),
            movable = jsonArrayToIntList(obj.optJSONArray("movable")),
            gameOver = obj.optBoolean("gameOver"),
            winner = winner.ifBlank { null },
            finishOrder = jsonArrayToStringList(obj.optJSONArray("finishOrder")),
            rankBadge = rank,
            magicOn = obj.optBoolean("magicOn"),
            magicDiceCells = jsonArrayToIntList(obj.optJSONArray("magicDiceCells")),
            magicRocketCells = jsonArrayToIntList(obj.optJSONArray("magicRocketCells")),
            extraRollNextCost = extraCost
        )
    }

    private fun parseProfiles(obj: JSONObject?): Map<String, Profile> {
        if (obj == null) return emptyMap()
        val out = mutableMapOf<String, Profile>()
        obj.keys().forEach { color ->
            val p = obj.optJSONObject(color) ?: return@forEach
            out[color] = Profile(name = p.optString("name"), avatar = p.optString("avatar"))
        }
        return out
    }

    private fun parseEvents(arr: JSONArray?): List<GameEvent> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            val color = e.optString("color")
            val winner = e.optString("winner")
            val message = e.optString("message")
            GameEvent(
                type = e.optString("type"),
                color = color.ifBlank { null },
                value = e.optInt("value"),
                tokenIndex = e.optInt("tokenIndex"),
                from = e.optInt("from", -1),
                to = e.optInt("to", -1),
                captured = jsonArrayToStringList(e.optJSONArray("captured")),
                arrowJumped = e.optBoolean("arrowJumped"),
                arrowAt = e.optInt("arrowAt"),
                magicBonus = e.optBoolean("magicBonus"),
                rocketBonus = e.optBoolean("rocketBonus"),
                reachedHome = e.optBoolean("reachedHome"),
                movable = jsonArrayToIntList(e.optJSONArray("movable")),
                winner = winner.ifBlank { null },
                finishOrder = jsonArrayToStringList(e.optJSONArray("finishOrder")),
                message = message.ifBlank { null },
                jointTokenIndex = if (e.has("jointTokenIndex") && !e.isNull("jointTokenIndex"))
                    e.optInt("jointTokenIndex") else null
            )
        }
    }

    private fun parse(text: String): ServerMessage? {
        return try {
            val obj = JSONObject(text)
            when (obj.optString("type")) {
                "auth" -> {
                    playerId = obj.optString("player_id")
                    gameId = obj.optLong("game_id")
                    authToken = obj.optString("auth_token")
                    coins = obj.optLong("coins")
                    diamonds = obj.optLong("diamonds")
                    myName = obj.optString("name")
                    myAvatar = obj.optString("avatar")
                    authed = true
                    flushPending()
                    ServerMessage.Auth(playerId!!, gameId, authToken!!, coins, diamonds, myName, myAvatar)
                }
                "error" -> ServerMessage.Err(obj.optString("message"))
                "waiting" -> ServerMessage.Waiting(obj.optString("message"))
                "matched" -> {
                    if (obj.has("coins")) coins = obj.optLong("coins")
                    val snap = parseSnapshot(obj.optJSONObject("state")) ?: return null
                    inGame = true
                    val m = ServerMessage.Matched(
                        roomId = obj.optString("room_id"),
                        color = obj.optString("color"),
                        players = jsonArrayToStringList(obj.optJSONArray("players")),
                        mode = obj.optString("mode"),
                        bet = obj.optInt("bet"),
                        coins = coins,
                        state = snap,
                        profiles = parseProfiles(obj.optJSONObject("profiles"))
                    )
                    lastMatch = m
                    m
                }
                "resumed" -> {
                    if (obj.has("coins")) coins = obj.optLong("coins")
                    if (obj.has("diamonds")) diamonds = obj.optLong("diamonds")
                    val snap = parseSnapshot(obj.optJSONObject("state")) ?: return null
                    inGame = true
                    authed = true
                    flushPending()
                    ServerMessage.Resumed(
                        roomId = obj.optString("room_id"),
                        color = obj.optString("color"),
                        players = jsonArrayToStringList(obj.optJSONArray("players")),
                        mode = obj.optString("mode"),
                        bet = obj.optInt("bet"),
                        coins = coins,
                        diamonds = diamonds,
                        state = snap,
                        profiles = parseProfiles(obj.optJSONObject("profiles"))
                    )
                }
                "opponentDisconnected" -> ServerMessage.OpponentDisconnected(obj.optString("color"), obj.optInt("seconds", 60))
                "opponentReconnected" -> ServerMessage.OpponentReconnected(obj.optString("color"))
                "events" -> {
                    val snap = parseSnapshot(obj.optJSONObject("state")) ?: return null
                    ServerMessage.Events(events = parseEvents(obj.optJSONArray("events")), state = snap)
                }
                "wallet" -> {
                    if (obj.has("coins")) coins = obj.optLong("coins")
                    if (obj.has("diamonds")) diamonds = obj.optLong("diamonds")
                    val color = obj.optString("color")
                    val message = obj.optString("message")
                    ServerMessage.Wallet(
                        color = color.ifBlank { null },
                        coins = if (obj.has("coins")) obj.optLong("coins") else null,
                        diamonds = if (obj.has("diamonds")) obj.optLong("diamonds") else null,
                        message = message.ifBlank { null }
                    )
                }
                "opponentLeft" -> ServerMessage.OpponentLeft(obj.optString("color"))
                "opponentProfile" -> ServerMessage.OpponentProfile(
                    color = obj.optString("color"),
                    name = obj.optString("name"),
                    avatar = obj.optString("avatar")
                )
                "chat" -> ServerMessage.Chat(
                    color = obj.optString("color"),
                    name = obj.optString("name"),
                    text = obj.optString("message")
                )
                "mic" -> ServerMessage.Mic(obj.optString("color"), obj.optBoolean("on", true))
                "turnTimer" -> ServerMessage.TurnTimer(obj.optString("color"), obj.optInt("seconds", 12))
                "forceLogout" -> {
                    // Server ne yeh (purana) connection band kar diya — apni local
                    // session state bhi turant clear kar dete hain taake app dobara
                    // isi purani auth se kuch bhejne ki koshish na kare.
                    playerId = null
                    gameId = 0
                    authToken = null
                    inGame = false
                    authed = false
                    ServerMessage.ForceLogout(obj.optString("message"))
                }
                // Admin panel mein Save hua — config dobara fetch karo (screens khud update hongi).
                "configUpdated" -> {
                    AppConfigStore.refresh(force = true)
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("BackendClient", "parse error for '$text': ${e.message}")
            null
        }
    }
}
