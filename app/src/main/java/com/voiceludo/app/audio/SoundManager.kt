package com.voiceludo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.voiceludo.app.R

// ==== SoundManager ====
// Poore game ke saare sound-effects + lobby background music ka ek hi
// markazi (central) control point. Short SFX (kill, click, dice, waghera)
// SoundPool se — kam latency, overlap-friendly (ek sath kai awazein baj
// sakti hain). Lobby ka background music alag se ek looping MediaPlayer se
// (SoundPool lambi/looping cheezon ke liye theek nahi).
object SoundManager {
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Int, Int>()
    private var lobbyPlayer: MediaPlayer? = null

    // User setting se control hota hai (agar app mein "mute sounds" jaisa
    // koi toggle ho) — abhi default false (sound ON) rakha hai.
    var muted = false

    private val allSfx = intArrayOf(
        R.raw.sfx_kill, R.raw.sfx_click, R.raw.sfx_win, R.raw.sfx_dice_roll,
        R.raw.sfx_extra_dice, R.raw.sfx_opp_left, R.raw.sfx_last_sec,
        R.raw.sfx_lose, R.raw.sfx_magic_dice, R.raw.sfx_my_turn,
        R.raw.sfx_opp_turn, R.raw.sfx_rocket, R.raw.sfx_three_six,
        R.raw.sfx_token_move, R.raw.sfx_firework
    )

    // App/Activity shuru hote hi ek hi baar call karna hai (MainActivity
    // onCreate mein). Dobara call karne se kuch nahi hota (soundPool != null
    // guard).
    fun init(context: Context) {
        if (soundPool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        soundPool = pool
        val appCtx = context.applicationContext
        allSfx.forEach { resId -> soundIds[resId] = pool.load(appCtx, resId, 1) }
    }

    private fun play(resId: Int) {
        if (muted) return
        val pool = soundPool ?: return
        val id = soundIds[resId] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun playKill() = play(R.raw.sfx_kill)
    fun playClick() = play(R.raw.sfx_click)
    fun playWin() = play(R.raw.sfx_win)
    fun playDiceRoll() = play(R.raw.sfx_dice_roll)
    fun playExtraDice() = play(R.raw.sfx_extra_dice)
    fun playOppLeft() = play(R.raw.sfx_opp_left)
    fun playLastSecondTick() = play(R.raw.sfx_last_sec)
    fun playLose() = play(R.raw.sfx_lose)
    fun playMagicDice() = play(R.raw.sfx_magic_dice)
    fun playMyTurn() = play(R.raw.sfx_my_turn)
    fun playOppTurn() = play(R.raw.sfx_opp_turn)
    fun playRocket() = play(R.raw.sfx_rocket)
    fun playThreeSix() = play(R.raw.sfx_three_six)
    fun playTokenMove() = play(R.raw.sfx_token_move)
    // firework.mp3 — token ghar (home) pohanchne par, aur arrow ki tail cell
    // trigger hone par.
    fun playFirework() = play(R.raw.sfx_firework)

    // Lobby (matching/waiting screen) background music — screen mein aate hi
    // shuru, wahan se nikalte hi (matched ho jaye ya wapis chale jayein) band.
    fun startLobbyMusic(context: Context) {
        stopLobbyMusic()
        if (muted) return
        lobbyPlayer = MediaPlayer.create(context.applicationContext, R.raw.bg_lobby)?.apply {
            isLooping = true
            setVolume(0.5f, 0.5f)
            start()
        }
    }

    fun stopLobbyMusic() {
        lobbyPlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        lobbyPlayer = null
    }

    // App background chali jaye (MainActivity onPause) to lobby music turant
    // ruk jaye — pehle yeh sirf screen-level (Compose DisposableEffect) se
    // control hoti thi, jo Activity paused hone par khud nahi rukti (Compose
    // tree background mein bhi zinda rehta hai). Ab pause() se position yaad
    // rehti hai, taake app wapis foreground mein aane par (onResume) wahin se
    // continue ho jaye, restart na ho.
    fun pauseLobbyMusic() {
        lobbyPlayer?.let { runCatching { if (it.isPlaying) it.pause() } }
    }

    fun resumeLobbyMusic() {
        if (muted) return
        lobbyPlayer?.let { runCatching { if (!it.isPlaying) it.start() } }
    }

    // App band hote waqt (MainActivity onDestroy) call karna — resources free.
    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        stopLobbyMusic()
    }
}
