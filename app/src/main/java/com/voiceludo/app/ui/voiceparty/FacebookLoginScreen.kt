package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// Asal "facebookLogin" screen ka hoobahoo design — #1877f2 header, safed card-real body,
// "f" gol logo, email/password fields, Log In + "Continue with Facebook App" buttons,
// aur Forgotten password / Create account / Back-to-game links.
@Composable
fun FacebookLoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0a2f1a)),
        verticalArrangement = Arrangement.Center
    ) {
        LoginHeaderBar("Facebook Login", Color(0xFF1877f2)) { navController.popBackStack() }

        LoginCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF1877f2)),
                    contentAlignment = Alignment.Center
                ) { Text("f", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp) }
                Spacer(Modifier.height(8.dp))
                Text("Log in to Facebook", color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            RealInput(email, { email = it }, "Mobile number or email address")
            Spacer(Modifier.height(12.dp))
            RealInput(pass, { pass = it }, "Facebook password", isPassword = true)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { com.voiceludo.app.audio.SoundManager.playClick(); navController.navigate("vp_home") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877f2)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Log In", color = Color.White, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { com.voiceludo.app.audio.SoundManager.playClick() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe7f3ff)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text("Continue with Facebook App", color = Color(0xFF1877f2), fontWeight = FontWeight.Bold, fontSize = 13.sp) }

            LinkRow(
                leftText = "Forgotten password?",
                rightText = "Create new account",
                onLeft = { /* TODO: signupFb jaisi forgot-password screen abhi nahi bani */ },
                onRight = { /* TODO: signupFb (Facebook account create) screen abhi nahi bani */ }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "\u2190 Back to Game Login",
                color = Color(0xFF0a7a42),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { com.voiceludo.app.audio.SoundManager.playClick(); navController.popBackStack() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
