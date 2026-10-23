package com.voiceludo.app.ui.ludo

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.Color
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.ui.common.parseConfigColor

// Game ki speed / timing — sab admin panel (Game tab) se aati hain. Panel mein kuch set na ho
// (ya internet na ho) to neeche likhi default values chalti hain. Sab values milliseconds mein
// hain (rocketLift dp mein). AppConfigStore.config Compose state hai, isliye panel mein Save
// hote hi nayi values agle roll / move se lag jati hain.
object GameTuning {
    private fun num(key: String): Int? = AppConfigStore.setting(key, "").trim().toIntOrNull()
    private fun v(key: String, def: Int, min: Int, max: Int): Int = (num(key) ?: def).coerceIn(min, max)

    val diceRollMs: Int get() = v("t_dice_roll_ms", 380, 100, 3000)     // dice ghoomne ka waqt
    val diceAfterMs: Int get() = v("t_dice_after_ms", 250, 0, 2000)     // dice ruknay ke baad pause
    val tokenStepMs: Int get() = v("t_token_step_ms", 220, 60, 1000)    // token ek cell chalne ka waqt
    val tokenGlideMs: Int get() = minOf(v("t_token_glide_ms", 160, 60, 800), tokenStepMs)  // ek step ki smooth slide
    val tokenOutMs: Int get() = v("t_token_out_ms", 140, 60, 1000)      // yard se nikalna
    val arrowMs: Int get() = v("t_arrow_ms", 480, 100, 2000)            // arrow jump
    val killMs: Int get() = v("t_kill_ms", 1200, 200, 4000)             // kill flash
    val rocketMs: Int get() = num("t_rocket_ms")?.coerceIn(200, 3000) ?: RocketTuning.durationMs.value
    val rocketLift: Int get() = num("t_rocket_lift")?.coerceIn(0, 200) ?: RocketTuning.liftDp.value

    // Panel (Game tab) se: sab dice / sab token ka size (%), 100 = jaisa abhi hai
    val diceScalePct: Int get() = v("t_dice_scale", 100, 30, 300)
    val tokenScalePct: Int get() = v("t_token_scale", 100, 40, 250)

    // ---- Dice ka look (panel Game tab se) ----
    val diceRollZoomPct: Int get() = v("t_dice_roll_zoom", 150, 100, 250)   // ghoomte waqt dice kitna bada dikhe
    val diceRadiusDp: Int get() = v("t_dice_radius", 10, 0, 30)             // dice box ke kone
    val diceBgAlphaPct: Int get() = v("t_dice_bg_alpha", 12, 0, 100)        // dice box ke peeche ka rang kitna gehra
    val diceBgColor: Color get() =
        parseConfigColor(AppConfigStore.setting("t_dice_bg_color", "#FFFFFF"), Color.White)
    fun diceBgImage(): String = AppConfigStore.element("g_diceBg").image
    fun diceBgVisible(): Boolean = AppConfigStore.element("g_diceBg").visible

    // ---- Token movement ki baaqi sab cheezein (panel Game tab se) ----
    val tokenPopMs: Int get() = v("t_token_pop_ms", 100, 20, 600)           // yard se nikalte waqt "pop-in"
    val tokenSizeMs: Int get() = v("t_token_size_ms", 220, 20, 800)         // ek cell mein kai token aane par size badalna
    val yardMs: Int get() = v("t_yard_ms", 230, 20, 1000)                   // yard ke andar token ki harkat
    val arrowAfterMs: Int get() = v("t_arrow_after_ms", 160, 0, 1500)       // arrow jump ke baad choti ruk
    // 0 = smooth (shuru/aakhir mein dheema), 1 = seedhi barabar speed, 2 = aakhir mein dheema, 3 = shuru mein dheema
    val tokenEase: Easing get() = when (num("t_token_ease") ?: 0) {
        1 -> LinearEasing
        2 -> LinearOutSlowInEasing
        3 -> FastOutLinearInEasing
        else -> FastOutSlowInEasing
    }

    // Mic / chat icon ki images (panel mein khali = app ki apni image)
    fun micImage(on: Boolean): String = AppConfigStore.element(if (on) "g_micOn" else "g_micOff").image
    fun chatImage(): String = AppConfigStore.element("g_chat").image

    // Dice ki images (panel mein khali = app ki apni image)
    fun diceFace(value: Int, default: String): String =
        AppConfigStore.element("g_dice$value").image.ifBlank { default }
    fun diceRollImage(default: String): String =
        AppConfigStore.element("g_diceRoll").image.ifBlank { default }
}
