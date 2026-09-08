package com.voiceludo.app.ui.voiceparty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.voiceludo.app.audio.SoundManager

// index.html ke .card-real / .input-real / header-bar patterns — Facebook/Mobile/Gmail
// teeno login screens isi styling ko share karti hain.
val CardRealBg = Color(0xFFf5fff7)
val CardRealBorder = Color(0xFF0a7a42)

@Composable
fun LoginHeaderBar(
    title: String,
    headerColor: Color = Color.Transparent,
    padding: Int = 12,
    titleSize: Int = 22,
    useBackArrow: Boolean = false,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor)
            .padding(horizontal = 14.dp, vertical = padding.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (useBackArrow) {
            Box(
                modifier = Modifier.size(40.dp).clickable { SoundManager.playClick(); onClose() },
                contentAlignment = Alignment.Center
            ) { Text("\u2190", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = titleSize.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        if (useBackArrow) {
            Spacer(Modifier.width(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2C8F72).copy(alpha = 0.82f))
                    .clickable { SoundManager.playClick(); onClose() },
                contentAlignment = Alignment.Center
            ) { Text("\u2715", color = Color.White, fontSize = 25.sp) }
        }
    }
}

// Yalla Ludo jaisa daba — gradient border (halka teal upar, gehra green nichay)
// mint/white card ke charo taraf, jo kisi bhi content height par sahi rehta hai.
val CardBorderGradient = Brush.verticalGradient(
    listOf(Color(0xFF6EE7B7), Color(0xFF17A863), Color(0xFF0a7a42))
)

// User ki di hui green card/background image — Gmail Login, Sign Up, Set/Reset
// Password, Mobile aur Facebook Login sab isi LoginCard ko share karte hain, is
// liye yahan ek jagah badalne se sab jagah apply ho jata hai.
private const val CARD_BG_IMG = "file:///android_asset/img/bghome-1295ddb.png"

@Composable
fun LoginCard(showTopBorder: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE6FBF5).copy(alpha = 0.94f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
fun RemoteImageButton(
    imageUrl: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.clickable { SoundManager.playClick(); onClick() }
    )
}

@Composable
fun RealInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFF9aa89e)) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        // Password field ke liye keyboardType=Password set karna zaroori hai —
        // warna kuch keyboards masked text par bhi autocorrect/auto-capitalize
        // laga dete hain jo silently password ghalat kar deta hai (dikhta nahi
        // kyunki text dots ke peeche chupa hota hai).
        keyboardOptions = if (isPassword) {
            KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = KeyboardCapitalization.None)
        } else {
            KeyboardOptions(capitalization = KeyboardCapitalization.None)
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Color(0xFFc8e6c9),
            unfocusedBorderColor = Color(0xFFc8e6c9)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ================= LIVE SIZE/POSITION DEBUG PANEL =================
// Chota gear (⚙) button screen par tap karo to yeh panel khul jata hai.
// Har row ke [-] [+] dabao to size/position turant phone par change hoti
// dikhegi — jo number sahi lage wo mujhe bata dena, code mein baak diya
// jayega. Panel sirf testing ke liye hai, isay kabhi bhi off kiya ja sakta hai.

@Composable
fun DebugToggleButton(visible: MutableState<Boolean>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { visible.value = !visible.value },
        contentAlignment = Alignment.Center
    ) { Text("\u2699", color = Color.White, fontSize = 18.sp) }
}

@Composable
fun DebugStepperRow(
    label: String,
    value: MutableState<Int>,
    step: Int = 4,
    range: IntRange = -150..400
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label: ${value.value}",
            color = Color.White, fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF444444))
                .clickable { value.value = (value.value - step).coerceIn(range) },
            contentAlignment = Alignment.Center
        ) { Text("\u2212", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0a7a42))
                .clickable { value.value = (value.value + step).coerceIn(range) },
            contentAlignment = Alignment.Center
        ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun DebugPanel(visible: MutableState<Boolean>, content: @Composable ColumnScope.() -> Unit) {
    if (!visible.value) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
            .heightIn(max = 260.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Size Panel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "close", color = Color(0xFF9ad6b8), fontSize = 12.sp,
                modifier = Modifier.clickable { visible.value = false }
            )
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}
// =====================================================================

