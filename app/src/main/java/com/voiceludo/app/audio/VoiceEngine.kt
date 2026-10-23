package com.voiceludo.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.voiceludo.app.net.BackendClient
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// ============================================================================
// Live voice chat (mic relay) — backend ke VOICE_PROTOCOL.md ke mutabiq.
//
//  App -> Server : BINARY frame (har 20 ms): [2 byte seq][2 byte predictor][1 byte index][160 byte ADPCM]
//  Server -> App : BINARY frame: [1 byte: bolne wale ka slot][wohi payload]
//                  slot: 0=RED 1=GREEN 2=YELLOW 3=BLUE
//
// Codec: IMA-ADPCM (4 bit/sample), 16 kHz mono — ~66 kbps, koi native library nahi chahiye.
// Har frame apna predictor/index khud le kar chalta hai, isliye koi frame drop ho jaye
// to agla frame foran theek ho jata hai (awaz toot-phoot nahi bigadti).
// ============================================================================
object VoiceEngine {
    private const val TAG = "VoiceEngine"
    const val SAMPLE_RATE = 16000
    private const val FRAME_SAMPLES = 320                       // 20 ms
    private const val HEADER = 5                                // seq(2) + predictor(2) + index(1)
    private const val PAYLOAD_SIZE = HEADER + FRAME_SAMPLES / 2 // 165 bytes
    const val SELF_SLOT = -1

    private val main = Handler(Looper.getMainLooper())

    // ---- UI ke liye (Compose state) ----
    // User ne mic chalu rakha hai ya band (permission se alag).
    var micOn by mutableStateOf(true)
        private set
    // RECORD_AUDIO permission mili hui hai ya nahi.
    var hasPermission by mutableStateOf(false)
        private set
    // Abhi asal mein mic chal raha hai (permission bhi hai AUR user ne on rakha hai).
    val micEffective: Boolean get() = micOn && hasPermission
    // Jo slots (0..3) abhi bol rahe hain; SELF_SLOT = main khud.
    var speaking by mutableStateOf<Set<Int>>(emptySet())
        private set

    @Volatile private var running = false
    @Volatile private var muted = false
    @Volatile private var capturing = false
    private var captureThread: Thread? = null

    private val lastHeard = ConcurrentHashMap<Int, Long>()
    @Volatile private var selfLevelUntil = 0L

    private var audioManager: AudioManager? = null
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeaker = false
    private var changedAudioMode = false

    private val players = arrayOfNulls<Player>(4)

