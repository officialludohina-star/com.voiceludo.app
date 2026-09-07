package com.voiceludo.app.net

import android.content.Context
import android.content.SharedPreferences

// ============================================================================
// Asal backend (Go WebSocket server) ka session yahan save hota hai — sirf
// yeh save hone ki wajah se hi user ek dafa login karne ke baad app band/on
// karne par dobara login/signup nahi karta (BackendClient app khulte hi yahan
// se token utha kar khud "authToken" bhej deta hai).
//
// Yeh AccountStore.kt (purana, sirf on-device demo accounts ke liye tha) se
// bilkul alag hai — yeh asal server session token store karta hai.
// ============================================================================
private const val PREFS = "voiceludo_session"

object SessionStore {
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        context: Context,
        playerId: String,
        authToken: String,
        name: String,
        avatar: String,
        coins: Long,
        diamonds: Long,
        gameId: Long = 0
    ) {
        prefs(context).edit()
            .putString("player_id", playerId)
            .putString("auth_token", authToken)
            .putString("name", name)
            .putString("avatar", avatar)
            .putLong("coins", coins)
            .putLong("diamonds", diamonds)
            .putLong("game_id", gameId)
            .apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString("auth_token", null)
    fun getPlayerId(context: Context): String? = prefs(context).getString("player_id", null)
    fun getGameId(context: Context): Long = prefs(context).getLong("game_id", 0)
    fun getName(context: Context): String = prefs(context).getString("name", "") ?: ""
    fun getAvatar(context: Context): String = prefs(context).getString("avatar", "") ?: ""
    fun getCoins(context: Context): Long = prefs(context).getLong("coins", 0)
    fun getDiamonds(context: Context): Long = prefs(context).getLong("diamonds", 0)

    fun updateWallet(context: Context, coins: Long, diamonds: Long) {
        prefs(context).edit().putLong("coins", coins).putLong("diamonds", diamonds).apply()
    }

    // Token invalid nikle (server par account/session ab valid nahi — jaise DB
    // reset ho chuki ho) to yahan se saaf kar dete hain taake app hamesha ke
    // liye "loading" mein na atki rahe, aur wapis normal login dikhaye.
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun hasSession(context: Context): Boolean = getToken(context) != null
}
