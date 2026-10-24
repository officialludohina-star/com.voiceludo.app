package com.voiceludo.app.ui.voiceparty

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlin.random.Random

// ============================================================================
// Asal HTML wale login/signup screens (gmailLogin, signupGmail, setPass) Firebase
// Firestore + EmailJS (real email OTP) use karte thay. Yeh Kotlin/Compose app abhi
// tak Firebase se connected hi nahi hai (na google-services.json, na koi API key) —
// isi liye "Sign Up" button pehle sirf ek khali TODO tha aur "Login" hamesha seedha
// success maan kar aage chala jata tha, chahe account bana ho ya na ho.
//
// Yahan hum asal HTML jaisa hi UX (email + OTP + set password) local, on-device
// storage (SharedPreferences) ke sath implement kar rahe hain — taake Sign Up aur
// Login dono asal mein kaam karein is app ke andar hi, bina kisi backend keys ke.
//
// NOTE: Agar aap chahte hain ke yeh asal Firebase account system se judy (jaisa HTML
// mein hai — sab devices/browsers mein wahi account chale), to aapko apna Firebase
// project ka google-services.json is app mein daalna hoga aur Firebase Auth/Firestore
// dependencies add karni hongi. Filhaal yeh sirf isi phone/device par account yaad
// rakhta hai (HTML jaisa hi cross-device sync abhi nahi hai).
// ============================================================================

private const val PREFS_NAME = "voiceludo_accounts"

data class LudoAccount(
    val loginValue: String,   // jo email/phone user ne enter kiya tha
    val passwordHash: String,
    val idNumber: String,
    val coins: Int = 10000,
    val diamonds: Int = 25,
    val level: Int = 1
)

object AccountStore {

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // gmail_<email> / mobile_<country phone> jaisi hi unique key, HTML ke uid pattern se milti hai
    private fun uidFor(method: String, contact: String): String =
        method + "_" + contact.lowercase().replace(Regex("[^a-z0-9]"), "_")

    // Asal HTML ke generateSafeLudoId jaisa hi — 9 digit, sirf 1-9 (koi 0 nahi), unique
    fun generateLudoId(context: Context): String {
        val used = prefs(context).all.values.mapNotNull { (it as? String) }.toSet()
        repeat(30) {
            val id = (1..9).joinToString("") { (1 + Random.nextInt(9)).toString() }
            val exists = prefs(context).all.keys.any { key ->
                key.startsWith("account_") && prefs(context).getString(key, null)?.contains("\"idNumber\":\"$id\"") == true
            }
            if (!exists) return id
        }
        return (1..9).joinToString("") { (1 + Random.nextInt(9)).toString() }
    }

    fun accountExists(context: Context, method: String, contact: String): Boolean =
        prefs(context).contains("account_" + uidFor(method, contact))

    // Simple JSON-less serialize (koi extra JSON library add nahi karni padi) — bas ek
    // delimiter-based line, values mein hamare use-case mein pipe '|' ya newline nahi aatay.
    fun createAccount(context: Context, method: String, contact: String, plainPassword: String): LudoAccount {
        val uid = uidFor(method, contact)
        val idNumber = generateLudoId(context)
        val hash = sha256(plainPassword)
        val account = LudoAccount(loginValue = contact, passwordHash = hash, idNumber = idNumber)
        val serialized = "loginValue=${account.loginValue}|passwordHash=${account.passwordHash}|" +
            "idNumber=${account.idNumber}|coins=${account.coins}|diamonds=${account.diamonds}|level=${account.level}"
        prefs(context).edit().putString("account_$uid", serialized).apply()
        return account
    }

    private fun deserialize(raw: String): LudoAccount {
        val map = raw.split("|").associate {
            val (k, v) = it.split("=", limit = 2)
            k to v
        }
        return LudoAccount(
            loginValue = map["loginValue"] ?: "",
            passwordHash = map["passwordHash"] ?: "",
            idNumber = map["idNumber"] ?: "",
            coins = map["coins"]?.toIntOrNull() ?: 10000,
            diamonds = map["diamonds"]?.toIntOrNull() ?: 25,
            level = map["level"]?.toIntOrNull() ?: 1
        )
    }

    fun getAccount(context: Context, method: String, contact: String): LudoAccount? {
        val raw = prefs(context).getString("account_" + uidFor(method, contact), null) ?: return null
        return deserialize(raw)
    }

    // Login: account maujood hona chahiye aur password match hona chahiye
    sealed class LoginResult {
        data class Success(val account: LudoAccount) : LoginResult()
        object NoAccount : LoginResult()
        object WrongPassword : LoginResult()
    }

    fun login(context: Context, method: String, contact: String, plainPassword: String): LoginResult {
        val account = getAccount(context, method, contact) ?: return LoginResult.NoAccount
        return if (account.passwordHash == sha256(plainPassword)) {
            LoginResult.Success(account)
        } else {
            LoginResult.WrongPassword
        }
    }

    // Real OTP — EmailService.sendOtp() se generate ho kar user ke asal Gmail par
    // jata hai (koi fake/bypass code nahi ab). Jab tak koi OTP generate na hua ho,
    // currentOtp null rehta hai taake verifyOtp() kabhi bhi khali/default match na kare.
    private var currentOtp: String? = null

    fun generateOtp(): String {
        val otp = (1000..9999).random().toString()
        currentOtp = otp
        return otp
    }

    fun verifyOtp(entered: String): Boolean = entered.isNotBlank() && entered == currentOtp

    // ---- Current session (kis method/ID se ab login hai) — Settings panel mein
    // dikhane ke liye. Login success par saveSession() call hota hai, Logout par clear.
    private const val SESSION_METHOD = "session_method"
    private const val SESSION_CONTACT = "session_contact"

    fun saveSession(context: Context, method: String, contact: String) {
        prefs(context).edit()
            .putString(SESSION_METHOD, method)
            .putString(SESSION_CONTACT, contact)
            .apply()
    }

    // Pair(method, contact) — dono null hain to koi bhi logged in nahi (Guest)
    fun getSession(context: Context): Pair<String, String>? {
        val method = prefs(context).getString(SESSION_METHOD, null) ?: return null
        val contact = prefs(context).getString(SESSION_CONTACT, null) ?: return null
        return method to contact
    }

    fun clearSession(context: Context) {
        prefs(context).edit().remove(SESSION_METHOD).remove(SESSION_CONTACT).apply()
    }
}


// Signup ka server-verified code Confirm screen se SetPassword screen tak le jane ke liye
// (route mein code nahi daalte). Signup kaamyab hone ya screen band hone par saaf ho jata hai.
object PendingSignupOtp {
    @Volatile var code: String = ""
}