    // ------------------------------------------------------------------
    // Start / stop
    // ------------------------------------------------------------------
    fun start(context: Context) {
        val app = context.applicationContext
        hasPermission = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!running) {
            running = true
            muted = !micOn
            routeAudio(app)
            main.post(sweeper)
        }
        if (hasPermission) startCapture()
    }

    // Runtime permission dialog ka natija.
    fun onPermissionResult(context: Context, granted: Boolean) {
        hasPermission = granted
        if (granted && running) {
            micOn = true
            muted = false
            startCapture()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        capturing = false
        main.removeCallbacks(sweeper)
        val t = captureThread
        captureThread = null
        try { t?.join(600) } catch (_: InterruptedException) {}
        synchronized(players) {
            for (i in players.indices) {
                players[i]?.release()
                players[i] = null
            }
        }
        lastHeard.clear()
        restoreAudio()
        speaking = emptySet()
    }

    // Mic on/off. Naya effective status wapis deta hai (permission na ho to false).
    fun setMic(on: Boolean): Boolean {
        micOn = on
        muted = !on
        if (!on) selfLevelUntil = 0L
        return micEffective
    }

    fun toggleMic(): Boolean = setMic(!micOn)

    // ------------------------------------------------------------------
    // Awaz ka rasta (speaker/earpiece)
    // ------------------------------------------------------------------
    private fun headsetConnected(am: AudioManager): Boolean {
        return try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                when (it.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> true
                    else -> false
                }
            }
        } catch (_: Exception) { false }
    }

    private fun routeAudio(app: Context) {
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            savedMode = am.mode
            savedSpeaker = am.isSpeakerphoneOn
            // MODE_IN_COMMUNICATION se phone ka echo-canceller sahi kaam karta hai.
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            // Headphone/Bluetooth laga ho to unhi par chalne do, warna loudspeaker.
            am.isSpeakerphoneOn = !headsetConnected(am)
            changedAudioMode = true
        } catch (e: Exception) {
            Log.w(TAG, "routeAudio failed: ${e.message}")
        }
    }

    private fun restoreAudio() {
        if (!changedAudioMode) return
        try {
            audioManager?.let {
                it.isSpeakerphoneOn = savedSpeaker
                it.mode = savedMode
            }
        } catch (_: Exception) {}
        changedAudioMode = false
    }

    // ------------------------------------------------------------------
    // Capture (mic -> server)
    // ------------------------------------------------------------------
    @Synchronized
    private fun startCapture() {
        if (capturing || !running) return
        capturing = true
        val t = Thread({ captureLoop() }, "voice-capture")
        captureThread = t
        t.start()
    }

    private fun captureLoop() {
        var rec: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) { Log.w(TAG, "bad min buffer"); return }
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, FRAME_SAMPLES * 2 * 4)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) { Log.w(TAG, "AudioRecord init failed"); return }
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(rec.audioSessionId)
                    aec?.enabled = true
                }
            } catch (_: Exception) {}
            try {
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(rec.audioSessionId)
                    ns?.enabled = true
                }
            } catch (_: Exception) {}

            val pcm = ShortArray(FRAME_SAMPLES)
            val enc = AdpcmEncoder()
            var seq = 0
            var recording = false

            while (running) {
                if (muted) {
                    if (recording) {
                        try { rec.stop() } catch (_: Exception) {}
                        recording = false
                    }
                    try { Thread.sleep(60) } catch (_: InterruptedException) { break }
                    continue
                }
                if (!recording) {
                    try {
                        rec.startRecording()
                        recording = rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    } catch (_: IllegalStateException) {
                        recording = false
                    }
                    if (!recording) {
                        try { Thread.sleep(200) } catch (_: InterruptedException) { break }
                        continue
                    }
                }
                var got = 0
                while (got < FRAME_SAMPLES && running && !muted) {
                    val n = rec.read(pcm, got, FRAME_SAMPLES - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < FRAME_SAMPLES) {
                    try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                    continue
                }

                // awaz ka level: bol rahe hon to apni profile par green ring
                var sum = 0L
                for (s in pcm) sum += abs(s.toInt())
                if (sum / FRAME_SAMPLES > 450) selfLevelUntil = System.currentTimeMillis() + 350L

                BackendClient.sendVoiceFrame(enc.encode(pcm, seq))
                seq = (seq + 1) and 0xFFFF
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture error: ${e.message}")
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            try { aec?.release() } catch (_: Exception) {}
            try { ns?.release() } catch (_: Exception) {}
            try { rec?.release() } catch (_: Exception) {}
            capturing = false
        }
    }

    // ------------------------------------------------------------------
    // Playback (server -> speaker)
    // ------------------------------------------------------------------
    // BackendClient ke binary callback se: [slot][payload].
    fun onIncoming(data: ByteArray) {
        if (!running || data.size != 1 + PAYLOAD_SIZE) return
        val slot = data[0].toInt()
        if (slot !in 0..3) return
        val pcm = AdpcmDecoder.decode(data, 1) ?: return
        lastHeard[slot] = System.currentTimeMillis()
        val p = synchronized(players) {
            players[slot] ?: Player().also { players[slot] = it }
        }
        p.offer(pcm)
    }

    private class Player {
        private val queue = ArrayBlockingQueue<ShortArray>(16)
        @Volatile private var alive = true
        private var track: AudioTrack? = null
        private val thread: Thread

        init {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, FRAME_SAMPLES * 2 * 6))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            thread = Thread({ loop() }, "voice-play")
            thread.start()
        }

        fun offer(pcm: ShortArray) {
            // Latency badhne se bachao: peeche bahut frame jama hon to purane phenk do.
            if (queue.size >= 10) queue.clear()
            queue.offer(pcm)
        }

        private fun loop() {
            val t = track ?: return
            var started = false
            try {
                while (alive) {
                    val f = queue.poll(200, TimeUnit.MILLISECONDS)
                    if (f == null) {
                        // khamoshi — agli dafa naye sire se (kam delay ke saath) shuru karo
                        if (started) {
                            try { t.pause(); t.flush() } catch (_: Exception) {}
                            started = false
                        }
                        continue
                    }
                    t.write(f, 0, f.size)
                    if (!started) {
                        // 2 frame (~40 ms) jama hone ka thora sa intezaar, phir play
                        val deadline = System.currentTimeMillis() + 80
                        while (alive && queue.size < 2 && System.currentTimeMillis() < deadline) {
                            Thread.sleep(5)
                        }
                        t.play()
                        started = true
                    }
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                Log.w(TAG, "playback error: ${e.message}")
            }
        }

        fun release() {
            alive = false
            thread.interrupt()
            try { thread.join(300) } catch (_: InterruptedException) {}
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
        }
    }

    // ------------------------------------------------------------------
    // "Kaun bol raha hai" — profile par green ring ke liye
    // ------------------------------------------------------------------
    private val sweeper = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            val s = HashSet<Int>()
            for ((slot, t) in lastHeard) if (now - t < 450) s.add(slot)
            if (now < selfLevelUntil && !muted) s.add(SELF_SLOT)
            if (s != speaking) speaking = s
            main.postDelayed(this, 150)
        }
    }

    // ------------------------------------------------------------------
    // IMA-ADPCM
    // ------------------------------------------------------------------
    private val STEP = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
        253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
        1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
        3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487,
        12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )
    private val INDEX_ADJ = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)

    private class AdpcmEncoder {
        private var pred = 0
        private var idx = 0

        fun encode(pcm: ShortArray, seq: Int): ByteArray {
            val out = ByteArray(PAYLOAD_SIZE)
            out[0] = (seq shr 8).toByte()
            out[1] = seq.toByte()
            out[2] = (pred shr 8).toByte()
            out[3] = pred.toByte()
            out[4] = idx.toByte()
            var o = HEADER
            var i = 0
            while (i < FRAME_SAMPLES) {
                val a = sample(pcm[i].toInt())
                val b = sample(pcm[i + 1].toInt())
                out[o++] = (a or (b shl 4)).toByte()
                i += 2
            }
            return out
        }

        private fun sample(s: Int): Int {
            val step = STEP[idx]
            var diff = s - pred
            var code = 0
            if (diff < 0) { code = 8; diff = -diff }
            var temp = step
            var vp = step shr 3
            if (diff >= temp) { code = code or 4; diff -= temp; vp += step }
            temp = temp shr 1
            if (diff >= temp) { code = code or 2; diff -= temp; vp += step shr 1 }
            temp = temp shr 1
            if (diff >= temp) { code = code or 1; vp += step shr 2 }
            pred += if ((code and 8) != 0) -vp else vp
            if (pred > 32767) pred = 32767
            if (pred < -32768) pred = -32768
            idx += INDEX_ADJ[code and 7]
            if (idx < 0) idx = 0
            if (idx > 88) idx = 88
            return code
        }
    }

    private object AdpcmDecoder {
        // data[offset..] = [seq2][pred2][idx1][160 byte nibbles]
        fun decode(data: ByteArray, offset: Int): ShortArray? {
            if (data.size < offset + PAYLOAD_SIZE) return null
            var pred = ((data[offset + 2].toInt() shl 8) or (data[offset + 3].toInt() and 0xFF)).toShort().toInt()
            var idx = data[offset + 4].toInt() and 0xFF
            if (idx > 88) idx = 88
            val out = ShortArray(FRAME_SAMPLES)
            var o = 0
            var p = offset + HEADER
            for (n in 0 until FRAME_SAMPLES / 2) {
                val b = data[p++].toInt() and 0xFF
                for (k in 0..1) {
                    val code = if (k == 0) (b and 0x0F) else (b shr 4)
                    val step = STEP[idx]
                    var vp = step shr 3
                    if ((code and 4) != 0) vp += step
                    if ((code and 2) != 0) vp += step shr 1
                    if ((code and 1) != 0) vp += step shr 2
                    pred += if ((code and 8) != 0) -vp else vp
                    if (pred > 32767) pred = 32767
                    if (pred < -32768) pred = -32768
                    idx += INDEX_ADJ[code and 7]
                    if (idx < 0) idx = 0
                    if (idx > 88) idx = 88
                    out[o++] = pred.toShort()
                }
            }
            return out
        }
    }
}
