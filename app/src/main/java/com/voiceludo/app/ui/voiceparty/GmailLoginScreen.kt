package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.GoogleSignInHelper
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore
import com.voiceludo.app.ui.common.LoadingOverlay
import com.voiceludo.app.ui.ludo.NO_CONNECTION_ICON
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGIN_BG = "file:///android_asset/img/login_bg.png"

// Ab REAL bekend (BackendClient.login) se login hota hai — koi local/fake
// account-check nahi. Bekend ka apna account (email + bcrypt password) hi
// asal source-of-truth hai, jo signup ke waqt SetPasswordScreen se bana tha.
//
// DEBUG BUILD: 10-second timeout add kiya hai (server se koi response na aaye
// to loading hamesha ke liye ghoomta nahi rahega) aur ek chhota neela debug
// text bhi dikhta hai jisse pata chalta hai server se asal mein kya mila —
// isse "stuck ho jata hai" wali problem diagnose karna aasan ho jayega.
@Composable
fun GmailLoginScreen(navController: NavController) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    // Google login: account chooser/Firebase step (isme 10s timeout nahi lagta — user ko
    // account chunne mein waqt lag sakta hai). Token milte hi normal `loading` shuru hota hai.
    var googleBusy by remember { mutableStateOf(false) }
    var googleFlow by remember { mutableStateOf(false) }
    var googleEmail by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    // WebSocket bekend se connect hi na ho paye (net na ho ya server down) — is
    // waqt baar-baar screen par NO_CONNECTION_ICON dikhaya jata hai.
    var connectionLost by remember { mutableStateOf(false) }

    // Agar loading 10 second se zyada chale (slow network / server down) to
    // khud hi timeout error dikha do — hamesha ghoomta hua spinner nahi rehna chahiye.
    LaunchedEffect(loading) {
        if (loading) {
            delay(10000)
            if (loading) {
                loading = false
                errorText = "Timeout: no response from server in 10 seconds. Please check your network."
            }
        }
    }

    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Auth -> {
                    loading = false
                    connectionLost = false
                    AccountStore.saveSession(
                        context, "gmail",
                        if (googleFlow) googleEmail.ifBlank { "Google account" } else email
                    )
                    SessionStore.save(context, msg.playerId, msg.authToken, msg.name, msg.avatar, msg.coins, msg.diamonds, msg.gameId)
                    navController.navigate("vp_home")
                }
                is ServerMessage.Err -> {
                    loading = false
                    connectionLost = false
                    errorText = if (msg.message.isNotBlank()) msg.message else "Server ne error bheja (khali message)"
                }
                is ServerMessage.ConnectionClosed -> {
                    // Pehle yahan kuch nahi hota tha — agar WebSocket connect hi na ho pae
                    // (server down/unreachable), loading hamesha ke liye ghoomta rehta tha
                    // aur koi error kabhi nazar nahi aata tha. Ab connection fail hone par
                    // seedha error + NO_CONNECTION_ICON dikha dete hain.
                    connectionLost = true
                    if (loading) {
                        loading = false
                        errorText = "Could not connect to server: ${msg.reason}"
                    }
                }
                is ServerMessage.ConnectionOpened -> connectionLost = false
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose { BackendClient.removeListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = LOGIN_BG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            AuthPopupHeader("Login", onClose = { navController.popBackStack() })

            AuthPopupCard {
                AuthLabeledField(
                    glyph = "\u2709\uFE0F",
                    label = "Email",
                    value = email,
                    onValueChange = { email = it; errorText = "" },
                    placeholder = "Enter email address"
                )

                Spacer(Modifier.height(14.dp))

                AuthLabeledField(
                    glyph = "\uD83D\uDD12",
                    label = "Password",
                    value = pass,
                    onValueChange = { pass = it; errorText = "" },
                    placeholder = "Enter password",
                    isPassword = true
                )

                if (errorText.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (connectionLost) {
                            AsyncImage(
                                model = NO_CONNECTION_ICON,
                                contentDescription = "no connection",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            errorText, color = Color(0xFFcc3333), fontSize = 11.sp,
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFfdecec), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                AuthGoldButton(
                    text = "Login",
                    enabled = pass.isNotBlank() && !loading,
                    loading = loading,
                    onClick = {
                        when {
                            email.isBlank() || pass.isBlank() ->
                                errorText = "Please enter your email and password."
                            else -> {
                                loading = true
                                errorText = ""
                                BackendClient.login(email, pass)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                )

                LinkRow(
                    leftText = "Forgot Password?",
                    rightText = "Sign Up",
                    onLeft = { navController.navigate("vp_forgot_password") },
                    onRight = { navController.navigate("vp_gmail_signup") }
                )

                Spacer(Modifier.height(14.dp))

                // Asal Google login (Firebase). Naya user ho to account khud ban jata hai.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.White)
                        .clickable(enabled = !loading && !googleBusy) {
                            com.voiceludo.app.audio.SoundManager.playClick()
                            errorText = ""
                            googleBusy = true
                            scope.launch {
                                GoogleSignInHelper.signIn(
                                    activityContext = context,
                                    onSuccess = { firebaseToken, mail ->
                                        googleBusy = false
                                        googleFlow = true
                                        googleEmail = mail
                                        loading = true
                                        BackendClient.socialLogin(firebaseToken)
                                    },
                                    onError = { msg ->
                                        googleBusy = false
                                        errorText = msg
                                    }
                                )
                            }
                        }
                ) {
                    Text(
                        "G  Continue with Google",
                        color = Color(0xFF3c4043),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Jab tak bekend se jawab (Auth ya error) na aa jaye, poori screen block —
        // aage nahi badh sakte, dobara button bhi nahi daba sakte.
        if (loading || googleBusy) {
            LoadingOverlay(if (googleBusy) "Connecting to Google..." else "Logging in...")
        }
    }
}
