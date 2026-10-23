package com.voiceludo.app.net

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Admin panel (/admin) se aane wali settings. App khulte hi aur har dafa panel
// mein "Save" hone par (server "configUpdated" bhejta hai) /config dobara
// fetch hota hai, aur Compose screens khud update ho jati hain — APK dobara
// build nahi karna parta.

data class ElementCfg(
    val visible: Boolean = true,
    val x: Int = 0,
    val y: Int = 0,
    val size: Int = 100,
    val animation: String = "none",
    val label: String = "",
    val image: String = "",
    val action: String = "",
    val fontSize: Int = 0
)

data class GiftCfg(
    val id: String,
    val name: String,
    val price: Long,
    val currency: String,
    val image: String,
    val animation: String,
    val animImage: String,
    val enabled: Boolean
)

data class ButtonCfg(
    val id: String,
    val label: String,
    val image: String,
    val x: Int,
    val y: Int,
    val size: Int,
    val animation: String,
    val action: String,
    val visible: Boolean,
    val layer: String = "front",
    val fontSize: Int = 0
)

// Page / screen ka ek item (image, svg, text ya button). x/y screen ke percent
// (item ka center), w/h dp mein.
data class ScreenElCfg(
    val id: String,
    val type: String,
    val src: String,
    val text: String,
    val color: String,
    val fontSize: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val rotation: Int,
    val opacity: Int,
    val animation: String,
    val action: String,
    val visible: Boolean,
    val layer: String = "front"
)

// Khaas ids: "matching" (game start screen), "result" (game over screen).
// Baaki "page_..." ids apne banaye hue pages hain (route: vp_page/<id>).
data class ScreenCfg(
    val name: String,
    val bgColor: String,
    val bgImage: String,
    val elements: List<ScreenElCfg>
)

data class AppConfig(
    val version: Long = 0,
    val elements: Map<String, ElementCfg> = emptyMap(),
    val gifts: List<GiftCfg> = emptyList(),
    val buttons: List<ButtonCfg> = emptyList(),
    val screens: Map<String, ScreenCfg> = emptyMap(),
    val settings: Map<String, String> = emptyMap()
)

object AppConfigStore {
    private const val TAG = "AppConfigStore"
    private const val PREFS = "app_config"
    private const val KEY_JSON = "json"

    // Server na mile (ya pehli dafa offline khule) to yehi pehle se baked
    // positions chalti hain — bilkul purani screen jaisi.
    private val DEFAULT_ELEMENTS: Map<String, ElementCfg> = mapOf(
        "avatar" to ElementCfg(x = -8, y = 5, size = 100),
        "coinPill" to ElementCfg(x = -2, y = 7, size = 100),
        "gemPill" to ElementCfg(x = -1, y = 7, size = 100),
        "shop" to ElementCfg(x = 3, y = 8, size = 159),
        "settings" to ElementCfg(x = 2, y = 5, size = 108),
        "modeCard1" to ElementCfg(x = -9, y = 196, size = 104),
        "modeCard2" to ElementCfg(x = 7, y = 195, size = 104),
        "navEvents" to ElementCfg(x = -23, y = 6, size = 93),
        "navBattle" to ElementCfg(x = -32, y = 13, size = 104),
        "navChat" to ElementCfg(x = -19, y = 8, size = 112),
        "navSocial" to ElementCfg(x = -1, y = 11, size = 106)
    )

    // Compose state — isay padhne wali har screen config badalte hi khud redraw hoti hai.
    var config by mutableStateOf(AppConfig(elements = DEFAULT_ELEMENTS))
        private set

    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private var prefs: SharedPreferences? = null
    @Volatile private var lastFetchMs = 0L
    @Volatile private var fetching = false

    private var appCtx: Context? = null

    fun init(context: Context) {
        if (prefs != null) return
        appCtx = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        // Pehle last-saved config (offline start par bhi panel wali settings milen)
        p.getString(KEY_JSON, null)?.let { raw -> parse(raw)?.let { config = it } }
        // App khulte hi sab images / GIF / SVG / SVGA background mein pehle se load
        main.post { appCtx?.let { ConfigAssetCache.prefetch(it, config); ConfigAssetCache.prefetchBuiltIn(it) } }
        refresh(force = true)
    }

    fun element(key: String): ElementCfg = config.elements[key] ?: DEFAULT_ELEMENTS[key] ?: ElementCfg()

    fun screen(id: String): ScreenCfg? = config.screens[id]

