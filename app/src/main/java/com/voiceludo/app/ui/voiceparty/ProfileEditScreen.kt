package com.voiceludo.app.ui.voiceparty

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.voiceludo.app.audio.SoundManager
import com.voiceludo.app.net.AppConfigStore
import com.voiceludo.app.net.BackendClient
import com.voiceludo.app.ui.common.ConfigScreenElements
import com.voiceludo.app.ui.common.RemoteArt
import com.voiceludo.app.ui.common.cfgElement
import com.voiceludo.app.ui.common.parseConfigColor
import java.io.File

// ============================================================================
// Asal HTML ke #profileEdit screen ka hoobahoo Compose version — Name, Flag,
// Gender, Bio (sab dashed rows, tap karne par ek modal khulta hai) aur upar
// Avatar + "Gallery" button (preset avatars ka grid, wahi yalla.games default
// pictures jo HTML mein thay) + device se apni photo choose karne ka option.
// Data ProfileStore (SharedPreferences) mein save hota hai, HTML ke
// window.currentUserData + Firestore setDoc jaisa hi, bas on-device.
// ============================================================================

// Default avatar placeholder (jab tak user apni photo nahi lagata) — wahi
// asal generic person-icon jo pehle se tha.
private const val DEFAULT_AVATAR = "file:///android_asset/img/user-icon.png"
// Poore screen ka background (green gradient + glow/sparkles + card frame) —
// user ne diya hua link.
private const val SCREEN_BACKGROUND_IMAGE = "https://i.postimg.cc/W4MZk05H/file-00000000076882088e096a6ce8072b70.png"
private const val UPLOAD_PHOTO_ICON = "https://i.postimg.cc/PrbZKVL7/IMG-20260831-WA0011.jpg"
private const val FEMALE_ICON = "https://i.postimg.cc/SKgfdTvw/female.webp"
private const val MALE_ICON = "https://i.postimg.cc/65CVg5SN/male.webp"

