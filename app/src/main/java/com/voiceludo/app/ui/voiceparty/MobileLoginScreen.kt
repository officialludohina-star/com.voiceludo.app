package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// Asal "mobileLogin" screen — #0a7a42 header, poori 195-country dropdown (COUNTRY_LIST),
// phone/password fields, gray gradient jaisa "Login" button, Forgot Password/Sign Up links.
@Composable
fun MobileLoginScreen(navController: NavController) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(COUNTRY_LIST.first()) } // default: Pakistan
    var countryMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0a2f1a)),
        verticalArrangement = Arrangement.Center
    ) {
        LoginHeaderBar("Login", Color(0xFF0a7a42)) { navController.popBackStack() }

        LoginCard {
            // Country dropdown — click karke poori list se select karna
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFFc8e6c9), RoundedCornerShape(12.dp))
                        .clickable { com.voiceludo.app.audio.SoundManager.playClick(); countryMenuOpen = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(country, color = Color(0xFF222222), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("\u25BE", color = Color(0xFF0a7a42))
                }
                DropdownMenu(
                    expanded = countryMenuOpen,
                    onDismissRequest = { countryMenuOpen = false },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    COUNTRY_LIST.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { com.voiceludo.app.audio.SoundManager.playClick(); country = c; countryMenuOpen = false })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            RealInput(phone, { phone = it }, "Enter phone number")
            Spacer(Modifier.height(12.dp))
            RealInput(password, { password = it }, "Enter password", isPassword = true)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { com.voiceludo.app.audio.SoundManager.playClick(); navController.navigate("vp_home") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFbdbdbd)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("Login", color = Color(0xFF222222), fontWeight = FontWeight.Bold) }

            LinkRow(
                leftText = "Forgot Password?",
                rightText = "Sign Up",
                onLeft = { /* TODO: forgotMob screen abhi nahi bani */ },
                onRight = { /* TODO: signupMob screen abhi nahi bani */ }
            )
        }
    }
}
