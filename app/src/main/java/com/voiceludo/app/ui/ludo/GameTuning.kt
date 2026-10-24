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

    // ---- Ek cell mein 2/3/4 token ek sath hon to (panel Game tab > "Ek jagah kai token") ----
    // Size har token ka (cell ka %). Default 74 = pehle jaisa. spread = tokens ke beech faasla (100 = pehle jaisa).
    fun stackSizePct(count: Int): Float = when {
        count <= 1 -> 100f
        count == 2 -> v("t_stack2_size", 74, 40, 100).toFloat()
        count == 3 -> v("t_stack3_size", 74, 40, 100).toFloat()
        else -> v("t_stack4_size", 74, 40, 100).toFloat()
    }
    val stackSpreadPct: Int get() = v("t_stack_spread", 100, 0, 250)
    // 1 = jis ki bari ho uska token (jo chal sakta ho) stack mein sab se upar aaye
    val stackTopOn: Boolean get() = v("t_stack_top", 1, 0, 1) == 1

    // ---- Turn ka glow (panel Game tab > "Turn ka glow") ----
    val turnGlowOn: Boolean get() = v("t_turn_glow", 1, 0, 1) == 1
    val turnGlowSizePct: Int get() = v("t_turn_glow_size", 155, 100, 260)
    // Khali = token ka apna rang
    val turnGlowColor: Color? get() {
        val raw = AppConfigStore.setting("t_turn_glow_color", "").trim()
        return if (raw.isBlank()) null else parseConfigColor(raw, Color(0xFFFFD400))
    }

    // ---- Token animation (panel Game tab > "Token animation"). "none" = pehle jaisa, koi badlav nahi ----
    val moveFx: String get() = AppConfigStore.setting("t_token_move_fx", "none").trim().ifBlank { "none" }   // none | glow | ring | pulse
    val landFx: String get() = AppConfigStore.setting("t_token_land_fx", "none").trim().ifBlank { "none" }   // none | pop | bounce | ripple
    val killFx: String get() = AppConfigStore.setting("t_token_kill_fx", "none").trim().ifBlank { "none" }   // none | shake | spin | shrink | fade
    val fxMs: Int get() = v("t_token_fx_ms", 450, 100, 1500)
    val fxColor: Color get() = parseConfigColor(AppConfigStore.setting("t_token_fx_color", "#FFD400"), Color(0xFFFFD400))

    // ---- Dice ka look (panel Game tab se) ----
    val diceRollZoomPct: Int get() = v("t_dice_roll_zoom", 150, 100, 250)   // ghoomte waqt dice kitna bada dikhe
    // Number aane par (dice ruk jaye tab) ka size aur animation — rolling se alag
    val diceResultZoomPct: Int get() = v("t_dice_result_zoom", 100, 50, 250)
    val diceResultMs: Int get() = v("t_dice_result_ms", 300, 60, 1500)
    // none | pop | bounce | shake
    val diceResultAnim: String get() = AppConfigStore.setting("t_dice_result_anim", "none").trim().ifBlank { "none" }
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
