package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage
import com.voiceludo.app.net.SessionStore
import com.voiceludo.app.ui.common.LoadingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val JUNGLE_MOON_BG = "file:///android_asset/img/login_bg.png"

// "Forgot Password?" ka asal, kaam karne wala flow — GmailSignupScreen jaisa
// hi real EmailJS OTP use karta hai (koi fake bypass nahi), aur verify hone
// ke baad naya password seedha REAL bekend (BackendClient.resetPassword) par
// set hota hai. Reset hote hi bekend "auth" bhi bhej deta hai — is se user
// turant logged-in ho kar seedha home par chala jata hai, dobara login karne
// ki zaroorat nahi.
@Composable
fun ForgotPasswordScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // step 1 = email+OTP, step 2 = naya password
    var step by remember { mutableStateOf(1) }

    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var sendingEmail by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }

    var newPass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.Auth -> {
                    loading = false
                    AccountStore.saveSession(context, "gmail", email)
                    SessionStore.save(context, msg.playerId, msg.authToken, msg.name, msg.avatar, msg.coins, msg.diamonds, msg.gameId)
                    done = true
                }
                is ServerMessage.OtpSent -> {
                    sendingEmail = false
                    otpSent = true
                    secondsLeft = 60
                    errorText = ""
                    scope.launch { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
                }
                is ServerMessage.Err -> {
                    loading = false
                    sendingEmail = false
                    errorText = msg.message
                    // Code galat/expire ho gaya to wapis code wale step par
                    if (msg.message.startsWith("verification code", ignoreCase = true)) step = 1
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            AuthPopupHeader("Forgot Password", onClose = { navController.popBackStack() })

            AuthPopupCard {
                if (done) {
                    Text(
                        "\u2705 Password Updated!",
                        color = Color(0xFF0a7a42), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You are now logged in.",
                        color = Color(0xFF222222), fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    AuthGoldButton(
                        text = "Continue",
                        onClick = { navController.popBackStack("vp_home", inclusive = false) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                } else if (step == 1) {
                    AuthLabeledField(
                        glyph = "\u2709\uFE0F",
                        label = "Email",
                        value = email,
                        onValueChange = { email = it; errorText = "" },
                        placeholder = "Enter email address"
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RealInput(
                            code, { code = it; errorText = "" }, "Verification Code",
                            modifier = Modifier.weight(1f)
                        )
                        if (secondsLeft == 0) {
                            AuthGoldButton(
                                text = "Obtain",
                                fontSize = 14,
                                loading = sendingEmail,
                                onClick = {
                                    if (email.isBlank()) {
                                        errorText = "Please enter your email address."
                                    } else if (!sendingEmail) {
                                        sendingEmail = true
                                        errorText = ""
                                        BackendClient.requestPasswordReset(email.trim())
                                    }
                                },
                                modifier = Modifier.width(84.dp).height(44.dp)
                            )
                        } else {
                            Text(
                                "${secondsLeft}s",
                                color = Color(0xFF6d756f), fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                    }

                    if (otpSent) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Verification code has been sent to $email",
                            color = Color(0xFF0a7a42), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                        )
                    }

                    if (errorText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorText, color = Color(0xFFcc3333), fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFfdecec), RoundedCornerShape(8.dp)).padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    AuthGoldButton(
                        text = "Confirm",
                        onClick = {
                            when {
                                email.isBlank() -> errorText = "Please enter your email address."
                                !otpSent -> errorText = "Please tap Obtain to receive a verification code."
                                code.isBlank() -> errorText = "Please enter the verification code."
                                else -> { errorText = ""; step = 2 }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                } else {
                    // Step 2 — naya password
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; errorText = "" },
                        placeholder = { Text("New Password", color = Color(0xFF9aa89e)) },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            EyeToggleIcon(
                                visible = passVisible,
                                modifier = Modifier.padding(end = 10.dp).clickable { com.voiceludo.app.audio.SoundManager.playClick(); passVisible = !passVisible }
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
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )

                    if (errorText.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            errorText, color = Color(0xFFcc3333), fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFfdecec), RoundedCornerShape(8.dp)).padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    AuthGoldButton(
                        text = "Update Password",
                        enabled = !loading,
                        loading = loading,
                        onClick = {
                            when {
                                newPass.length < 6 -> errorText = "Password must be at least 6 characters."
                                else -> {
                                    loading = true
                                    errorText = ""
                                    BackendClient.resetPassword(email.trim(), newPass, code.trim())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                }
            }
        }

        if (loading) {
            LoadingOverlay(if (step == 1) "Sending code..." else "Resetting password...")
        }
    }
}
