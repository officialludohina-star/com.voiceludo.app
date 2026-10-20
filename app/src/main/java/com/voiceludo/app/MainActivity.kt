package com.voiceludo.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.ui.common.ConfigPageScreen
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.voiceludo.app.ui.ludo.LudoModeSelectScreen
import com.voiceludo.app.ui.ludo.LudoGameScreen
import com.voiceludo.app.ui.ludo.LudoMatchingScreen
import com.voiceludo.app.ui.voiceparty.VoicePartyMainScreen
import com.voiceludo.app.ui.voiceparty.MobileLoginScreen
import com.voiceludo.app.ui.voiceparty.FacebookLoginScreen
import com.voiceludo.app.ui.voiceparty.GmailLoginScreen
import com.voiceludo.app.ui.voiceparty.GmailSignupScreen
import com.voiceludo.app.ui.voiceparty.ForgotPasswordScreen
import com.voiceludo.app.ui.voiceparty.SetPasswordScreen
import com.voiceludo.app.ui.voiceparty.YallaHomeScreen
import com.voiceludo.app.ui.voiceparty.ProfileEditScreen
import com.voiceludo.app.ui.voiceparty.AccountStore
import com.voiceludo.app.ui.voiceparty.SplashAutoLoginScreen
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore
import com.voiceludo.app.net.NetworkMonitor
import com.voiceludo.app.ui.common.ConnectionStatusOverlay