// Gender icon ab image se load hoti hai (female.webp / male.webp) — pehle
// Unicode ♀/♂ symbol draw hota tha, ab asal icon dikhta hai.
@Composable
private fun GenderIcon(gender: String, size: androidx.compose.ui.unit.Dp) {
    val isFemale = gender == "female"
    AsyncImage(
        model = if (isFemale) FEMALE_ICON else MALE_ICON,
        contentDescription = gender,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

// COUNTRY_LIST (CountryList.kt) already "🇵🇰 +92 Pakistan" jaisi 195 entries rakhti
// hai (phone signup dropdown ke liye) — flag modal ke liye usi se icon+name nikal
// kar A-Z sort kar dete hain, taake dobara wahi list ko hardcode na karna pare.
private val FLAG_LIST: List<Pair<String, String>> by lazy {
    val regex = Regex("^(\\S+)\\s+\\+\\d+\\s+(.+)$")
    COUNTRY_LIST.mapNotNull { entry ->
        regex.find(entry)?.let { m -> m.groupValues[1] to m.groupValues[2] }
    }.distinctBy { it.second }.sortedBy { it.second }
}

// Naya dark-green "glow" theme (reference screenshot ke mutabiq) — pehle wala
// halka-safed card theme ab sirf dialogs ke liye reh gaya hai.
private val ProfileGreenBg = Color(0xFF06170f)          // poore screen ka dark base
private val ProfileGreenBorder = Color(0xFF0a2e1c)       // (ab use nahi hota, backward-compat ke liye rakha)
private val ProfileGreenDark = Color(0xFFcafcdc)         // field values (halka mint-white)
private val ProfileGreenAccent = Color(0xFF3ce87a)       // labels, arrows, glow accent
private val FieldBoxBg = Color(0xFF0b2418)
private val FieldBoxBorder = Color(0xFF1f5c3a)
private val PlaceholderTextColor = Color(0xFF5f9c7c)

// Naya light/mint card (reference background-image ke andar wala blank
// panel) — fields ab isi card ke andar, light theme mein render hoti hain.
private val CardBg = Color(0xFFeef8f2)
private val CardBorder = Color(0xFFcfe8d8)
private val CardLabelColor = Color(0xFF0a7a42)
private val CardValueColor = Color(0xFF1e4d30)
private val CardArrowColor = Color(0xFF22c55e)
private val CardPlaceholderColor = Color(0xFF8fb89f)
private val CardFieldBoxBg = Color(0xFFf6fffa)
private val CardFieldBoxBorder = Color(0xFFd7ecdf)

@Composable
fun ProfileEditScreen(navController: NavController) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var profile by remember { mutableStateOf(ProfileStore.get(context)) }
    // Game ID — bekend se milta hai (BackendClient.gameId). Agar kisi wajah se
    // abhi tak load nahi hui (jaise app dobara khuli aur auth response ka
    // intezar hai), to locally save ki hui id (SessionStore) fallback ke
    // taur par dikha dete hain.
    val gameId = remember {
        BackendClient.gameId.takeIf { it > 0 }
            ?: com.voiceludo.app.net.SessionStore.getGameId(context)
    }
    var idCopied by remember { mutableStateOf(false) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showFlagDialog by remember { mutableStateOf(false) }
    var avatarUploading by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }

    // Naam/avatar badalte hi bekend (real account, game-partners ko dikhne wala)
    // ko bhi sync kar dete hain — kabhi bhi local file path avatar ke taur par
    // nahi bhejte, sirf pehle se hosted http(s) URL (upload hone ke baad).
    fun pushProfileToBackend(currentProfile: UserProfile) {
        // BUG FIX: khali avatar bekend ko na bhejein — server ka UpdateProfile
        // hamesha avatar column overwrite karta hai, chahe khali ho. Pehle yahan
        // "" bhej dete thay jab is device par avatarUri http URL na ho (jaise
        // sirf naam edit kiya ho) — is se pehle se saved DP database se mit
        // jati thi (logout/on-off ke baad "DP/naam gayab" wala bug yahi tha).
        val avatarForBackend = currentProfile.avatarUri.takeIf { it.startsWith("http") } ?: BackendClient.myAvatar
        BackendClient.updateProfile(currentProfile.name, avatarForBackend)
    }

    // Device ki photo picker (Android system picker — koi runtime permission nahi
    // chahiye). Photo pehle internal storage mein copy karte hain (turant preview
    // ke liye), phir usay bekend ke POST /avatar par upload karte hain — response
    // mein mila hosted URL hi asal avatarUri ban jata hai (README ke mutabiq
    // base64/local path kabhi bhi bekend/doosre players ko nahi bheja jata).
    val pickPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    avatarError = "Photo padhi nahi ja saki"
                    return@rememberLauncherForActivityResult
                }
                // Turant local preview (upload complete hone tak)
                val outFile = File(context.filesDir, "profile_avatar.jpg")
                outFile.writeBytes(bytes)
                profile = profile.copy(avatarUri = outFile.absolutePath)
                avatarError = null
                avatarUploading = true
                BackendClient.uploadAvatar(bytes, mime) { url, err ->
                    avatarUploading = false
                    if (url != null) {
                        ProfileStore.saveAvatar(context, url)
                        profile = profile.copy(avatarUri = url)
                        pushProfileToBackend(profile)
                    } else {
                        // Upload fail — local preview hi reh jati hai is device par,
                        // lekin doosre players ko nahi dikhegi jab tak dobara try na ho.
                        avatarError = err ?: "Photo upload nahi ho saki, dobara koshish karein"
                    }
                }
            } catch (_: Exception) {
                avatarError = "Photo save nahi ho saki"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Poora background — green gradient + glow/sparkles (user ka diya link)
        // Panel (Screens > Profile edit) ka background: rang / image / GIF / SVG. Na bana ho to yehi default.
        val profCfg = AppConfigStore.screen("profile")
        if (profCfg != null && profCfg.bgColor.isNotBlank()) {
            Box(Modifier.fillMaxSize().background(parseConfigColor(profCfg.bgColor, Color(0xFF06170f))))
        }
        if (profCfg != null && profCfg.bgImage.isNotBlank()) {
            RemoteArt(profCfg.bgImage, Modifier.fillMaxSize(), ContentScale.Crop)
        } else {
            AsyncImage(
                model = SCREEN_BACKGROUND_IMAGE,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Panel ke items jo "Icons ke PEECHE" set hain
        ConfigScreenElements("profile", navController, "back")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ---- Header: "Edit Profile" title aur X dono background image mein
            // pehle se bane huay hain (baked-in) — is liye yahan alag se apna
            // Text/"✕" nahi banate (warna dono doubled/overlapping dikhte thay).
            // Sirf ek invisible tap-area rakhte hain, theek background ke X ke
            // upar, taake woh assli (background wala) X hi tap hone par
            // navController.popBackStack() se peechay chala jaye.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(56.dp)
                        .clickable { SoundManager.playClick(); navController.popBackStack() }
                )
            }

            // ---- Avatar: glow-strip ke beech circle, neeche-right corner par
            // chhota pencil-badge (tap karte hi gallery khul jati hai). ----
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                val customAvatar = AppConfigStore.element("profAvatar").image
                val avatarModel = when {
                    profile.avatarUri.isEmpty() -> customAvatar.ifBlank { DEFAULT_AVATAR }
                    profile.avatarUri.startsWith("http") -> profile.avatarUri
                    else -> File(profile.avatarUri)
                }

                Box(
                    modifier = Modifier
                        .cfgElement("profAvatar")
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0d2818))
                        .clickable {
                            SoundManager.playClick()
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile.avatarUri.isEmpty() && customAvatar.isNotBlank()) {
                        RemoteArt(customAvatar, Modifier.size(100.dp).clip(CircleShape), ContentScale.Crop)
                    } else {
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = "avatar",
                            modifier = Modifier
                                .size(if (profile.avatarUri.isEmpty()) 64.dp else 100.dp)
                                .clip(CircleShape),
                            contentScale = if (profile.avatarUri.isEmpty()) ContentScale.Fit else ContentScale.Crop
                        )
                    }
                }

                // Pencil-badge, avatar ke neeche-right corner par
                Box(
                    modifier = Modifier
                        .offset(x = 36.dp, y = 36.dp)
                        .cfgElement("profBadge")
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0d2818))
                        .border(2.dp, ProfileGreenAccent.copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            SoundManager.playClick()
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val badgeImg = AppConfigStore.element("profBadge").image
                    if (badgeImg.isNotBlank()) {
                        RemoteArt(badgeImg, Modifier.fillMaxSize(), ContentScale.Crop)
                    } else {
                        AsyncImage(
                            model = UPLOAD_PHOTO_ICON,
                            contentDescription = "edit avatar",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (avatarUploading) {
                Text(
                    "Photo upload ho rahi hai...",
                    color = ProfileGreenAccent,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            avatarError?.let { err ->
                Text(
                    err,
                    color = Color(0xFFff6b6b),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // ---- Game ID: profile picture ke neeche, tap karte hi copy ho jati
            // hai (dost isay add karne ke liye maang sakte hain). ----
            if (gameId > 0) {
                Row(
                    modifier = Modifier
                        .cfgElement("profId")
                        .fillMaxWidth()
                        .clickable {
                            SoundManager.playClick()
                            clipboard.setText(AnnotatedString(gameId.toString()))
                            idCopied = true
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ID: $gameId",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (idCopied) "\u2713" else "\uD83D\uDCCB",
                        color = if (idCopied) ProfileGreenAccent else Color.White,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(8.dp))

            // ---- Light/mint card — reference image ke blank-panel jaisa —
            // Username/Gender/Country/Bio sab isi ke andar, light theme mein. ----
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()) {
                // Mint card ka peeche wala panel — alag element (panel mein hilta / badalta hai)
                val cardImg = AppConfigStore.element("profCard").image
                Box(
                    Modifier
                        .matchParentSize()
                        .cfgElement("profCard")
                        .clip(RoundedCornerShape(20.dp))
                        .then(if (cardImg.isBlank()) Modifier.background(CardBg) else Modifier)
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                ) {
                    if (cardImg.isNotBlank()) RemoteArt(cardImg, Modifier.fillMaxSize(), ContentScale.FillBounds)
                }
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                // ---- Username ----
                Column(Modifier.cfgElement("profUsername")) {
                CardFieldLabel("Username (0/3 times per day)")
                CardFieldBox(onClick = { showNameDialog = true }) {
                    Text(
                        profile.name,
                        color = CardValueColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Gender ----
                Column(Modifier.cfgElement("profGender")) {
                CardFieldLabel("Gender")
                CardFieldBox(onClick = { showGenderDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        GenderIcon(profile.gender, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            profile.gender.replaceFirstChar { it.uppercase() },
                            color = CardValueColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Country ----
                Column(Modifier.cfgElement("profCountry")) {
                CardFieldLabel("Country")
                CardFieldBox(onClick = { showFlagDialog = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(profile.flagIcon, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(profile.flagName, color = CardValueColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Bio ----
                Column(Modifier.cfgElement("profBio")) {
                CardFieldLabel("Bio")
                CardFieldBox(onClick = { showBioDialog = true }, minHeight = 120.dp, alignTop = true) {
                    Text(
                        profile.bio.ifEmpty { "Bio is left empty" },
                        color = if (profile.bio.isEmpty()) CardPlaceholderColor else CardValueColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        fontStyle = if (profile.bio.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                        modifier = Modifier.weight(1f)
                    )
                }
                }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
        // Panel ke items jo "Icons ke AAGE" set hain
        ConfigScreenElements("profile", navController, "front")
    }


    // ---- Name modal ----
    if (showNameDialog) {
        TextInputDialog(
            title = "Enter name (max 16 chars)",
            initial = profile.name,
            maxLen = 16,
            onDismiss = { showNameDialog = false },
            onConfirm = { t ->
                ProfileStore.saveName(context, t)
                profile = ProfileStore.get(context)
                pushProfileToBackend(profile)
                showNameDialog = false
            }
        )
    }

    // ---- Bio modal ----
    if (showBioDialog) {
        TextInputDialog(
            title = "Bio (max 70 chars)",
            initial = profile.bio,
            maxLen = 70,
            singleLine = false,
            onDismiss = { showBioDialog = false },
            onConfirm = { t ->
                ProfileStore.saveBio(context, t)
                profile = ProfileStore.get(context)
                showBioDialog = false
            }
        )
    }

    // ---- Gender modal ----
    if (showGenderDialog) {
        AlertDialog(
            onDismissRequest = { showGenderDialog = false },
            title = { Text("Select gender", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    listOf("male" to "Male", "female" to "Female").forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundManager.playClick()
                                    ProfileStore.saveGender(context, value)
                                    profile = ProfileStore.get(context)
                                    showGenderDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = profile.gender == value, onClick = {
                                SoundManager.playClick()
                                ProfileStore.saveGender(context, value)
                                profile = ProfileStore.get(context)
                                showGenderDialog = false
                            })
                            Spacer(Modifier.width(6.dp))
                            GenderIcon(value, 20.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { SoundManager.playClick(); showGenderDialog = false }) { Text("Close") }
            }
        )
    }

    // ---- Flag modal (A-Z searchable country list) ----
    if (showFlagDialog) {
        FlagPickerDialog(
            onDismiss = { showFlagDialog = false },
            onSelect = { icon, name ->
                ProfileStore.saveFlag(context, name, icon)
                profile = ProfileStore.get(context)
                showFlagDialog = false
            }
        )
    }

}

// Label jo har field box ke upar dikhta hai (jaisa "Username (0/3 times per
// day)", "Gender", "Country", "Bio" reference image mein).
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = ProfileGreenAccent,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// Outlined rounded box (dark fill, green border), andar content + right arrow —
// reference screenshot ke har row jaisa. Bio ke liye taller/top-aligned bhi ho sakta hai.
@Composable
private fun FieldBox(
    onClick: () -> Unit,
    minHeight: androidx.compose.ui.unit.Dp = 52.dp,
    alignTop: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(FieldBoxBg)
            .border(1.dp, FieldBoxBorder, RoundedCornerShape(10.dp))
            .clickable { SoundManager.playClick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
    ) {
        content()
        Text("\u276F", color = ProfileGreenAccent, fontSize = 14.sp)
    }
}

// Light/mint card ke andar wali label + box variants (dark text on light bg) —
// naye background-image design ke card ke liye.
@Composable
private fun CardFieldLabel(text: String) {
    Text(
        text,
        color = CardLabelColor,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun CardFieldBox(
    onClick: () -> Unit,
    minHeight: androidx.compose.ui.unit.Dp = 50.dp,
    alignTop: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(CardFieldBoxBg)
            .border(1.dp, CardFieldBoxBorder, RoundedCornerShape(10.dp))
            .clickable { SoundManager.playClick(); onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically
    ) {
        content()
        Text("\u276F", color = CardArrowColor, fontSize = 13.sp)
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    maxLen: Int,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black, fontSize = 14.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLen) text = it },
                    singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("${text.length}/$maxLen", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = { SoundManager.playClick(); onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { SoundManager.playClick(); onDismiss() }) { Text("Cancel") }
        }
    )
}

@Composable
private fun FlagPickerDialog(onDismiss: () -> Unit, onSelect: (icon: String, name: String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) FLAG_LIST
        else FLAG_LIST.filter { it.second.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Country A-Z", fontWeight = FontWeight.Black, fontSize = 14.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search country...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered) { (icon, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { SoundManager.playClick(); onSelect(icon, name) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontSize = 13.sp, color = CardValueColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
