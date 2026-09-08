package com.voiceludo.app.ui.ludo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.audio.SoundManager

// Asal "#ludoModeScreen" ka hoobahoo design — tabs (1 ON 1 / 4 Players), Select Mode
// card (Classic/Arrow/Quick/Master), Select Amount card (bet +/-, total pool), Magic
// toggle, aur Start button. Rangeen gradients/icons HTML se seedhe copy kiye gaye hain.
@Composable
fun LudoModeSelectScreen(navController: NavController) {
    var mode by remember { mutableStateOf(LudoMode.CLASSIC) }
    var players by remember { mutableStateOf(2) }
    var betIndex by remember { mutableStateOf(0) }
    var magicOn by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val bet = BET_OPTIONS[betIndex]
    val totalPool = bet * players

    // Safety: agar kisi wajah se lobby music abhi tak baj rahi ho (jaise
    // "wapis" aakar isi screen par dobara pohanchna), yahan hamesha rok di
    // jati hai — yeh (bet select) screen "lobby" nahi hai, lobby sirf
    // matching/waiting screen hai.
    LaunchedEffect(Unit) { SoundManager.stopLobbyMusic() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0a1a2a))) {
        AsyncImage(
            model = MODE_SCREEN_BG_IMG,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TABS: 1 ON 1 / 4 Players
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeTab(
                    iconUrl = MODE_2P_ICON, label = "1 ON 1",
                    active = players == 2, onClick = { players = 2 }
                )
                ModeTab(
                    iconUrl = MODE_4P_ICON, label = "4 Players",
                    active = players == 4, onClick = { players = 4 }
                )
            }

            // SELECT MODE CARD
            LudoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Text("Select Mode", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { SoundManager.playClick(); showHelp = true },
                        contentAlignment = Alignment.Center
                    ) { Text("?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ModeButton("Classic", mode == LudoMode.CLASSIC, Modifier.weight(1f)) { mode = LudoMode.CLASSIC }
                    ModeButton("Arrow", mode == LudoMode.ARROW, Modifier.weight(1f), showHot = true) { mode = LudoMode.ARROW }
                    ModeButton("Quick", mode == LudoMode.QUICK, Modifier.weight(1f)) { mode = LudoMode.QUICK }
                    ModeButton("Master", mode == LudoMode.MASTER, Modifier.weight(1f)) { mode = LudoMode.MASTER }
                }
            }

            // SELECT AMOUNT CARD
            LudoCard {
                Text(
                    "Select Amount", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = if (betIndex <= 0) BET_MINUS_ICON_DISABLED else BET_MINUS_ICON,
                        contentDescription = "kam karo",
                        modifier = Modifier.size(40.dp).clickable {
                            SoundManager.playClick()
                            if (betIndex > 0) betIndex--
                        }
                    )
                    Spacer(Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        AsyncImage(model = COIN_ICON, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(bet.toString(), color = Color(0xFFffe066), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    AsyncImage(
                        model = if (betIndex >= BET_OPTIONS.lastIndex) BET_PLUS_ICON_DISABLED else BET_PLUS_ICON,
                        contentDescription = "zyada karo",
                        modifier = Modifier.size(40.dp).clickable {
                            SoundManager.playClick()
                            if (betIndex < BET_OPTIONS.lastIndex) betIndex++
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Total Pool: $totalPool", color = Color(0xFFcce6ff), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // 4-player mein 1st (60%) / 2nd (40%) prize split, rank badge icons ke sath —
                // asal HTML ke renderPoolAndPrize() se
                if (players == 4) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(model = RANK_1_ICON, contentDescription = "1st prize", modifier = Modifier.size(20.dp))
                            Text((totalPool * 0.6).let { Math.round(it) }.toString(), color = Color(0xFF7fffbf), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(model = RANK_2_ICON, contentDescription = "2nd prize", modifier = Modifier.size(20.dp))
                            Text((totalPool * 0.4).let { Math.round(it) }.toString(), color = Color(0xFFffe066), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // MAGIC TOGGLE CARD
            LudoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { SoundManager.playClick(); magicOn = !magicOn },
                        contentAlignment = Alignment.Center
                    ) { if (magicOn) Text("\u2713", color = Color(0xFF7fffbf), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(model = GOLDEN_DICE_ICON, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Magic", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
            }

            Spacer(Modifier.height(40.dp))

            // START BUTTON — asal "Start" image (text image ke andar hi baked-in hai).
            // aspectRatio image ke asal ratio (2172x724) ke mutabiq rakha hai taake
            // pill squished/stretched na dikhe, aur upar wala gap barha ke thora
            // neechay kiya hai.
            AsyncImage(
                model = START_BUTTON_IMG,
                contentDescription = "Start",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(2172f / 724f)
                    .clickable {
                        SoundManager.playClick()
                        navController.navigate("ludo_matching/${mode.name}/$players/$magicOn/$betIndex")
                    }
            )

            Spacer(Modifier.height(20.dp))
        }

        // CLOSE BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 12.dp)
                .size(width = 44.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFff7a2a), Color(0xFFc93a0a))))
                .clickable {
                    SoundManager.playClick()
                    navController.popBackStack()
                },
            contentAlignment = Alignment.Center
        ) { Text("\u2715", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
    }

    if (showHelp) {
        RulesDialog(onDismiss = { showHelp = false })
    }
}

// "Rules" popup — Classic tab mein user ki di hui asal English rules text hai
// (unki screenshots se, sirf text — koi board/photo image nahi lagayi). Arrow/
// Quick/Master ke liye abhi tak sirf short one-line summary di hui thi, wahi
// rakhi hai kyunke unka poora rules text nahi mila.
@Composable
private fun RulesDialog(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(LudoMode.CLASSIC) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF3ad9ac), Color(0xFF1a9e7a))))
                    .padding(vertical = 18.dp)
            ) {
                Text(
                    "Rules",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(26.dp)
                        .clickable { SoundManager.playClick(); onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("\u2715", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            }

            // Tabs
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                listOf(
                    "Classic" to LudoMode.CLASSIC,
                    "Arrow" to LudoMode.ARROW,
                    "Quick" to LudoMode.QUICK,
                    "Master" to LudoMode.MASTER
                ).forEach { (label, m) ->
                    val active = tab == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFFe2f7ef) else Color(0xFFf1f1f1))
                            .clickable { SoundManager.playClick(); tab = m }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color(0xFF1a9e7a) else Color(0xFF9a9a9a),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (tab) {
                    LudoMode.CLASSIC -> {
                        RuleSection(
                            "MOVING OUT OF NEST",
                            "A takeoff will take place when you roll a six. Rolling a six also gives you an extra turn to roll the dice."
                        )
                        RuleSection(
                            "WINNING THE GAME",
                            "You win when all of your four tokens reach the end first."
                        )
                        RuleSection(
                            "REWARD OF KILLING",
                            "Tokens of different colors can kill each other. You will get an EXTRA TURN to roll the dice if you kill an opponent's token."
                        )
                        RuleSection(
                            "SAFE SQUARES",
                            "Tokens on the start squares and star squares are protected and cannot be killed."
                        )
                        RuleSection(
                            "PUNISHMENT OF ROLLING THREE CONSECUTIVE SIX",
                            "If you roll a six three times then your turn ends, remember to reset after the third time you roll a six."
                        )
                        RuleSection(
                            "MOVING TO THE ENDING TRACK",
                            "On the ending track, you can proceed only when the point is equal or is less than the distance from the end."
                        )
                    }
                    LudoMode.ARROW -> RuleSection("ARROW", "Fast — tokens move quicker across the board.")
                    LudoMode.QUICK -> RuleSection("QUICK", "Short — a reduced board for quicker matches.")
                    LudoMode.MASTER -> RuleSection("MASTER", "Pro — the advanced ruleset for experienced players.")
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RuleSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFe2f7ef))
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text(
                title,
                color = Color(0xFF14503f),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color(0xFF3a4a4a), fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@Composable
private fun ModeTab(iconUrl: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) Brush.verticalGradient(listOf(Color(0xFF3ad4ff), Color(0xFF1a7aff)))
                else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f)))
            )
            .clickable { SoundManager.playClick(); onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        AsyncImage(
            model = iconUrl, contentDescription = label,
            modifier = Modifier
                .padding(top = 18.dp) // icon ko thora aur nichy karne ke liye (user ne mangi thi)
                .fillMaxWidth()
                .height(56.dp)
        )
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LudoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 18.dp, start = 14.dp, end = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1a4a7a), Color(0xFF0f3a5a))))
            .padding(vertical = 14.dp, horizontal = 12.dp),
        content = content
    )
}

@Composable
private fun ModeButton(label: String, active: Boolean, modifier: Modifier = Modifier, showHot: Boolean = false, onClick: () -> Unit) {
    Box(modifier = modifier.clickable { SoundManager.playClick(); onClick() }) {
        // Box ka background hi asal image hai (select image mein gold border +
        // corner checkmark ribbon pehle se baked-in hai, unselect plain blue box) —
        // upar sirf game ka naam likha jata hai.
        AsyncImage(
            model = if (active) MODE_BTN_SELECT_IMG else MODE_BTN_UNSELECT_IMG,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        )
        Text(
            label,
            color = if (active) Color(0xFFfff042) else Color(0xFFcfe6ff),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 2.dp)
        )

        if (showHot) {
            AsyncImage(
                model = HOT_BADGE_ICON, contentDescription = "hot",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-6).dp)
                    .size(20.dp)
            )
        }
    }
}
