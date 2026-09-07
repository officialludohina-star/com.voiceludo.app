package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore
import com.voiceludo.app.ui.common.LoadingOverlay

// Same jungle/moon background jo baaki saari screens (Login, Sign Up, Home) mein use hoti hai
private const val JUNGLE_MOON_BG = "file:///android_asset/img/login_bg.png"

// Asal HTML ke "setPass" screen jaisa hi — naya password set karke account banata hai.
// method=="gmail" ho to ab REAL bekend (BackendClient.signup) par account banta hai —
// koi fake local account nahi. Password par pehle jo sakht rule tha (8 chars + letter +
// number + special-char zaroori) woh hata diya gaya — jo bhi password lagao who chal
// jayega (bekend khud sirf itna check karta hai ke kam se kam 6 characters ho).
@Composable
fun SetPasswordScreen(navController: NavController, method: String, contact: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var newPass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var createdId by remember { mutableStateOf<String?>(null) }
    var idCopied by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // Sirf gmail method ke waqt hi bekend se signup call ka jawab sunna hai
    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Auth -> {
                    loading = false
                    AccountStore.saveSession(context, "gmail", contact)
                    SessionStore.save(context, msg.playerId, msg.authToken, msg.name, msg.avatar, msg.coins, msg.diamonds, msg.gameId)
                    createdId = msg.gameId.toString()
                }
                is ServerMessage.Err -> {
                    loading = false
                    errorText = msg.message
                }
                is ServerMessage.ConnectionClosed -> {
                    if (loading) {
                        loading = false
                        errorText = "Could not connect to server: ${msg.reason}"
                    }
                }
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose { BackendClient.removeListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = JUNGLE_MOON_BG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            // Reference jaisa hi teal gradient header, back-arrow ke saath
            LoginHeaderBar(
                "Set Password",
                headerColor = Color(0xFF2ea87f),
                useBackArrow = true
            ) { navController.popBackStack() }

            LoginCard {
                if (createdId != null) {
                    Text(
                        "\uD83C\uDF89 Account Created!",
                        color = Color(0xFF0a7a42), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Aapki Ludo ID: $createdId",
                        color = Color(0xFF222222), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (idCopied) "\u2713 Copied!" else "\uD83D\uDCCB Tap to copy ID",
                        color = if (idCopied) Color(0xFF0a7a42) else Color(0xFF2ea87f),
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                createdId?.let {
                                    clipboard.setText(AnnotatedString(it))
                                    idCopied = true
                                }
                            },
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val loginRoute = if (method == "mobile") "vp_mobile_login" else "vp_gmail_login"
                            navController.popBackStack(loginRoute, inclusive = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0a7a42)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text("Login karein", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    // Reference jaisa ek hi "New Password" field, eye-toggle ke saath
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; errorText = "" },
                        placeholder = { Text("New Password", color = Color(0xFF9aa89e)) },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            EyeToggleIcon(
                                visible = passVisible,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clickable { passVisible = !passVisible }
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFFc8e6c9),
                            unfocusedBorderColor = Color(0xFFc8e6c9)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Koi bhi password lagayen (kam se kam 6 characters).",
                        color = Color(0xFF0a7a42), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorText, color = Color(0xFFcc3333), fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFfdecec), RoundedCornerShape(8.dp)).padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            when {
                                newPass.length < 6 ->
                                    errorText = "Password kam se kam 6 characters ka ho."
                                method == "gmail" -> {
                                    loading = true
                                    errorText = ""
                                    BackendClient.signup(contact, newPass)
                                }
                                else -> {
                                    // Mobile/Facebook abhi bekend par nahi hain (bekend sirf
                                    // email/password accounts sambhalta hai) — is liye woh
                                    // filhal on-device account ki tarah hi chalte hain.
                                    val account = AccountStore.createAccount(context, method, contact, newPass)
                                    createdId = account.idNumber
                                }
                            }
                        },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFbdbdbd)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF3a3a3a), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm", color = Color(0xFF3a3a3a), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        if (loading) {
            LoadingOverlay("Creating account...")
        }
    }
}
