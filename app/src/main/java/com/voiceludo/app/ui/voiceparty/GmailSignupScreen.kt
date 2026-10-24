package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.net.ServerMessage

private const val SIGNUP_BG = "file:///android_asset/img/login_bg.png"

@Composable
fun GmailSignupScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var lastOtp by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var errorText by remember { mutableStateOf("") }
    var sendingEmail by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener: (ServerMessage) -> Unit = { msg ->
            when (msg) {
                is ServerMessage.OtpSent -> {
                    sendingEmail = false
                    otpSent = true
                    secondsLeft = 60
                    errorText = ""
                    scope.launch { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
                }
                is ServerMessage.Err -> {
                    sendingEmail = false
                    errorText = msg.message
                }
                else -> {}
            }
        }
        BackendClient.addListener(listener)
        onDispose { BackendClient.removeListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = SIGNUP_BG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            AuthPopupHeader("Sign Up", onClose = { navController.popBackStack() })

            AuthPopupCard {
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
                            onClick = {
                                if (email.isBlank()) {
                                    errorText = "Please enter your email address."
                                } else if (!sendingEmail) {
                                    sendingEmail = true
                                    errorText = ""
                                    BackendClient.requestSignupOtp(email.trim())
                                }
                            },
                            modifier = Modifier.width(84.dp).height(44.dp)
                        )
                    } else {
                        Text(
                            "${secondsLeft}s",
                            modifier = Modifier.width(84.dp),
                            textAlign = TextAlign.Center,
                            color = Color(0xFF6d756f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
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
                        errorText,
                        color = Color(0xFFcc3333), fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFfdecec), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(8.dp)
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
                            else -> {
                                PendingSignupOtp.code = code.trim()
                                navController.navigate("vp_set_password/gmail/${email.trim()}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }
        }
    }
}
