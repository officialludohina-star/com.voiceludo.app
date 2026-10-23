package com.voiceludo.app.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import coil.Coil
import coil.request.ImageRequest
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// Admin panel ki images / GIF / SVG / SVGA ko app khulte hi (login/splash ke
// dauran) background mein pehle se load kar lete hain, taake Home/Profile
// kholte hi woh foran dikhein — pehle har screen khulne par network se utarti
// aur WebView / SVGA decode hoti thin, isi liye der se nazar aati thin.
object ConfigAssetCache {
    private const val TAG = "ConfigAssetCache"
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)
    private val http = OkHttpClient()
    private val started = ConcurrentHashMap.newKeySet<String>()

    // SVG: url -> data URI (state map, taake aate hi screen khud update ho jaye)
    private val svgData = mutableStateMapOf<String, String>()
    // SVGA: decoded entity (sirf main thread se chhua jata hai)
    private val svgaMem = HashMap<String, SVGAVideoEntity>()

    fun svgDataUri(url: String): String? = svgData[url]
    fun svgaEntity(url: String): SVGAVideoEntity? = svgaMem[url]
    fun putSvga(url: String, e: SVGAVideoEntity) { svgaMem[url] = e }

    private fun urlsOf(cfg: AppConfig): Set<String> {
        val s = LinkedHashSet<String>()
        cfg.elements.values.forEach { s.add(it.image) }
        cfg.gifts.forEach { s.add(it.image); s.add(it.animImage) }
        cfg.buttons.forEach { s.add(it.image) }
        cfg.screens.values.forEach { sc ->
            s.add(sc.bgImage)
            sc.elements.forEach { s.add(it.src) }
        }
        s.remove("")
        return s
    }

    // App ke andar likhe huay (hardcoded) bahari image links — Profile / Home / Mode screens.
    private val BUILTIN_URLS = listOf(
        "https://i.postimg.cc/65CVg5SN/male.webp",
        "https://i.postimg.cc/PrbZKVL7/IMG-20260831-WA0011.jpg",
        "https://i.postimg.cc/SKgfdTvw/female.webp",
        "https://i.postimg.cc/T335q8LT/horse-chat.webp",
        "https://i.postimg.cc/T3C5ckRd/file-000000000da882088a81c4962bde4afa.png",
        "https://i.postimg.cc/W4MZk05H/file-00000000076882088e096a6ce8072b70.png",
        "https://i.postimg.cc/hGXpXvTZ/IMG-20260902-WA0005.jpg",
        "https://i.postimg.cc/kggkYCRb/IMG-20260902-WA0005.jpg",
        "https://i.postimg.cc/qMtCGcNh/bg-associate-watch-vs.webp",
        "https://i.postimg.cc/vZTDxWkg/img8-static.png",
        "https://i.postimg.cc/zfFtpYyw/match.gif"
    )

    fun prefetchBuiltIn(ctx: Context) {
        val app = ctx.applicationContext
        for (url in BUILTIN_URLS) {
            if (!started.add(url)) continue
            runCatching {
                Coil.imageLoader(app).enqueue(ImageRequest.Builder(app).data(url).build())
            }.onFailure { started.remove(url) }
        }
    }

    // Main thread se bulao.
    fun prefetch(ctx: Context, cfg: AppConfig) {
        val app = ctx.applicationContext
        for (url in urlsOf(cfg)) {
            if (!started.add(url)) continue
            val own = url.startsWith(HTTP_BASE)
            when {
                own && url.endsWith(".svga") -> runCatching {
                    SVGAParser(app).decodeFromURL(
                        java.net.URL(url),
                        object : SVGAParser.ParseCompletion {
                            override fun onComplete(videoItem: SVGAVideoEntity) { svgaMem[url] = videoItem }
                            override fun onError() { started.remove(url) }
                        }
                    )
                }.onFailure { started.remove(url) }

                own && url.endsWith(".svg") -> io.execute { loadSvg(app, url) }

                else -> runCatching {
                    Coil.imageLoader(app).enqueue(ImageRequest.Builder(app).data(url).build())
                }.onFailure { started.remove(url) }
            }
        }
    }

    private fun loadSvg(app: Context, url: String) {
        try {
            val dir = File(app.cacheDir, "cfg_assets").apply { mkdirs() }
            val f = File(dir, url.substringAfterLast('/').filter { it.isLetterOrDigit() || it == '.' })
            if (f.exists() && f.length() > 0) {
                publishSvg(url, f.readBytes())
                return
            }
            http.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { started.remove(url) }
                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        val bytes = if (r.isSuccessful) r.body?.bytes() else null
                        if (bytes == null || bytes.isEmpty()) { started.remove(url); return }
                        runCatching { f.writeBytes(bytes) }
                        publishSvg(url, bytes)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "svg prefetch fail: ${e.message}")
            started.remove(url)
        }
    }

    private fun publishSvg(url: String, bytes: ByteArray) {
        val uri = "data:image/svg+xml;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        main.post { svgData[url] = uri }
    }
}
