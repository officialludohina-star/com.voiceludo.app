package com.voiceludo.app.ui.ludo

import androidx.compose.runtime.mutableStateOf

// ==== TEMP: Rocket flight tuning ====
// Magic Rocket ki udaan (LudoGameState.animateMove aur LudoBoardCanvas dono
// mein) ke teeno zaroori numbers yahan ek jagah — taake in-game tuning panel
// dono jagah se live control kar sake. User in values ko khud slider se
// adjust kar ke dekhega, phir final numbers bata dega jo hum seedha yahan
// default bana denge (aur tuning panel hata denge).
object RocketTuning {
    // Udaan ki total duration (milliseconds). Yehi value LudoGameState ke
    // "delay(...)" aur LudoBoardCanvas ke position-tween dono mein use hoti
    // hai, taake dono hamesha sync rahein.
    val durationMs = mutableStateOf(620)

    // Udaan ke beech mein kitna upar uthta hai (dp) — arc/lift wala effect.
    val liftDp = mutableStateOf(46)

    // Udaan shuru hone se theek pehle ka chhota "frame gap" (milliseconds) —
    // taake Compose purani jagah ko theek se register kar le, warna kabhi
    // kabhi seedha final jagah par snap ho jata hai.
    val preGapMs = mutableStateOf(16)
}