    fun setting(key: String, default: String): String = config.settings[key] ?: default

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (fetching || (!force && now - lastFetchMs < 4000)) return
        fetching = true
        lastFetchMs = now
        val req = Request.Builder().url("$HTTP_BASE/config").build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                fetching = false
                Log.w(TAG, "config fetch fail: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    fetching = false
                    val body = if (r.isSuccessful) r.body?.string() else null
                    val parsed = body?.let { parse(it) } ?: return
                    prefs?.edit()?.putString(KEY_JSON, body)?.apply()
                    main.post {
                        if (parsed != config) config = parsed
                        appCtx?.let { ConfigAssetCache.prefetch(it, parsed) }
                    }
                }
            }
        })
    }

    private val ASSET_PATH = Regex("^/a/[a-f0-9]{16}\\.svga?$")

    // Image link: apni SVG ("/a/<id>.svg") to poora https link bana do, baaki sirf
    // saaf-suthre https links. Kuch bhi aur (quotes, spaces, http://) khali ho jata hai.
    private fun assetUrl(s: String): String = when {
        ASSET_PATH.matches(s) -> "$HTTP_BASE$s"
        s.startsWith("https://") && s.none { it == '\'' || it == '"' || it == '<' || it == '>' || it.isWhitespace() } -> s
        else -> ""
    }

    private fun parse(raw: String): AppConfig? = try {
        val o = JSONObject(raw)

        val els = HashMap<String, ElementCfg>()
        o.optJSONObject("elements")?.let { eo ->
            eo.keys().forEach { k ->
                val e = eo.optJSONObject(k) ?: return@forEach
                els[k] = ElementCfg(
                    visible = e.optBoolean("visible", true),
                    x = e.optInt("x", 0),
                    y = e.optInt("y", 0),
                    size = e.optInt("size", 100).coerceIn(1, 400),
                    fontSize = e.optInt("fontSize", 0).coerceIn(0, 80),
                    animation = e.optString("animation", "none"),
                    label = e.optString("label", ""),
                    image = assetUrl(e.optString("image", "")),
                    action = e.optString("action", "")
                )
            }
        }

        val gifts = ArrayList<GiftCfg>()
        o.optJSONArray("gifts")?.let { a ->
            for (i in 0 until a.length()) {
                val g = a.optJSONObject(i) ?: continue
                gifts.add(
                    GiftCfg(
                        id = g.optString("id"),
                        name = g.optString("name"),
                        price = g.optLong("price", 0),
                        currency = g.optString("currency", "coins"),
                        image = assetUrl(g.optString("image", "")),
                        animation = g.optString("animation", "none"),
                        animImage = assetUrl(g.optString("animImage", "")),
                        enabled = g.optBoolean("enabled", true)
                    )
                )
            }
        }

        val buttons = ArrayList<ButtonCfg>()
        o.optJSONArray("buttons")?.let { a ->
            for (i in 0 until a.length()) {
                val b = a.optJSONObject(i) ?: continue
                buttons.add(
                    ButtonCfg(
                        id = b.optString("id"),
                        label = b.optString("label", ""),
                        image = assetUrl(b.optString("image", "")),
                        x = b.optInt("x", 50).coerceIn(-50, 150),
                        y = b.optInt("y", 50).coerceIn(-50, 150),
                        size = b.optInt("size", 80).coerceIn(1, 300),
                        fontSize = b.optInt("fontSize", 0).coerceIn(0, 80),
                        animation = b.optString("animation", "none"),
                        action = b.optString("action", ""),
                        visible = b.optBoolean("visible", true),
                        layer = if (b.optString("layer") == "back") "back" else "front"
                    )
                )
            }
        }

        val screens = HashMap<String, ScreenCfg>()
        o.optJSONObject("screens")?.let { so ->
            so.keys().forEach { id ->
                val sc = so.optJSONObject(id) ?: return@forEach
                val items = ArrayList<ScreenElCfg>()
                sc.optJSONArray("elements")?.let { a ->
                    for (i in 0 until a.length()) {
                        val e = a.optJSONObject(i) ?: continue
                        items.add(
                            ScreenElCfg(
                                id = e.optString("id"),
                                type = e.optString("type", "image"),
                                src = assetUrl(e.optString("src", "")),
                                text = e.optString("text", ""),
                                color = e.optString("color", ""),
                                fontSize = e.optInt("fontSize", 18),
                                x = e.optInt("x", 50).coerceIn(0, 100),
                                y = e.optInt("y", 50).coerceIn(0, 100),
                                w = e.optInt("w", 100).coerceIn(4, 1200),
                                h = e.optInt("h", 100).coerceIn(4, 1200),
                                rotation = e.optInt("rotation", 0),
                                opacity = e.optInt("opacity", 100).coerceIn(0, 100),
                                animation = e.optString("animation", "none"),
                                action = e.optString("action", ""),
                                visible = e.optBoolean("visible", true),
                                layer = if (e.optString("layer") == "back") "back" else "front"
                            )
                        )
                    }
                }
                screens[id] = ScreenCfg(
                    name = sc.optString("name", ""),
                    bgColor = sc.optString("bgColor", ""),
                    bgImage = assetUrl(sc.optString("bgImage", "")),
                    elements = items
                )
            }
        }

        val settings = HashMap<String, String>()
        o.optJSONObject("settings")?.let { so ->
            so.keys().forEach { k -> settings[k] = so.optString(k, "") }
        }

        AppConfig(
            version = o.optLong("version", 0),
            elements = els,
            gifts = gifts,
            buttons = buttons,
            screens = screens,
            settings = settings
        )
    } catch (e: Exception) {
        Log.e(TAG, "config parse error: ${e.message}")
        null
    }
}