@Composable
fun EyeToggleIcon(visible: Boolean, modifier: Modifier = Modifier) {
    // Ankh ka icon — password chupa ho (visible = false) to ankh par ek "/"
    // line aa jati hai, dekhna ho (visible = true) to seedhi khuli ankh.
    Box(modifier = modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Text("\uD83D\uDC41\uFE0F", fontSize = 18.sp)
        if (!visible) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .rotate(-45f)
                    .background(Color(0xFF555555))
            )
        }
    }
}

// ================= NAYA POPUP DESIGN (Login / Sign Up / Forgot Password) =================
// User ki bheji hui screenshots jaisa — purple/gold banner header, teal-bordered
// mint card, teal icon+label field, gold gradient button. Sab kuch native Compose
// drawing (Canvas/gradient/border) se bana hai — koi image, URL ya base64 nahi.

private val AuthGoldLight = Color(0xFFFFE08A)
private val AuthGold = Color(0xFFF3B93A)
private val AuthGoldDark = Color(0xFFC9861A)
private val AuthPurpleTop = Color(0xFF6B2FA0)
private val AuthPurpleBottom = Color(0xFF2C0A52)
private val AuthTealLight = Color(0xFF3FD9B0)
private val AuthTealDark = Color(0xFF0E7A57)
private val AuthCardBg = Color(0xFFEAFBF6)
private val AuthCardBorderTop = Color(0xFF8FF0D2)
private val AuthCardBorderBottom = Color(0xFF0E8A63)

// Banner ka trapezoid shape (upar chaura, niche thoda sankra) — Path se draw hota hai.
private fun bannerPath(width: Float, height: Float): Path {
    val notchTop = width * 0.20f
    val notchBottom = width * 0.10f
    return Path().apply {
        moveTo(notchTop, 0f)
        lineTo(width - notchTop, 0f)
        lineTo(width - notchBottom, height)
        lineTo(notchBottom, height)
        close()
    }
}

@Composable
fun AuthPopupHeader(title: String, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(70.dp)) {
        Canvas(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.82f).height(58.dp)) {
            val path = bannerPath(size.width, size.height)
            drawPath(path, brush = Brush.verticalGradient(listOf(AuthPurpleTop, AuthPurpleBottom)))
            drawPath(path, color = AuthGold, style = Stroke(width = 6f))
        }
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(AuthTealLight, AuthTealDark)))
                .border(3.dp, AuthGold, CircleShape)
                .clickable { SoundManager.playClick(); onClose() },
            contentAlignment = Alignment.Center
        ) { Text("\u2715", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun AuthPopupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AuthCardBg)
            .border(
                width = 3.dp,
                brush = Brush.verticalGradient(listOf(AuthCardBorderTop, AuthCardBorderBottom)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 18.dp, vertical = 22.dp),
        content = content
    )
}

// Teal icon+label box (Email/Password) seedha input field ke sath jura hua —
// screenshot mein jaisa dikhta hai waisa hi, emoji glyph use kiya hai (koi image nahi).
@Composable
fun AuthLabeledField(
    glyph: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(Brush.verticalGradient(listOf(AuthTealLight, AuthTealDark)))
                .padding(horizontal = 12.dp)
        ) {
            Text(glyph, fontSize = 17.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        RealInput(
            value, onValueChange, placeholder, isPassword = isPassword,
            modifier = Modifier.weight(1f).height(52.dp)
        )
    }
}

// Gold gradient button — "Login" / "Confirm" / "Obtain" sab isi se banta hai.
@Composable
fun AuthGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    fontSize: Int = 18
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(AuthGoldLight, AuthGold, AuthGoldDark))
                else Brush.verticalGradient(listOf(Color(0xFFdadada), Color(0xFFbdbdbd)))
            )
            .border(2.dp, if (enabled) AuthGoldDark else Color(0xFF9e9e9e), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled && !loading) { SoundManager.playClick(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF6b4a00), strokeWidth = 2.dp)
        } else {
            Text(text, color = Color(0xFF6b4a00), fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp)
        }
    }
}
// =====================================================================

@Composable
fun LinkRow(leftText: String, rightText: String, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            leftText, color = CardRealBorder, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clickable { SoundManager.playClick(); onLeft() }
        )
        Text(
            rightText, color = CardRealBorder, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clickable { SoundManager.playClick(); onRight() }
        )
    }
}