// Poori app ka navigation graph — Voice Party (login/home) aur Ludo (mode-select/game)
// dono yahan se navigate hote hain, sab kuch native Kotlin/Compose mein.
class MainActivity : ComponentActivity() {
    // Poori app ko full-screen/immersive banata hai — status bar (battery, network,
    // clock waghera) aur navigation bar dono hide ho jate hain, taake mobile par game
    // bilkul edge-to-edge, "app jaisa" nazar aaye, browser/system chrome jaisa nahi.
    // User swipe kar ke bars ko thori der ke liye wapis la sakta hai (transient), is
    // liye onWindowFocusChanged mein dobara hide kar dete hain taake woh sticky rahe.
    private fun applyImmersiveFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Notch/camera-cutout wale phones par, immersive mode ke bawajood, us
        // cutout ke peechay content draw nahi hota jab tak yeh explicitly allow
        // na kiya jaye — warna wahan hamesha ek black patti dikhti rehti hai
        // (chahe status bar hidden ho). Yeh line poori screen tak content ko
        // extend karti hai, cutout ke peechay bhi.
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= 30) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullScreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveFullScreen()
        com.voiceludo.app.audio.SoundManager.init(this)
        AppConfigStore.init(applicationContext)

        // Dice-roll GIF (aur koi bhi doosri animated GIF) ko sahi se animate karne ke liye
        // Coil ke default ImageLoader mein GIF decoder register karna zaroori hai — warna
        // AsyncImage GIF ka sirf pehla frame static dikhata hai.
        //
        // Disk + memory cache bhi yahan explicitly configure ki hai, aur
        // respectCacheHeaders(false) lagaya hai — hamare hosted images (i.postimg.cc)
        // sahi Cache-Control headers nahi bhejtin, is wajah se Coil unhe by-default
        // kabhi disk par cache hi nahi karta tha aur app khulte hi HAR baar dobara
        // poori tarah internet se load hoti thin (isi liye pehli baar dhundla/khali
        // dikhta tha jab tak network na aa jaye). Ab ek dafa load hone ke baad
        // (chahe pichli app-session mein hi kyun na ho) yeh images disk cache se
        // turant mil jati hain — bina internet ka intezaar kiye.
        val gifImageLoader = ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                // Admin panel ki (static) SVG files ke liye
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(gifImageLoader)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // Phone ka internet on/off track karna shuru — ek hi dafa (poori app
                    // ke liye), taake ConnectionStatusOverlay sab screens par kaam kare.
                    // WebSocket connection bhi yahin se turant shuru kar dete hain (pehle
                    // sirf login button dabane par connect hota tha) — warna saved-session
                    // wale users ke liye SplashAutoLoginScreen ka "connState == CONNECTED"
                    // wala intezaar kabhi poora hi na hota, aur "loading..." par hamesha
                    // atki reh jati.
                    DisposableEffect(Unit) {
                        NetworkMonitor.start(context)
                        BackendClient.ensureConnected()
                        onDispose {}
                    }

                    // Pehle se saved session (auth_token) mile to "vp_splash" se shuru
                    // karte hain (jo khud-b-khud login try karti hai) — warna seedha
                    // "vp_main" (login/signup choose karne wali screen). Isi se woh purana
                    // bug fix hota hai: "1 dafa login karne ke baad app band/on karne par
                    // dobara signup maanga jata tha" — pehle koi bhi saved session check
                    // hi nahi hoti thi.
                    val startDestination = remember { if (SessionStore.hasSession(context)) "vp_splash" else "vp_main" }

                    // 1 ID = 1 device — bekend ne yeh connection "forceLogout" kar diya
                    // (matlab yehi account kisi doosre phone/device par login/signup ho
                    // gaya hai). Yahan poori app ke liye ek hi global listener hai
                    // (chahe user is waqt kisi bhi screen par ho), taake turant local
                    // session clear ho kar login screen par wapis bheja ja sake.
                    var forceLogoutMsg by remember { mutableStateOf<String?>(null) }
                    DisposableEffect(Unit) {
                        val listener: (ServerMessage) -> Unit = { msg ->
                            if (msg is ServerMessage.ForceLogout) {
                                forceLogoutMsg = msg.message.ifBlank { "Your ID has been logged in on another phone/device." }
                            }
                        }
                        BackendClient.addListener(listener)
                        onDispose { BackendClient.removeListener(listener) }
                    }
                    forceLogoutMsg?.let { msg ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Logged Out", fontWeight = FontWeight.Black) },
                            text = { Text(msg) },
                            confirmButton = {
                                TextButton(onClick = {
                                    AccountStore.clearSession(context)
                                    SessionStore.clear(context)
                                    forceLogoutMsg = null
                                    navController.navigate("vp_main") { popUpTo(0) }
                                }) { Text("OK") }
                            }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            enterTransition = { androidx.compose.animation.EnterTransition.None },
                            exitTransition = { androidx.compose.animation.ExitTransition.None },
                            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                            popExitTransition = { androidx.compose.animation.ExitTransition.None }
                        ) {
                            composable("vp_splash") { SplashAutoLoginScreen(navController) }
                            composable("vp_main") { VoicePartyMainScreen(navController) }
                            composable("vp_mobile_login") { MobileLoginScreen(navController) }
                            composable("vp_facebook_login") { FacebookLoginScreen(navController) }
                            composable("vp_gmail_login") { GmailLoginScreen(navController) }
                            composable("vp_gmail_signup") { GmailSignupScreen(navController) }
                            composable("vp_forgot_password") { ForgotPasswordScreen(navController) }
                            composable(
                                "vp_set_password/{method}/{contact}",
                                arguments = listOf(
                                    navArgument("method") { type = NavType.StringType },
                                    navArgument("contact") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val method = backStackEntry.arguments?.getString("method") ?: "gmail"
                                val contact = backStackEntry.arguments?.getString("contact") ?: ""
                                SetPasswordScreen(navController, method = method, contact = contact)
                            }
                            composable("vp_home") { YallaHomeScreen(navController) }
                            // Admin panel se banaye gaye naye pages
                            composable("vp_page/{id}") { entry ->
                                ConfigPageScreen(navController, entry.arguments?.getString("id") ?: "")
                            }
                            composable("vp_profile_edit") { ProfileEditScreen(navController) }
                            composable("ludo_mode_select") { LudoModeSelectScreen(navController) }
                            composable("ludo_matching/{mode}/{players}/{magic}/{betIndex}") { backStackEntry ->
                                val mode = backStackEntry.arguments?.getString("mode") ?: "classic"
                                val players = backStackEntry.arguments?.getString("players")?.toIntOrNull() ?: 4
                                val magic = backStackEntry.arguments?.getString("magic")?.toBooleanStrictOrNull() ?: false
                                val betIndex = backStackEntry.arguments?.getString("betIndex")?.toIntOrNull() ?: 0
                                LudoMatchingScreen(navController, mode = mode, players = players, magic = magic, betIndex = betIndex)
                            }
                            composable("ludo_game/{mode}/{players}/{magic}/{betIndex}") { backStackEntry ->
                                val mode = backStackEntry.arguments?.getString("mode") ?: "classic"
                                val players = backStackEntry.arguments?.getString("players")?.toIntOrNull() ?: 4
                                val magic = backStackEntry.arguments?.getString("magic")?.toBooleanStrictOrNull() ?: false
                                val betIndex = backStackEntry.arguments?.getString("betIndex")?.toIntOrNull() ?: 0
                                LudoGameScreen(navController, mode = mode, players = players, magic = magic, betIndex = betIndex)
                            }
                        }

                        // Poori app ke upar (har route ke upar) — internet/server connection
                        // chali jaye to sabse pehle yeh nazar aata hai.
                        ConnectionStatusOverlay(modifier = Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // App background chali jaye (home button, app-switch, screen off,
        // waghera) to lobby/matching screen ka background music turant ruk
        // jaye — pehle sirf screen se nikalne (composable dispose) par rukta
        // tha, app minimize karne par nahi (Compose tree tab bhi zinda rehta
        // hai, isliye music bajta reh jata tha).
        com.voiceludo.app.audio.SoundManager.pauseLobbyMusic()
    }

    override fun onResume() {
        super.onResume()
        com.voiceludo.app.audio.SoundManager.resumeLobbyMusic()
        // App wapis khulte hi panel ki latest settings check karo (4 second se pehle dobara nahi).
        AppConfigStore.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.voiceludo.app.audio.SoundManager.release()
    }
}
