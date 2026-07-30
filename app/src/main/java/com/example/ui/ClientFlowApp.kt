package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ClinicalSessionLog
import com.example.data.Patient
import com.example.data.PersonalJournalEntry
import com.example.data.GeminiHelper
import com.example.ui.security.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import com.example.ui.theme.*
import coil.compose.AsyncImage
import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFlowApp(viewModel: ClientFlowViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()
    val panicMode by viewModel.panicModeActivated.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var showDevReset by remember { mutableStateOf(false) }
    var activePushNotification by remember { mutableStateOf<String?>(null) }

    MyApplicationTheme(
        selectedTheme = settings?.selectedTheme ?: "Natural Tones",
        selectedAccent = settings?.selectedAccent ?: "Sage",
        darkTheme = settings?.isDarkMode ?: isSystemInDarkTheme()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Evaluates App Lock Gate first
            if (isLocked) {
                SecurityGateScreen(
                    onUnlock = { code -> viewModel.unlockApp(code) },
                    onSetPin = { code -> viewModel.togglePinSecurity(true, code) },
                    settingsPin = settings?.pinCode ?: ""
                )
            } else if (panicMode) {
                // Instantly covers clinical info with compliance reading
                PanicShieldScreen(
                    onDismiss = { viewModel.deactivatePanicMode() }
                )
            } else if (settings == null || settings?.selectedMode == null || !settings!!.onboardingCompleted) {
                // If not selection set, show Onboarding and Login / Sign-up Secure Profile Setup
                ModeOnboardingContainer(
                    syncedUserEmail = settings?.syncedUserEmail,
                    onRegisterLogin = { email, isSignUp, pass -> viewModel.registerLoginUser(email, isSignUp, pass) },
                    currentSelectedMode = settings?.selectedMode,
                    onModeSelected = { viewModel.updateSelectedMode(it) },
                    onFinishOnboarding = { viewModel.completeOnboarding() }
                )
            } else {
                // Primary App Dashboard Workspace
                val activeMode = settings?.selectedMode ?: "Personal"
                var activeTab by remember { mutableStateOf("Home") } // Home, Primary vs Secondary

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeMode == "Personal") Icons.Rounded.Eco else Icons.Rounded.Work,
                                        contentDescription = "App Mode Logo",
                                        tint = if (activeMode == "Personal") SageGreen else TealPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "ClientFlow",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            },
                            actions = {
                                // Panic Button for practitioners
                                if (activeMode == "Practitioner") {
                                    Button(
                                        onClick = { viewModel.activatePanicMode() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = OverwhelmedAura,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.padding(end = 4.dp).testTag("panic_trigger_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Security,
                                            contentDescription = "Shield Panic",
                                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                        )
                                        Text("PANIC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Global Theme Light/Dark Mode Quick Toggle Button
                                IconButton(
                                    onClick = {
                                        viewModel.toggleDarkMode(!(settings?.isDarkMode ?: false))
                                    },
                                    modifier = Modifier.testTag("global_theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (settings?.isDarkMode == true) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Toggle Light/Dark Mode",
                                        tint = if (settings?.isDarkMode == true) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Dev/Test Quick actions
                                IconButton(
                                    onClick = { showDevReset = true },
                                    modifier = Modifier.testTag("dev_settings_header")
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = "App Settings")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        LovelyAnimatedBottomBar(
                            activeMode = activeMode,
                            activeTab = activeTab,
                            onTabSelected = { activeTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (activeMode == "Personal") {
                            when (activeTab) {
                                "Home" -> BeautifulDashboardHome(viewModel = viewModel, activeMode = "Personal", onNavigate = { activeTab = it })
                                "Primary" -> PersonalJournalFeedView(viewModel = viewModel)
                                else -> PersonalInsightsView(viewModel = viewModel)
                            }
                        } else {
                            when (activeTab) {
                                "Home" -> BeautifulDashboardHome(viewModel = viewModel, activeMode = "Practitioner", onNavigate = { activeTab = it })
                                "Primary" -> PractitionerCaseloadView(viewModel = viewModel)
                                else -> PractitionerPresencesView(viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            // Simulated Heads-up Push Notification HUD Banner
            AnimatedVisibility(
                visible = activePushNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(100f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Active Notifications",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(
                                "ClientFlow System Push Channel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activePushNotification ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { activePushNotification = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Alert Notification",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Developer Quick Setting Dialog
            if (showDevReset) {
                Dialog(onDismissRequest = { showDevReset = false }) {
                    val currentSettings = settings
                    var localPasscode by remember { mutableStateOf(currentSettings?.pinCode ?: "") }
                    var connectionEmailInput by remember { mutableStateOf(currentSettings?.syncedUserEmail ?: "clinical.user@gmail.com") }
                    var selectedThemeOpt by remember { mutableStateOf(currentSettings?.selectedTheme ?: "Natural Tones") }
                    var selectedAccentOpt by remember { mutableStateOf(currentSettings?.selectedAccent ?: "Sage") }
                    var syncEnabled by remember { mutableStateOf(currentSettings?.cloudSyncEnabled ?: false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 620.dp)
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Gear setting icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Workspace Settings",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { showDevReset = false },
                                    modifier = Modifier.size(24.dp).testTag("close_settings_dialog")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Window Settings")
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 1. Theme Atmosphere Customizer
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Palette, contentDescription = "Palette", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("Fluid Theme Atmosphere (4 Options)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                
                                val flowThemes = listOf("Natural Tones", "Cosmic Galaxy", "Midnight Eclipse", "Lavender Mist")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    flowThemes.take(2).forEach { themeName ->
                                        val isSel = selectedThemeOpt == themeName
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedThemeOpt = themeName
                                                    viewModel.updateTheme(themeName)
                                                }
                                                .padding(vertical = 10.dp)
                                                .testTag("theme_opt_${themeName.replace(" ", "_").lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = themeName,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    flowThemes.drop(2).forEach { themeName ->
                                        val isSel = selectedThemeOpt == themeName
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedThemeOpt = themeName
                                                    viewModel.updateTheme(themeName)
                                                }
                                                .padding(vertical = 10.dp)
                                                .testTag("theme_opt_${themeName.replace(" ", "_").lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = themeName,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                // Dark Mode Switch Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (settings?.isDarkMode == true) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                            contentDescription = "Dark Mode Toggle",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                "Dark Mode Reading Comfort",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "Low-light contrast for clinical & journaling views",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = settings?.isDarkMode ?: false,
                                        onCheckedChange = { viewModel.toggleDarkMode(it) },
                                        modifier = Modifier.testTag("dark_mode_switch")
                                    )
                                }
                            }

                            // 2. 10 Accent Color Customizer
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Aesthetic Accent Hue Selection (10 Choices)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                                val themeAccents = listOf(
                                    Pair("Sage", Color(0xFF708B75)),
                                    Pair("Mint", Color(0xFF66BB6A)),
                                    Pair("Ocean", Color(0xFF00897B)),
                                    Pair("Indigo", Color(0xFF5C6BC0)),
                                    Pair("Lavender", Color(0xFFAB47BC)),
                                    Pair("Rose", Color(0xFFEC407A)),
                                    Pair("Amber", Color(0xFFFFA726)),
                                    Pair("Terracotta", Color(0xFFD84315)),
                                    Pair("Slate", Color(0xFF78909C)),
                                    Pair("Gold", Color(0xFFFBC02D))
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    themeAccents.forEach { (name, colVal) ->
                                        val isSel = selectedAccentOpt == name
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(colVal)
                                                .border(
                                                    width = if (isSel) 3.dp else 1.dp,
                                                    color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedAccentOpt = name
                                                    viewModel.updateAccentColor(name)
                                                }
                                                .testTag("accent_opt_${name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSel) {
                                                Icon(Icons.Default.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 3. User passcode screen lock configuration
                            val isPinChecked = currentSettings?.pinLockEnabled ?: false
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.VpnKey, contentDescription = "Security Keys", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("Workstation Passcode Gate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Switch(
                                        checked = isPinChecked,
                                        onCheckedChange = { chk ->
                                            if (chk) {
                                                viewModel.togglePinSecurity(true, localPasscode.ifEmpty { "1234" })
                                            } else {
                                                viewModel.togglePinSecurity(false, "")
                                            }
                                        },
                                        modifier = Modifier.testTag("settings_pin_switch")
                                    )
                                }
                                
                                if (isPinChecked) {
                                    OutlinedTextField(
                                        value = localPasscode,
                                        onValueChange = { newVal ->
                                            if (newVal.length <= 4 && newVal.all { it.isDigit() }) {
                                                localPasscode = newVal
                                                viewModel.togglePinSecurity(true, newVal)
                                            }
                                        },
                                        label = { Text("Set Custom 4-Digit Passcode") },
                                        placeholder = { Text("e.g. 5678") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("custom_passcode_input")
                                    )
                                    Text(
                                        text = "Secure passcode protection is active. Use security parameter '${localPasscode}' to unlock the workshop in next session.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 3b. Biometric Recognition & Fingerprint/Face Security
                            BiometricSettingsCard(viewModel = viewModel)

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 4. Firestore user data storage integrations, firebase auth & FCM pushes
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CloudQueue, contentDescription = "Cloud databases", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("Systems & Cloud Servers (Firestore / Auth / Push)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Dynamic Cloud Server state badges
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(if (syncEnabled) Color(0xFF66BB6A) else Color.Gray, CircleShape)
                                            )
                                            Text(
                                                text = if (syncEnabled) "Firebase Auth: CONNECTED" else "Firebase Auth: DISCONNECTED",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (syncEnabled) Color(0xFF2E7D32) else Color.Gray
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(if (syncEnabled) Color(0xFF66BB6A) else Color.Gray, CircleShape)
                                            )
                                            Text(
                                                text = if (syncEnabled) "Firestore DB State: SYNC ACTIVE" else "Firestore DB State: OFFLINE LOCALDATABASE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (syncEnabled) Color(0xFF2E7D32) else Color.Gray
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(if (syncEnabled) Color(0xFF66BB6A) else Color.Gray, CircleShape)
                                            )
                                            Text(
                                                text = if (syncEnabled) "Push FCM Notification Node: LISTENING" else "Push FCM Notification Node: INACTIVE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (syncEnabled) Color(0xFF2E7D32) else Color.Gray
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        if (!syncEnabled) {
                                            // Auth Setup Credentials input
                                            OutlinedTextField(
                                                value = connectionEmailInput,
                                                onValueChange = { connectionEmailInput = it },
                                                label = { Text("Firestore Admin Sign-in Email") },
                                                placeholder = { Text("email@example.com") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("firestore_email_input")
                                            )
                                            
                                            Button(
                                                onClick = {
                                                    if (connectionEmailInput.isNotEmpty() && connectionEmailInput.contains("@")) {
                                                        syncEnabled = true
                                                        viewModel.updateCloudSync(true, connectionEmailInput)
                                                        activePushNotification = "Workstation securely authenticated via Firebase Auth. Handshake sync initiated with Cloud Firestore database."
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("connect_firestore_btn"),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = "sync upload info", modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Initialize Firestore Server Connection", fontSize = 11.sp)
                                            }
                                        } else {
                                            // Connected state showing credentials
                                            Text(
                                                text = "Connected Firestore User Email:\n${connectionEmailInput}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Trigger simulated system FCM alert push
                                                Button(
                                                    onClick = {
                                                        activePushNotification = if (currentSettings?.selectedMode == "Personal") {
                                                            "Simulated Firestore Alert: New reflection writing suggestion has arrived! Tap to outline."
                                                        } else {
                                                            "Simulated Clinical Alert: Case PAT-CODE-52 progress indicators was refreshed on Cloud Firestore Server."
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("trigger_push_alert_btn"),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Icon(Icons.Default.NotificationsActive, contentDescription = "push send option", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Trigger Simulated Push", fontSize = 10.sp)
                                                }

                                                // Log out of cloud server database
                                                IconButton(
                                                    onClick = {
                                                        syncEnabled = false
                                                        viewModel.updateCloudSync(false, "")
                                                        activePushNotification = "System Alert: Firebase connection was logged out successfully. Clinical sandbox returned to local offline SQLite database."
                                                    },
                                                    modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape).testTag("disconnect_firestore_btn")
                                                ) {
                                                    Icon(Icons.Default.PowerOff, contentDescription = "close sync connection", tint = MaterialTheme.colorScheme.onErrorContainer)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 5. Swap current stream mode on the fly
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Change Clinical flow environment", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                val curMode = currentSettings?.selectedMode ?: "Personal"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.updateSelectedMode("Personal")
                                            showDevReset = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (curMode == "Personal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (curMode == "Personal") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f).testTag("settings_swap_personal")
                                    ) {
                                        Icon(Icons.Default.Eco, contentDescription = "Journal", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Personal", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.updateSelectedMode("Practitioner")
                                            showDevReset = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (curMode == "Practitioner") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (curMode == "Practitioner") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f).testTag("settings_swap_practitioner")
                                    ) {
                                        Icon(Icons.Default.Work, contentDescription = "Work", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Work", fontSize = 11.sp)
                                    }
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            // 6. Full State Reset
                            Button(
                                onClick = {
                                    viewModel.resetApp()
                                    showDevReset = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app_reset_trigger")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Clear Session")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wipe Workstation State")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// PRESTIGE & EXQUISITE ANIMATED FLOATING NAVIGATION DOCK
// ==========================================================================================
@Composable
fun LovelyAnimatedBottomBar(
    activeMode: String,
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    val items = if (activeMode == "Personal") {
        listOf(
            Triple("Home", "Home", Icons.Rounded.Home),
            Triple("Primary", "Daily Feed", Icons.Rounded.Create),
            Triple("Secondary", "Insights", Icons.Rounded.Timeline)
        )
    } else {
        listOf(
            Triple("Home", "Home", Icons.Rounded.Home),
            Triple("Primary", "Caseload", Icons.Rounded.People),
            Triple("Secondary", "Presences", Icons.Rounded.CalendarMonth)
        )
    }

    val selectedIndex = when (activeTab) {
        "Home" -> 0
        "Primary" -> 1
        "Secondary" -> 2
        else -> 0
    }

    // Floating modern navigation dock
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / 3

            // Animated sliding capsule behind the selected icon
            val animatedOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "offset"
            )

            // Dynamic color for the sliding capsule (utilizes the dynamically styled primary theme color!)
            val capsuleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .height(44.dp)
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .background(capsuleColor, RoundedCornerShape(16.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                items.forEachIndexed { index, (tabId, label, icon) ->
                    val isSelected = selectedIndex == index

                    // Bounce animation for icon scale
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "scale"
                    )

                    // Vertical offset bounce on selection
                    val verticalOffset by animateDpAsState(
                        targetValue = if (isSelected) (-2).dp else 0.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "verticalOffset"
                    )

                    Column(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // Custom visual feedback prioritised
                            ) {
                                onTabSelected(tabId)
                            }
                            .testTag(
                                if (activeMode == "Personal") {
                                    when (tabId) {
                                        "Home" -> "nav_personal_home"
                                        "Primary" -> "nav_personal_journal"
                                        else -> "nav_personal_insights"
                                    }
                                } else {
                                    when (tabId) {
                                        "Home" -> "nav_practitioner_home"
                                        "Primary" -> "nav_practitioner_caseload"
                                        else -> "nav_practitioner_events"
                                    }
                                }
                            )
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            },
                            modifier = Modifier
                                .offset(y = verticalOffset)
                                .scale(iconScale)
                                .size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// ONBOARDING & MODE SELECTION WORKSPACE
// ==========================================================================================

@Composable
fun ClientFlowBrandLogoHeader(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer Squircle Badge with Canvas artwork matching exact user logo design
        Box(
            modifier = Modifier
                .size(170.dp)
                .shadow(12.dp, RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF165056), Color(0xFF0C2B2F))
                    )
                )
                .border(1.5.dp, Color(0xFF26676E), RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                
                // Sparkles / Stars
                val star1 = Path().apply {
                    val sx = w * 0.52f
                    val sy = h * 0.28f
                    moveTo(sx, sy - 12f)
                    quadraticTo(sx, sy, sx + 12f, sy)
                    quadraticTo(sx, sy, sx, sy + 12f)
                    quadraticTo(sx, sy, sx - 12f, sy)
                    quadraticTo(sx, sy, sx, sy - 12f)
                }
                drawPath(star1, Color(0xFFFFDE85))

                drawCircle(Color(0xFFFFE89E), radius = 3f, center = Offset(w * 0.59f, h * 0.2f))
                drawCircle(Color(0xFFFFE89E), radius = 2f, center = Offset(w * 0.48f, h * 0.22f))
                drawCircle(Color(0xFFFFE89E), radius = 2.5f, center = Offset(w * 0.62f, h * 0.32f))

                // Crescent Moon & Face Profile (Light Sage Green)
                val moonPath = Path().apply {
                    moveTo(w * 0.55f, h * 0.14f)
                    cubicTo(w * 0.32f, h * 0.14f, w * 0.16f, h * 0.3f, w * 0.16f, h * 0.53f)
                    cubicTo(w * 0.16f, h * 0.74f, w * 0.32f, h * 0.86f, w * 0.55f, h * 0.86f)
                    cubicTo(w * 0.45f, h * 0.82f, w * 0.38f, h * 0.74f, w * 0.38f, h * 0.63f)
                    cubicTo(w * 0.38f, h * 0.58f, w * 0.4f, h * 0.54f, w * 0.43f, h * 0.5f)
                    cubicTo(w * 0.41f, h * 0.47f, w * 0.37f, h * 0.44f, w * 0.37f, h * 0.4f)
                    cubicTo(w * 0.37f, h * 0.36f, w * 0.4f, h * 0.33f, w * 0.42f, h * 0.3f)
                    cubicTo(w * 0.39f, h * 0.27f, w * 0.38f, h * 0.23f, w * 0.38f, h * 0.2f)
                    cubicTo(w * 0.38f, h * 0.16f, w * 0.45f, h * 0.14f, w * 0.55f, h * 0.14f)
                    close()
                }
                drawPath(moonPath, Color(0xFF98D2AC))

                // Closed Eye on Profile
                val eyePath = Path().apply {
                    moveTo(w * 0.33f, h * 0.38f)
                    quadraticTo(w * 0.36f, h * 0.41f, w * 0.39f, h * 0.38f)
                }
                drawPath(eyePath, Color(0xFF0D2E33), style = Stroke(width = 3f, cap = StrokeCap.Round))

                // Book Open Pages (Soft Cream)
                val bgPages = Path().apply {
                    moveTo(w * 0.46f, h * 0.46f)
                    cubicTo(w * 0.58f, h * 0.38f, w * 0.7f, h * 0.36f, w * 0.82f, h * 0.38f)
                    lineTo(w * 0.8f, h * 0.43f)
                    cubicTo(w * 0.7f, h * 0.41f, w * 0.58f, h * 0.44f, w * 0.46f, h * 0.51f)
                    close()
                }
                drawPath(bgPages, Color(0xFFEFE3CA))

                val mainPages = Path().apply {
                    moveTo(w * 0.44f, h * 0.5f)
                    cubicTo(w * 0.56f, h * 0.42f, w * 0.68f, h * 0.4f, w * 0.8f, h * 0.42f)
                    lineTo(w * 0.78f, h * 0.8f)
                    cubicTo(w * 0.68f, h * 0.78f, w * 0.56f, h * 0.8f, w * 0.44f, h * 0.86f)
                    close()
                }
                drawPath(mainPages, Color(0xFFFAF2E3))

                // Book Cover & Spine (Dark Teal Journal)
                val bookCover = Path().apply {
                    moveTo(w * 0.42f, h * 0.52f)
                    cubicTo(w * 0.54f, h * 0.44f, w * 0.66f, h * 0.42f, w * 0.78f, h * 0.44f)
                    cubicTo(w * 0.81f, h * 0.44f, w * 0.83f, h * 0.46f, w * 0.83f, h * 0.49f)
                    lineTo(w * 0.81f, h * 0.83f)
                    cubicTo(w * 0.81f, h * 0.85f, w * 0.78f, h * 0.87f, w * 0.75f, h * 0.87f)
                    cubicTo(w * 0.63f, h * 0.83f, w * 0.52f, h * 0.85f, w * 0.42f, h * 0.89f)
                    cubicTo(w * 0.4f, h * 0.89f, w * 0.39f, h * 0.88f, w * 0.39f, h * 0.86f)
                    lineTo(w * 0.39f, h * 0.54f)
                    cubicTo(w * 0.39f, h * 0.53f, w * 0.4f, h * 0.52f, w * 0.42f, h * 0.52f)
                    close()
                }
                drawPath(bookCover, Color(0xFF133E43))

                // Strap Band on Journal
                drawRoundRect(
                    color = Color(0xFF0B2529),
                    topLeft = Offset(w * 0.71f, h * 0.44f),
                    size = Size(w * 0.04f, h * 0.41f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )

                // Leaf / Sprout Emblem on Cover
                val leaf1 = Path().apply {
                    moveTo(w * 0.48f, h * 0.8f)
                    cubicTo(w * 0.52f, h * 0.75f, w * 0.58f, h * 0.68f, w * 0.63f, h * 0.59f)
                    cubicTo(w * 0.58f, h * 0.63f, w * 0.52f, h * 0.65f, w * 0.48f, h * 0.63f)
                    cubicTo(w * 0.48f, h * 0.68f, w * 0.5f, h * 0.74f, w * 0.48f, h * 0.8f)
                    close()
                }
                drawPath(leaf1, Color(0xFF98D2AC))

                val leaf2 = Path().apply {
                    moveTo(w * 0.53f, h * 0.75f)
                    cubicTo(w * 0.56f, h * 0.72f, w * 0.62f, h * 0.67f, w * 0.65f, h * 0.6f)
                    cubicTo(w * 0.6f, h * 0.63f, w * 0.55f, h * 0.64f, w * 0.53f, h * 0.75f)
                    close()
                }
                drawPath(leaf2, Color(0xFFBEDAC5))

                // Glowing Speech Bubble (Warm Gold)
                val bubblePath = Path().apply {
                    moveTo(w * 0.8f, h * 0.18f)
                    cubicTo(w * 0.88f, h * 0.18f, w * 0.93f, h * 0.23f, w * 0.93f, h * 0.29f)
                    cubicTo(w * 0.93f, h * 0.35f, w * 0.88f, h * 0.4f, w * 0.8f, h * 0.4f)
                    cubicTo(w * 0.77f, h * 0.4f, w * 0.74f, h * 0.39f, w * 0.72f, h * 0.38f)
                    lineTo(w * 0.66f, h * 0.41f)
                    lineTo(w * 0.68f, h * 0.36f)
                    cubicTo(w * 0.66f, h * 0.34f, w * 0.65f, h * 0.31f, w * 0.65f, h * 0.29f)
                    cubicTo(w * 0.65f, h * 0.23f, w * 0.72f, h * 0.18f, w * 0.8f, h * 0.18f)
                    close()
                }
                drawPath(bubblePath, Color(0xFFF3D083))

                // Three Dots inside Speech Bubble
                drawCircle(Color(0xFF0D2E33), radius = 2.5f, center = Offset(w * 0.74f, h * 0.29f))
                drawCircle(Color(0xFF0D2E33), radius = 2.5f, center = Offset(w * 0.79f, h * 0.29f))
                drawCircle(Color(0xFF0D2E33), radius = 2.5f, center = Offset(w * 0.84f, h * 0.29f))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Brand Title "clientflow"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "client",
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFFFAF2E3),
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "flow",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFF98D2AC),
                letterSpacing = (-0.5).sp
            )
        }

        if (showTagline) {
            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Tagline "JOURNAL. REFLECT. GROW."
            Text(
                text = "JOURNAL. REFLECT. GROW.",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFF98D2AC).copy(alpha = 0.9f),
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Decorative Line with glowing center dot
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val cy = size.height / 2f
                    
                    drawLine(
                        color = Color(0xFF26676E),
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = Color(0xFFF3D083),
                        radius = 4f,
                        center = Offset(w / 2f, cy)
                    )
                }
            }
        }
    }
}

@Composable
fun ModeOnboardingContainer(
    syncedUserEmail: String?,
    onRegisterLogin: (String, Boolean, String) -> Unit,
    currentSelectedMode: String?,
    onModeSelected: (String) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    var stepIndex by remember { mutableStateOf(0) } // 0 = Selection, 1..3 = Onboarding Slides
    
    // Auth screens routing: "Welcome", "Login", "SignUp"
    var authState by remember { mutableStateOf("Welcome") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf("") }
    
    // Check if user is logged in
    val isLoggedIn = !syncedUserEmail.isNullOrBlank()

    if (!isLoggedIn) {
        // Auth screens container matching the coloroso deep dark teal theme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D2E33))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            when (authState) {
                "Welcome" -> {
                    // Header Logo & Branding Illustration matching exact image design
                    ClientFlowBrandLogoHeader(showTagline = true)

                    Spacer(modifier = Modifier.height(28.dp))

                    // Welcome details card in coloroso deep dark teal surface style
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(28.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF133E43)),
                        border = BorderStroke(1.dp, Color(0xFF226168)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Welcome",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFAF2E3)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Log in securely or create your profile",
                                fontSize = 13.sp,
                                color = Color(0xFF98B5B0),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            // Primary Log In button (Sage green accent)
                            Button(
                                onClick = { authState = "Login"; authError = "" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_login_button"),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF98D2AC), contentColor = Color(0xFF0D2E33))
                            ) {
                                Text("Log in", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Outlined Sign Up button
                            OutlinedButton(
                                onClick = { authState = "SignUp"; authError = "" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_signup_button"),
                                border = BorderStroke(1.5.dp, Color(0xFF98D2AC)),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFAF2E3))
                            ) {
                                Text("Sign Up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                "SignUp" -> {
                    // Header Logo
                    ClientFlowBrandLogoHeader(showTagline = false)

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(28.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF133E43)),
                        border = BorderStroke(1.dp, Color(0xFF226168)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { authState = "Welcome" }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFAF2E3))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Sign up",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFAF2E3)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Create an account, It's free",
                                fontSize = 13.sp,
                                color = Color(0xFF98B5B0)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (authError.isNotEmpty()) {
                                Text(authError, color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0E3237),
                                unfocusedContainerColor = Color(0xFF0E3237),
                                focusedTextColor = Color(0xFFFAF2E3),
                                unfocusedTextColor = Color(0xFFFAF2E3),
                                focusedBorderColor = Color(0xFF98D2AC),
                                unfocusedBorderColor = Color(0xFF226168),
                                focusedLabelColor = Color(0xFF98D2AC),
                                unfocusedLabelColor = Color(0xFF98B5B0)
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email") },
                                placeholder = { Text("example@clientflow.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("Confirm Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (emailInput.isBlank() || !emailInput.contains("@")) {
                                        authError = "Please enter a valid email address."
                                    } else if (passwordInput.length < 4) {
                                        authError = "Password must be at least 4 characters."
                                    } else if (passwordInput != confirmPasswordInput) {
                                        authError = "Passwords do not match."
                                    } else {
                                        onRegisterLogin(emailInput, true, passwordInput)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("submit_signup_button"),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF98D2AC), contentColor = Color(0xFF0D2E33))
                            ) {
                                Text("Sign Up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = { authState = "Login" }) {
                                Text("Already have an account? Log In", color = Color(0xFFF3D083), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                "Login" -> {
                    // Header Logo
                    ClientFlowBrandLogoHeader(showTagline = false)

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(28.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF133E43)),
                        border = BorderStroke(1.dp, Color(0xFF226168)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { authState = "Welcome" }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFFAF2E3))
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Log in",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFAF2E3)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Sign in to access your secure profile",
                                fontSize = 13.sp,
                                color = Color(0xFF98B5B0),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (authError.isNotEmpty()) {
                                Text(authError, color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0E3237),
                                unfocusedContainerColor = Color(0xFF0E3237),
                                focusedTextColor = Color(0xFFFAF2E3),
                                unfocusedTextColor = Color(0xFFFAF2E3),
                                focusedBorderColor = Color(0xFF98D2AC),
                                unfocusedBorderColor = Color(0xFF226168),
                                focusedLabelColor = Color(0xFF98D2AC),
                                unfocusedLabelColor = Color(0xFF98B5B0)
                            )

                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (emailInput.isBlank() || !emailInput.contains("@")) {
                                        authError = "Please enter a valid email address."
                                    } else if (passwordInput.isEmpty()) {
                                        authError = "Password cannot be empty."
                                    } else {
                                        onRegisterLogin(emailInput, false, passwordInput)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("submit_login_button"),
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF98D2AC), contentColor = Color(0xFF0D2E33))
                            ) {
                                Text("Log In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = { authState = "SignUp" }) {
                                Text("Don't have an account? Sign Up", color = Color(0xFFF3D083), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    } else {
        // Already logged in! Proceed with standard onboarding chosen flow stages
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFDFCF9)) // Natural Tones LightCanvas
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentSelectedMode == null) {
                // First step: App selection (Natural Tones Landing Screen Styling & HTML Extraction)
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Top Bar / Status Area Placeholder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF435345), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Spa,
                                contentDescription = "Logomark",
                                tint = Color(0xFFFDFCF9),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "ClientFlow",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 18.sp,
                            color = Color(0xFF435345),
                            letterSpacing = (-0.3).sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show active user profile badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF435345).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = syncedUserEmail.takeWhile { it != '@' },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF435345)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE1E3D3), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings Icon",
                                tint = Color(0xFF435345),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 2. Headline Welcome Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Welcome.",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF1B1C17),
                        lineHeight = 40.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "Choose your flow.",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B1C17),
                        lineHeight = 40.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a workspace to begin. You can switch modes or create a separate profile later in settings.",
                        color = Color(0xFF747968),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Mode Cards (Exactly Styled with Natural Tones Design Specs)
                // Option A: Personal Journal Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_mode_personal")
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF0F2E7))
                        .border(1.dp, Color(0xFFE1E3D3), RoundedCornerShape(28.dp))
                        .clickable { onModeSelected("Personal"); stepIndex = 1 }
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF708B75), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Eco,
                                contentDescription = "Journal Option",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Personal Journal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF2D332D)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Reflect on your day, track mood patterns, and find insights with AI summaries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D6354),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go to Personal Reflection",
                            tint = Color(0xFF708B75),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option B: Practitioner Workspace Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_mode_practitioner")
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE1E3D3), RoundedCornerShape(28.dp))
                        .clickable { onModeSelected("Practitioner"); stepIndex = 1 }
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF5E7A8A), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Work,
                                contentDescription = "Workspace Option",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Practitioner Workspace",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF273238)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Manage your caseload, clinical session notes, and patient outcome analytics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF545F63),
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go to Practitioner Suite",
                            tint = Color(0xFF5E7A8A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 4. Bottom Security / Encryption Info Pill
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE9EBE0), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFFDFCF9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Security Info Symbol",
                                tint = Color(0xFF435345),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "ClientFlow uses end-to-end encryption to keep your data private and HIPAA-aligned.",
                            color = Color(0xFF435345),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Decorative Home Pill Indicator
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(4.dp)
                            .background(Color(0xFFDDE0D0), CircleShape)
                    )
                }
            } else {
                // Interactive Onboarding explaining core highlights
                val personalOnboarding = listOf(
                    OnboardingSlideData(
                        title = "Daily Mood Baseline Logging",
                        description = "Check in with your baseline emotions daily. Charting your emotional state establishes stable patterns and highlights positive trends or triggers over time, giving you clear cognitive visibility.",
                        icon = Icons.Default.Analytics
                    ),
                    OnboardingSlideData(
                        title = "Reflective Free-Write & Audio Diary",
                        description = "Express yourself freely on our secure, local markdown-lite canvas. Spilling your internal thoughts or transcribing spoken recordings provides an immediate emotional release and clears cognitive clutter safely.",
                        icon = Icons.Default.Edit
                    ),
                    OnboardingSlideData(
                        title = "Interactive Mood Calendar",
                        description = "See your progress over time visually. Looking back at historical mood states directly aids self-reflection, showing how far you've traversed in your journey towards emotional resilience.",
                        icon = Icons.Rounded.CalendarMonth
                    ),
                    OnboardingSlideData(
                        title = "Supportive AI Prompt Reflections",
                        description = "Receive context-aware writing questions. Phrased in a warm, encouraging tone, our supportive AI analyzes your recent logs to suggest gentle prompt directions tailored to you.",
                        icon = Icons.Default.AutoAwesome
                    )
                )

                val practitionerOnboarding = listOf(
                    OnboardingSlideData(
                        title = "Caseload Analytics Dashboard",
                        description = "Gain complete visibility on aggregate outcomes. Track active case indexes, structured notes, and homework completion bars.",
                        icon = Icons.Default.Dashboard
                    ),
                    OnboardingSlideData(
                        title = "Pre-Session Prep Briefing",
                        description = "Walk in prepared. Leverage AI assistance to extract patterns, distress progression, and identify historical sleep anomalies.",
                        icon = Icons.Default.AutoStories
                    ),
                    OnboardingSlideData(
                        title = "Trajectory Compare delta",
                        description = "Pair two sessions of a patient side-by-side. Calculate exact metric differences and review theme trajectories objectively.",
                        icon = Icons.Default.Compare
                    )
                )

                val activeSlides = if (currentSelectedMode == "Personal") personalOnboarding else practitionerOnboarding
                val activeSlide = activeSlides.getOrNull(stepIndex - 1) ?: activeSlides[0]

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (currentSelectedMode == "Personal") "Setting Up Your Journal" else "Configuring Clinical Suite",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentSelectedMode == "Personal") SageGreen else TealPrimary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Progress Indicators (Dynamic for variable onboarding screen counts)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    for (i in 1..activeSlides.size) {
                        Box(
                            modifier = Modifier
                                .width(if (i == stepIndex) 24.dp else 8.dp)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == stepIndex) {
                                        if (currentSelectedMode == "Personal") SageGreen else TealPrimary
                                    } else Color.LightGray
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = (if (currentSelectedMode == "Personal") SageGreen else TealPrimary).copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeSlide.icon,
                        contentDescription = "Onboarding icon",
                        tint = if (currentSelectedMode == "Personal") SageGreen else TealPrimary,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = activeSlide.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = activeSlide.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(60.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    TextButton(
                        onClick = {
                            if (stepIndex == 1) {
                                onModeSelected("") // Go back to choosing mode
                            } else {
                                stepIndex--
                            }
                        },
                        modifier = Modifier.testTag("onboarding_back_button")
                    ) {
                        Text("Back")
                    }

                    // Next or Finish button
                    Button(
                        onClick = {
                            if (stepIndex == activeSlides.size) {
                                onFinishOnboarding()
                            } else {
                                stepIndex++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentSelectedMode == "Personal") SageGreen else TealPrimary
                        ),
                        modifier = Modifier.testTag("onboarding_next_button")
                    ) {
                        Text(
                            if (stepIndex == activeSlides.size) {
                                if (currentSelectedMode == "Personal") "Start Journaling" else "Get Started"
                            } else {
                                "Next"
                            }
                        )
                    }
                }
            }
        }
    }
}

data class OnboardingSlideData(
    val title: String,
    val description: String,
    val icon: ImageVector
)

// ==========================================================================================
// SECURITY / LOCK INTERFACE SCREEN
// ==========================================================================================

@Composable
fun SecurityGateScreen(
    onUnlock: (String) -> Boolean,
    onSetPin: (String) -> Unit,
    settingsPin: String
) {
    var codeProgress by remember { mutableStateOf("") }
    var displaysErrorMessage by remember { mutableStateOf(false) }
    var errorMessageText by remember { mutableStateOf("") }

    // PIN Setup Flow States
    val isSetupMode = settingsPin.isEmpty()
    var setupStep by remember { mutableStateOf(1) } // 1 = enter initial PIN, 2 = confirm PIN
    var firstPinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero Visual Indicator
        Box(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .background(
                    if (isSetupMode) TealPrimary.copy(alpha = 0.1f) else SageGreen.copy(alpha = 0.1f),
                    CircleShape
                )
                .padding(20.dp)
        ) {
            Icon(
                imageVector = if (isSetupMode) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = "Workstation Security Gate",
                tint = if (isSetupMode) TealPrimary else SageGreen,
                modifier = Modifier.size(56.dp)
            )
        }

        Text(
            text = if (isSetupMode) {
                if (setupStep == 1) "Initialize Workstation PIN" else "Confirm Security PIN"
            } else {
                "Workstation Lock Active"
            },
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .testTag("pin_setup_step_text")
        )

        Text(
            text = if (isSetupMode) {
                if (setupStep == 1) {
                    "Set up a 4-digit passcode lock to secure clinical patient files and confidential personal records."
                } else {
                    "Please re-enter your chosen passcode parameters to verify and complete workstation setup."
                }
            } else {
                "Workstation protection is active. Enter passcode parameters to unlock the clinical dashboard."
            },
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        )

        // PIN Dot Indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            for (i in 1..4) {
                val filled = codeProgress.length >= i
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) {
                                if (isSetupMode) TealPrimary else SageGreen
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (filled) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                            shape = CircleShape
                        )
                        .testTag("pin_input_dot_$i")
                )
            }
        }

        // Live Diagnostic or Status Error message
        if (displaysErrorMessage) {
            Text(
                text = errorMessageText.ifBlank { "Passcode check failed. Please check parameters." },
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("security_error_message")
            )
        } else if (isSetupMode) {
            Text(
                text = if (setupStep == 1) "Step 1: Create 4-Digit Passcode" else "Step 2: Confirm 4-Digit Passcode",
                color = TealPrimary.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Numeric entry platform
        val keyrows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            for (row in keyrows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    for (num in row) {
                        val isSpKey = num == "C" || num == "⌫"
                        Button(
                            onClick = {
                                when (num) {
                                    "C" -> {
                                        codeProgress = ""
                                        displaysErrorMessage = false
                                    }
                                    "⌫" -> {
                                        if (codeProgress.isNotEmpty()) {
                                            codeProgress = codeProgress.dropLast(1)
                                            displaysErrorMessage = false
                                        }
                                    }
                                    else -> {
                                        if (codeProgress.length < 4) {
                                            codeProgress += num
                                            displaysErrorMessage = false

                                            // Trigger validations on 4 digits reached
                                            if (codeProgress.length == 4) {
                                                if (isSetupMode) {
                                                    if (setupStep == 1) {
                                                        firstPinInput = codeProgress
                                                        codeProgress = ""
                                                        setupStep = 2
                                                    } else {
                                                        if (codeProgress == firstPinInput) {
                                                            // Success match! Save & unlock app!
                                                            onSetPin(codeProgress)
                                                            onUnlock(codeProgress)
                                                        } else {
                                                            // Mismatch error
                                                            errorMessageText = "Passcode confirmation did not match! Please choose a new 4-digit PIN."
                                                            displaysErrorMessage = true
                                                            codeProgress = ""
                                                            firstPinInput = ""
                                                            setupStep = 1
                                                        }
                                                    }
                                                } else {
                                                    val ok = onUnlock(codeProgress)
                                                    if (!ok) {
                                                        errorMessageText = "Passcode validation failed. Access restricted."
                                                        displaysErrorMessage = true
                                                        codeProgress = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpKey) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(72.dp)
                                .testTag("pin_num_$num")
                        ) {
                            Text(
                                text = num,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isSpKey) 16.sp else 20.sp
                            )
                        }
                    }
                }
            }
        }

        // Standard Workspace Bypass / Demonstration Unlock Helper
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                if (isSetupMode) {
                    onSetPin("1234")
                    onUnlock("1234")
                } else {
                    onUnlock(settingsPin.ifBlank { "1234" })
                }
            },
            modifier = Modifier.testTag("pin_bypass_demo_button")
        ) {
            Text(
                text = if (isSetupMode) "Initialize with Default PIN (1234)" else "Bypass & Unlock (Default: ${settingsPin.ifBlank { "1234" }})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = if (isSetupMode) TealPrimary else SageGreen
            )
        }
    }
}

@Composable
fun PanicShieldScreen(
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = "Manual Icon", tint = Color(0xFF90A4AE), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reference Document", color = Color(0xFFECEFF1), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                modifier = Modifier.testTag("panic_dismiss_button")
            ) {
                Text("Restore", fontSize = 11.sp, color = Color.White)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A232E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Standard Practice Guidelines for Mental Health Documentation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Establishing comprehensive, client-centric record-keeping remains a fundamental pillar in clinical accountability. In keeping with modern security, all notes should satisfy the dual-motive standards:\n\n" +
                    "1. Confidentiality Integrity: Session archives must be preserved from intrusive environments. Practitioners should verify encryption protocols frequently.\n\n" +
                    "2. Objective Chronology: Entries recorded chronologically outline diagnostic homework and qualitative trajectories with precise baseline indices.\n\n" +
                    "3. Patient Autonomy: Active diagnostic tracking phases (Assessment, Intervention, Maintenance) encourage self-management structures to empower long term recovery and minimize risk anomalies.\n\n" +
                    "Observe standard audit controls on all exports.",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==========================================================================================
// A. PERSONAL JOURNAL DASHBOARD
// ==========================================================================================

data class EasyPalette(
    val name: String,
    val background: Color,
    val cardBg: Color,
    val text: Color,
    val accent: Color
)

@Composable
fun PersonalJournalFeedView(viewModel: ClientFlowViewModel) {
    val entries by viewModel.personalEntriesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isJournalUnlocked by viewModel.isJournalUnlocked.collectAsStateWithLifecycle()

    if (settings?.journalBiometricLocked == true && !isJournalUnlocked) {
        BiometricLockOverlay(
            title = "Personal Journal Locked",
            description = "Biometric protection is active. Authenticate with fingerprint, face recognition, or PIN to access your personal journal entries.",
            onUnlockBiometric = { activity, onSuccess, onError ->
                viewModel.unlockJournalWithBiometrics(activity, onSuccess, onError)
            },
            onUnlockPin = { pin ->
                viewModel.unlockJournalWithPin(pin)
            }
        )
        return
    }

    val scope = rememberCoroutineScope()

    // Screen States
    var selectedMoodFilter by remember { mutableStateOf<String?>(null) }
    var selectedHashtagFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Design and color customization states
    var entryCardDesignState by remember { mutableStateOf("OverlappingStack") } // "OverlappingStack" vs "StandardList"
    var selectedPaletteName by remember { mutableStateOf("Warm Ivory") }
    var activeStackIndex by remember { mutableStateOf(0) }

    // Reader customizer states
    var readerFontSize by remember { mutableStateOf(16f) } // default readable size
    var readerFontFamilySelection by remember { mutableStateOf("Serif") } // "Serif", "Sans-Serif", "Monospace", "Cursive"

    val presetCardPalettes = remember {
        listOf(
            EasyPalette("Warm Ivory", Color(0xFFFDFBF7), Color(0xFFFFF9E6), Color(0xFF3E2723), Color(0xFF8D6E63)),
            EasyPalette("Calm Mist", Color(0xFFF1F8E9), Color(0xFFE8F5E9), Color(0xFF1B5E20), Color(0xFF4CAF50)),
            EasyPalette("Ocean Air", Color(0xFFE0F7FA), Color(0xFFE0F7FA), Color(0xFF006064), Color(0xFF00ACC1)),
            EasyPalette("Soft Lavender", Color(0xFFFAF5FF), Color(0xFFEDE7F6), Color(0xFF4A148C), Color(0xFF7E57C2)),
            EasyPalette("Velvet Dark", Color(0xFF121212), Color(0xFF263238), Color(0xFFECEFF1), Color(0xFF455A64)),
            EasyPalette("Pastel Cherry", Color(0xFFFFF5F5), Color(0xFFFCE4EC), Color(0xFF880E4F), Color(0xFFEC407A))
        )
    }

    // Dialogue Add States
    var currentMoodSelected by remember { mutableStateOf("Neutral") }
    var contextOpenedModal by remember { mutableStateOf(false) }
    var contextNoteInput by remember { mutableStateOf("") }

    // Forms fields
    var freeWriteInput by remember { mutableStateOf("") }
    var tagStringInput by remember { mutableStateOf("") }
    var sleepRating by remember { mutableStateOf(5f) }

    // Media attachment states & launchers
    var attachedJournalPhotoUri by remember { mutableStateOf<String?>(null) }
    var attachedJournalAudioPath by remember { mutableStateOf<String?>(null) }

    val journalPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedJournalPhotoUri = uri.toString()
        }
    }

    val journalAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedJournalAudioPath = uri.toString()
        }
    }

    // Audio record simulation & real Speech-to-Text orchestrator
    val localContext = LocalContext.current
    var isRecordingAudio by remember { mutableStateOf(false) }
    var soundWavePhase by remember { mutableStateOf(0f) }
    var recordTimeSeconds by remember { mutableStateOf(0) }
    var simulatedOutputText by remember { mutableStateOf("") }
    var isTranscribingText by remember { mutableStateOf(false) }
    var recordedSubjectLine by remember { mutableStateOf("") }

    // Speech-to-text live service properties
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var realTimeTranscribedText by remember { mutableStateOf("") }
    var isRealSpeechRecognizerWorking by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(localContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Interactive switch for direct mic transcription vs AI assistant
    var transcriptionMode by remember { mutableStateOf("Mic") } // "Mic" or "AI Voice"
    var autoSaveAndSummarize by remember { mutableStateOf(true) }
    var lastAiGeneratedSummary by remember { mutableStateOf("") }
    var isGeneratingAutoSummary by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasMicPermission = isGranted
            if (isGranted) {
                Toast.makeText(localContext, "Microphone access granted. Ready to record and transcribe diary entries live!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(localContext, "Microphone permission denied. Falling back to AI Voice Assist simulation.", Toast.LENGTH_LONG).show()
                transcriptionMode = "AI Voice"
            }
        }
    )

    fun startRealtimeSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(localContext)) {
            Toast.makeText(localContext, "Native speech recognition is not available. Falling back to AI Voice Assist simulation.", Toast.LENGTH_SHORT).show()
            isRealSpeechRecognizerWorking = false
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(localContext)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    realTimeTranscribedText = ""
                    isRealSpeechRecognizerWorking = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    val amp = Math.abs(rmsdB).coerceIn(0f, 12f)
                    soundWavePhase = (soundWavePhase + amp / 2.0f) % 6f
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording failure"
                        SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission requirements unmet"
                        SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No voice matched"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service is busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server disconnected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Speech recognizer code: $error"
                    }
                    if (isRecordingAudio) {
                        isRealSpeechRecognizerWorking = false
                        Toast.makeText(localContext, "Microphone notice: $message. Please speak again or utilize AI Voice Assist.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val resultText = matches[0]
                        realTimeTranscribedText = resultText
                        if (freeWriteInput.isEmpty()) {
                            freeWriteInput = resultText
                        } else {
                            freeWriteInput += "\n$resultText"
                        }
                        Toast.makeText(localContext, "Transcribed: $resultText", Toast.LENGTH_SHORT).show()

                        if (autoSaveAndSummarize && resultText.isNotBlank()) {
                            isGeneratingAutoSummary = true
                            viewModel.addSpokenJournalEntryAndSummarize(
                                mood = currentMoodSelected,
                                oneSentenceNote = if (contextNoteInput.isBlank()) "Spoken voice reflection" else contextNoteInput,
                                transcribedText = resultText,
                                sleepQuality = sleepRating.toInt(),
                                tags = if (tagStringInput.isBlank()) "#spoken, #voice_journal" else tagStringInput,
                                generateAiSummaryImmediately = true
                            ) { entry, summary ->
                                isGeneratingAutoSummary = false
                                lastAiGeneratedSummary = summary
                                Toast.makeText(localContext, "Spoken entry saved & AI summary generated!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    isRealSpeechRecognizerWorking = false
                    isRecordingAudio = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        realTimeTranscribedText = matches[0]
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer = recognizer
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(localContext, "Unable to boot hardware speech assistant. Using simulation fallback.", Toast.LENGTH_SHORT).show()
            isRealSpeechRecognizerWorking = false
        }
    }

    fun stopRealtimeSpeechToText() {
        speechRecognizer?.apply {
            stopListening()
            destroy()
        }
        speechRecognizer = null
        isRealSpeechRecognizerWorking = false
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.apply {
                stopListening()
                destroy()
            }
        }
    }

    // Selected entry inspection state
    var selectedViewEntry by remember { mutableStateOf<PersonalJournalEntry?>(null) }

    // Prompt suggestion state
    var suggestedPrompt by remember { mutableStateOf("Gathering supportive thoughts to inspire your writing...") }
    var isFetchingPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(entries) {
        isFetchingPrompt = true
        suggestedPrompt = GeminiHelper.generateDailyPrompt(entries)
        isFetchingPrompt = false
    }

    // Run custom ticker for wave simulation and record elapsed time
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordTimeSeconds = 0
            while (isRecordingAudio) {
                delay(300)
                if (!isRealSpeechRecognizerWorking) {
                    soundWavePhase = (soundWavePhase + 1) % 6
                }
                recordTimeSeconds++
            }
        }
    }

    var activeJournalTab by remember { mutableStateOf("Feed") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector Bar (Write Entry vs Feed vs Media Gallery)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeJournalTab = "Write" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeJournalTab == "Write") SageGreen else Color.Transparent,
                    contentColor = if (activeJournalTab == "Write") Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = null
            ) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Write Entry", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { activeJournalTab = "Feed" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeJournalTab == "Feed") SageGreen else Color.Transparent,
                    contentColor = if (activeJournalTab == "Feed") Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = null
            ) {
                Icon(Icons.Rounded.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Daily Feed", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { activeJournalTab = "Media Gallery" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeJournalTab == "Media Gallery") SageGreen else Color.Transparent,
                    contentColor = if (activeJournalTab == "Media Gallery") Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = null
            ) {
                Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gallery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (activeJournalTab == "Write") {
            FirestoreJournalComposerScreen(
                viewModel = viewModel,
                onEntrySaved = { activeJournalTab = "Feed" }
            )
        } else if (activeJournalTab == "Media Gallery") {
            JournalMediaGalleryView(
                viewModel = viewModel,
                onOpenEntry = { selectedViewEntry = it }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // Hero Onboarding Welcome Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SageGreen.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            "My Serene Space",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = SageGreen
                        )
                        Text(
                            "Express daily thoughts securely. Your notes remain locally first, fully confidential.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        Icons.Default.FilterVintage,
                        contentDescription = "Flower visual asset",
                        tint = SageGreen,
                        modifier = Modifier.size(45.dp).padding(start = 8.dp)
                    )
                }
            }
        }

        // 1. TODAY'S MOOD GRANULAR QUICK SELECTOR
        item {
            Text(
                "How is your baseline today?",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Tap a state to add a quick 1-sentence note parameter",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val moods = listOf(
                MoodSelectOption("🌞 Happy", "Happy", HappyAura),
                MoodSelectOption("✨ Productive", "Productive", ProductiveAura),
                MoodSelectOption("🍃 Calm", "Calm", CalmAura),
                MoodSelectOption("📖 Reflective", "Reflective", ReflectiveAura),
                MoodSelectOption("😐 Neutral", "Neutral", NeutralAura),
                MoodSelectOption("⚡ Anxious", "Anxious", AnxiousAura),
                MoodSelectOption("😟 Overwhelmed", "Overwhelmed", OverwhelmedAura)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (moodOpt in moods) {
                    Button(
                        onClick = {
                            currentMoodSelected = moodOpt.id
                            contextOpenedModal = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentMoodSelected == moodOpt.id) moodOpt.color else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentMoodSelected == moodOpt.id) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("today_mood_tap_${moodOpt.id.lowercase()}")
                    ) {
                        Text(moodOpt.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Form Section Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Journal Entry Form",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SageGreen
                        )
                        var isMoodDropdownExpanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(SageGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { isMoodDropdownExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("form_mood_pill")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Mood: $currentMoodSelected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageGreen
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select mood",
                                    tint = SageGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isMoodDropdownExpanded,
                                onDismissRequest = { isMoodDropdownExpanded = false }
                            ) {
                                val moodsList = listOf(
                                    "Happy" to "🌞 Happy",
                                    "Productive" to "✨ Productive",
                                    "Calm" to "🍃 Calm",
                                    "Reflective" to "📖 Reflective",
                                    "Neutral" to "😐 Neutral",
                                    "Anxious" to "⚡ Anxious",
                                    "Overwhelmed" to "😟 Overwhelmed"
                                )
                                moodsList.forEach { (moodId, moodLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(moodLabel, fontSize = 12.sp) },
                                        onClick = {
                                            currentMoodSelected = moodId
                                            isMoodDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // AI Daily Prompt Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_daily_prompt_card"),
                        border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.2f)),
                        colors = CardDefaults.cardColors(containerColor = SageGreen.copy(alpha = 0.04f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI icon",
                                    tint = SageGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AI Suggested Writing Prompt",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = SageGreen
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isFetchingPrompt) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = SageGreen,
                                        strokeWidth = 1.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                isFetchingPrompt = true
                                                suggestedPrompt = GeminiHelper.generateDailyPrompt(entries)
                                                isFetchingPrompt = false
                                            }
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh Prompt",
                                            tint = SageGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = suggestedPrompt,
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    if (!freeWriteInput.contains(suggestedPrompt)) {
                                        freeWriteInput = if (freeWriteInput.isEmpty()) {
                                            "Prompt: $suggestedPrompt\n\n"
                                        } else {
                                            "$freeWriteInput\n\nPrompt: $suggestedPrompt\n\n"
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("apply_prompt_to_write")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Apply prompt",
                                    modifier = Modifier.size(12.dp),
                                    tint = SageGreen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Respond to this prompt",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SageGreen
                                )
                            }
                        }
                    }

                    if (contextNoteInput.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Gray.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Notes, contentDescription = "Context Note", modifier = Modifier.size(14.dp), tint = SageGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Context: \"$contextNoteInput\"",
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1.0f),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            IconButton(onClick = { contextNoteInput = "" }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // 2. AUDIO RECORDER & REAL SPEECH-TO-TEXT SERVICE CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_diary_card_container"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header & Service Mode selector Segmented Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = "Speech to text system",
                                        tint = SageGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Spoken Diary Engine",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Interactive toggle mode
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (transcriptionMode == "Mic") SageGreen.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { transcriptionMode = "Mic" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("opt_mic_transcription")
                                    ) {
                                        Text(
                                            "🎙️ Mic Key", 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = if (transcriptionMode == "Mic") SageGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (transcriptionMode == "AI Voice") SageGreen.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { transcriptionMode = "AI Voice" }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("opt_ai_assistant")
                                    ) {
                                        Text(
                                            "🤖 AI Assist", 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = if (transcriptionMode == "AI Voice") SageGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Auto AI Summary Toggle Switch Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SageGreen.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI Summary toggle",
                                        tint = SageGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Auto-Save & Generate AI Summary",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = autoSaveAndSummarize,
                                    onCheckedChange = { autoSaveAndSummarize = it },
                                    modifier = Modifier.scale(0.7f).testTag("auto_summary_toggle"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = SageGreen
                                    )
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Functional body layout
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isRecordingAudio) {
                                            // Stop audio
                                            if (transcriptionMode == "Mic") {
                                                stopRealtimeSpeechToText()
                                            }
                                            isRecordingAudio = false
                                        } else {
                                            // Start audio
                                            if (transcriptionMode == "Mic") {
                                                if (hasMicPermission) {
                                                    isRecordingAudio = true
                                                    simulatedOutputText = ""
                                                    startRealtimeSpeechToText()
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            } else {
                                                isRecordingAudio = true
                                                simulatedOutputText = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isRecordingAudio) OverwhelmedAura else SageGreen.copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                        .testTag("record_audio_trigger")
                                ) {
                                    Icon(
                                        imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Trigger voice recording diary entry",
                                        tint = if (isRecordingAudio) Color.White else SageGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1.0f)) {
                                    Text(
                                        text = if (isRecordingAudio) {
                                            if (isRealSpeechRecognizerWorking) "🎙️ Audio Recording Active (Speak now...)" else "🎙️ Local Audio Recording Active"
                                        } else "Diary Dictation Capture",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (isRecordingAudio) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Bouncing sound waves simulation based on mic RMS levels
                                            for (i in 0..5) {
                                                val height = (3 + (i + soundWavePhase).toInt() % 4 * 4).dp
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(height)
                                                        .background(SageGreen)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isRealSpeechRecognizerWorking) "Vocal track feedback active" else "Recording fallback channel",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    } else if (isTranscribingText) {
                                        Text("Transcribing voice audio using local algorithms...", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = SageGreen)
                                    } else {
                                        Text(
                                            text = if (transcriptionMode == "Mic") {
                                                "Tap MIC to record. Speech recognizer translates voice to writing."
                                            } else {
                                                "Tap MIC, input subject, and stop to generate custom mindfulness reflection."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            // Dynamic Live Speech feedback box
                            if (isRecordingAudio && transcriptionMode == "Mic") {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (realTimeTranscribedText.isEmpty()) "Listening for spoken voice entry..." else "\" $realTimeTranscribedText \"",
                                        fontSize = 11.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = if (realTimeTranscribedText.isEmpty()) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // If in AI Assist voice simulation or in general audio session details
                            if (isRecordingAudio && transcriptionMode == "AI Voice") {
                                TextField(
                                    value = recordedSubjectLine,
                                    onValueChange = { recordedSubjectLine = it },
                                    placeholder = { Text("What are you recording about? (e.g., job interview prep, walk near ocean)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audio_subject_field"),
                                    colors = OutlinedTextFieldDefaults.colors(),
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )

                                Button(
                                    onClick = {
                                        isRecordingAudio = false
                                        isTranscribingText = true
                                        scope.launch {
                                            delay(1000) // simulated transcribe delay
                                            val transcription = GeminiHelper.simulateVoiceTranscription(
                                                topic = if (recordedSubjectLine.isEmpty()) "Today's daily moments" else recordedSubjectLine,
                                                mood = currentMoodSelected
                                            )
                                            simulatedOutputText = transcription
                                            freeWriteInput += (if (freeWriteInput.isEmpty()) "" else "\n") + transcription
                                            isTranscribingText = false
                                            recordedSubjectLine = ""

                                            if (autoSaveAndSummarize && transcription.isNotBlank()) {
                                                isGeneratingAutoSummary = true
                                                viewModel.addSpokenJournalEntryAndSummarize(
                                                    mood = currentMoodSelected,
                                                    oneSentenceNote = if (contextNoteInput.isBlank()) "Spoken voice reflection" else contextNoteInput,
                                                    transcribedText = transcription,
                                                    sleepQuality = sleepRating.toInt(),
                                                    tags = if (tagStringInput.isBlank()) "#spoken, #voice_journal" else tagStringInput,
                                                    generateAiSummaryImmediately = true
                                                ) { entry, summary ->
                                                    isGeneratingAutoSummary = false
                                                    lastAiGeneratedSummary = summary
                                                    Toast.makeText(localContext, "Spoken entry saved & AI summary generated!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("transcribe_recording_action")
                                ) {
                                    Text("Stop, Transcribe & Auto-Summarize Spoken Record", fontSize = 11.sp)
                                }
                            }

                            // AI Summary Indicator or Loading State
                            if (isGeneratingAutoSummary) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SageGreen.copy(alpha = 0.12f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = SageGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Generating instant AI summary for spoken journal entry...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SageGreen
                                    )
                                }
                            } else if (lastAiGeneratedSummary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_spoken_summary_card"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, SageGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "AI Summary Badge",
                                                    tint = SageGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "AI-Generated Reflection Summary",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = SageGreen
                                                )
                                            }
                                            Text(
                                                "Saved to Journal",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.Gray
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = lastAiGeneratedSummary,
                                            fontSize = 11.sp,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Free Write TextField (Markdown-lite concept)
                    OutlinedTextField(
                        value = freeWriteInput,
                        onValueChange = { freeWriteInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .testTag("free_write_text_field"),
                        label = { Text("What is on your mind? (Markdown supported)") },
                        placeholder = { Text("Add some notes, e.g. *bold items*, - lists of items") },
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    // Sleep quality correlation rating
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bedtime, contentDescription = "Sleep score icon", modifier = Modifier.size(16.dp), tint = SageGreen)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sleep Quality Correlation Metric", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Text("${sleepRating.toInt()}/10", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SageGreen)
                        }
                        Slider(
                            value = sleepRating,
                            onValueChange = { sleepRating = it },
                            valueRange = 1f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = SageGreen, activeTrackColor = SageGreen)
                        )
                    }

                    // Hashtag fields
                    OutlinedTextField(
                        value = tagStringInput,
                        onValueChange = { tagStringInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tags_input_field"),
                        label = { Text("Hashtags & categorization") },
                        placeholder = { Text("#gratitude, #work, #relationships, #custom") },
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    // Media Attachments Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("journal_media_attachments"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach media files",
                                        tint = SageGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Journal Media Attachments",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                // Reset all attachments
                                if (attachedJournalPhotoUri != null || attachedJournalAudioPath != null) {
                                    Text(
                                        "Clear All",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            attachedJournalPhotoUri = null
                                            attachedJournalAudioPath = null
                                        }
                                    )
                                }
                            }

                            // Quick buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { journalPhotoLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SageGreen.copy(alpha = 0.12f),
                                        contentColor = SageGreen
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("journal_add_photo_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick photo", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Photo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { journalAudioLauncher.launch("audio/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SageGreen.copy(alpha = 0.12f),
                                        contentColor = SageGreen
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("journal_add_audio_button")
                                ) {
                                    Icon(Icons.Default.AudioFile, contentDescription = "Pick audio", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Audio File", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Presets for quick simulation in Emulator environment
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Select Quick Visual / Sound Presets:", fontSize = 9.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "ForestWalk.jpg" to "preset_forest_walk",
                                        "CalmOcean.jpg" to "preset_calm_ocean",
                                        "ZenBowl.mp3" to "preset_singing_bowl",
                                        "MindBell.wav" to "preset_mindfulness_bell"
                                    ).forEach { (displayLabel, presetKey) ->
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    if (presetKey.endsWith("_walk") || presetKey.endsWith("_ocean")) {
                                                        attachedJournalPhotoUri = presetKey
                                                    } else {
                                                        attachedJournalAudioPath = presetKey
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(displayLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // Display selected media indicators
                            if (attachedJournalPhotoUri != null || attachedJournalAudioPath != null) {
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(2.dp))

                                if (attachedJournalPhotoUri != null) {
                                    val isPreset = attachedJournalPhotoUri!!.startsWith("preset_")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPreset) {
                                            val gradient = when (attachedJournalPhotoUri) {
                                                "preset_forest_walk" -> Brush.horizontalGradient(listOf(SageGreen, Color(0xFF2E7D32)))
                                                "preset_calm_ocean" -> Brush.horizontalGradient(listOf(Color(0xFF0288D1), Color(0xFF00ACC1)))
                                                else -> Brush.horizontalGradient(listOf(SageGreen, TealPrimary))
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(gradient),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = "Preset icon", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        } else {
                                            Card(
                                                modifier = Modifier.size(40.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                AsyncImage(
                                                    model = attachedJournalPhotoUri,
                                                    contentDescription = "Attached photo user picked",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Text(
                                                text = if (isPreset) "Preset Image: ${attachedJournalPhotoUri!!.removePrefix("preset_").replace("_", " ")}" else "Attached Image URI",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isPreset) "No physical storage required" else "Content resolver pathway",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        IconButton(
                                            onClick = { attachedJournalPhotoUri = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                if (attachedJournalAudioPath != null) {
                                    val isPresetAudio = attachedJournalAudioPath!!.startsWith("preset_")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SageGreen.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Audio media icon", tint = SageGreen, modifier = Modifier.size(16.dp))
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1.0f)) {
                                            Text(
                                                text = if (isPresetAudio) "Audio Preset: ${attachedJournalAudioPath!!.removePrefix("preset_").replace("_", " ")}" else "Attached Audio Record",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Ready to play on journal overview",
                                                fontSize = 9.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        IconButton(
                                            onClick = { attachedJournalAudioPath = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove audio", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Save entries button
                    Button(
                        onClick = {
                            viewModel.addJournalEntry(
                                mood = currentMoodSelected,
                                oneSentenceNote = contextNoteInput,
                                freeWriteText = freeWriteInput,
                                sleepQuality = sleepRating.toInt(),
                                tags = tagStringInput,
                                photoUri = attachedJournalPhotoUri,
                                audioFilePath = attachedJournalAudioPath
                            )
                            // Clear states
                            freeWriteInput = ""
                            contextNoteInput = ""
                            tagStringInput = ""
                            sleepRating = 5f
                            attachedJournalPhotoUri = null
                            attachedJournalAudioPath = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_journal_entry")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Secure Today's Reflections")
                    }
                }
            }
        }

        // Filters platform
        item {
            Divider()
            Text("Chronological Reflections History", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Review and audit previous logs registered in this workplace local tables.", color = Color.Gray, fontSize = 11.sp)
            
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_search_bar"),
                placeholder = { Text("Search keywords in title/body...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = SageGreen,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SageGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedLabelColor = SageGreen,
                    unfocusedLabelColor = Color.Gray
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AESTHETIC STYLE CONTROLS SECTION
            Text("Aesthetic E-Reader Dashboard Styling", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = SageGreen)
            
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layout Switch buttons
                Row(
                    modifier = Modifier
                        .background(Color.LightGray.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isStack = entryCardDesignState == "OverlappingStack"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isStack) SageGreen else Color.Transparent)
                            .clickable { entryCardDesignState = "OverlappingStack" }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isStack) Color.White else Color.Gray
                            )
                            Text(
                                "Overlapping Deck",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isStack) Color.White else Color.Gray
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isStack) SageGreen else Color.Transparent)
                            .clickable { entryCardDesignState = "StandardList" }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Rounded.List,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (!isStack) Color.White else Color.Gray
                            )
                            Text(
                                "Compact Feed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isStack) Color.White else Color.Gray
                            )
                        }
                    }
                }

                // Small quick indicators
                Text(
                    text = "Theme: $selectedPaletteName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card Color Palettes Row (Colorful circle choices)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Card Coloroso Palette:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetCardPalettes.forEach { p ->
                        val isSelected = selectedPaletteName == p.name
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(p.cardBg)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) SageGreen else Color.LightGray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { selectedPaletteName = p.name }
                        )
                    }
                }
            }
        }

        // Render previous entries list
        val displayedEntries = entries.filter { item ->
            val matchMood = selectedMoodFilter == null || item.mood == selectedMoodFilter
            val matchTag = selectedHashtagFilter == null || item.tags.contains(selectedHashtagFilter!!)
            val matchSearch = searchQuery.isEmpty() ||
                    item.oneSentenceNote.contains(searchQuery, ignoreCase = true) ||
                    item.freeWriteText.contains(searchQuery, ignoreCase = true) ||
                    (item.transcribedText?.contains(searchQuery, ignoreCase = true) ?: false)
            matchMood && matchTag && matchSearch
        }

        val clampedStackIndex = if (displayedEntries.isEmpty()) 0 else activeStackIndex.coerceIn(0, displayedEntries.lastIndex)
        val activePalette = presetCardPalettes.find { it.name == selectedPaletteName } ?: presetCardPalettes.first()

        if (displayedEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No reflective journals found matching filters.", color = Color.Gray)
                }
            }
        } else if (entryCardDesignState == "OverlappingStack") {
            // RENDER VISUALLY STUNNING INTERACTIVE STACK DECK
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(370.dp)
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val stackSize = displayedEntries.size
                    // Back to front render
                    for (i in 2 downTo 0) {
                        val cardIndex = clampedStackIndex + i
                        if (cardIndex < stackSize) {
                            val entry = displayedEntries[cardIndex]
                            val cardScale = when (i) {
                                0 -> 1.0f
                                1 -> 0.94f
                                else -> 0.88f
                            }
                            val translationY = when (i) {
                                0 -> 0.dp
                                1 -> 16.dp
                                else -> 32.dp
                            }
                            val rotationZ = when (i) {
                                0 -> 0f
                                1 -> 3.5f
                                else -> -3.5f
                            }
                            val alpha = when (i) {
                                0 -> 1.0f
                                1 -> 0.82f
                                else -> 0.55f
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .offset(y = translationY)
                                    .scale(cardScale)
                                    .rotate(rotationZ)
                                    .alpha(alpha)
                                    .border(
                                        width = if (i == 0) 1.5.dp else 1.dp,
                                        color = if (i == 0) activePalette.accent else Color.Transparent,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .shadow(elevation = if (i == 0) 8.dp else 2.dp, shape = RoundedCornerShape(20.dp))
                                    .clickable(enabled = i == 0) {
                                        selectedViewEntry = entry
                                    },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = activePalette.cardBg)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp)
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val moodColor = when (entry.mood) {
                                                "Happy" -> HappyAura
                                                "Productive" -> ProductiveAura
                                                "Calm" -> CalmAura
                                                "Reflective" -> ReflectiveAura
                                                "Neutral" -> NeutralAura
                                                "Anxious" -> AnxiousAura
                                                "Overwhelmed" -> OverwhelmedAura
                                                else -> NeutralAura
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(moodColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = entry.mood,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = activePalette.text
                                            )
                                        }
                                        Text(
                                            text = "Rest: ${entry.sleepQuality}/10",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = activePalette.accent
                                        )
                                    }

                                    val formattedDate = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(entry.dateMillis)
                                    Text(
                                        text = formattedDate,
                                        fontSize = 11.sp,
                                        color = activePalette.text.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (entry.oneSentenceNote.isNotEmpty()) {
                                        Text(
                                            text = "Source context: \"${entry.oneSentenceNote}\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            color = activePalette.accent,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }

                                    // Render e-reader serif text in custom card stack
                                    Text(
                                        text = if (entry.freeWriteText.isEmpty()) "[No free-write notes added]" else entry.freeWriteText,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = activePalette.text,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Attachment pills
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (entry.photoUri != null) {
                                            Row(
                                                modifier = Modifier
                                                    .background(activePalette.accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, tint = activePalette.accent, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("📷 Asset", fontSize = 9.sp, color = activePalette.text, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (entry.audioFilePath != null) {
                                            Row(
                                                modifier = Modifier
                                                    .background(SageGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = SageGreen, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("🎵 Voice Track", fontSize = 9.sp, color = SageGreen, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        IconButton(
                                            onClick = { viewModel.deleteJournalEntry(entry) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete entry",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Stack navigation controllers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (clampedStackIndex > 0) activeStackIndex = clampedStackIndex - 1
                        },
                        enabled = clampedStackIndex > 0,
                        modifier = Modifier.background(SageGreen.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous stack card", tint = SageGreen)
                    }

                    Text(
                        text = "Card ${clampedStackIndex + 1} of ${displayedEntries.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = {
                            if (clampedStackIndex < displayedEntries.lastIndex) activeStackIndex = clampedStackIndex + 1
                        },
                        enabled = clampedStackIndex < displayedEntries.lastIndex,
                        modifier = Modifier.background(SageGreen.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next stack card", tint = SageGreen)
                    }
                }
            }
        } else {
            // CLASSIC LIST FEED COLORIZED BEAUTIFULLY WITH COLOROSO CARD PALETTE
            items(displayedEntries) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedViewEntry = entry }
                        .testTag("entry_item_${entry.id}"),
                    colors = CardDefaults.cardColors(containerColor = activePalette.cardBg),
                    border = BorderStroke(1.dp, activePalette.accent.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val moodColor = when (entry.mood) {
                                    "Happy" -> HappyAura
                                    "Productive" -> ProductiveAura
                                    "Calm" -> CalmAura
                                    "Reflective" -> ReflectiveAura
                                    "Neutral" -> NeutralAura
                                    "Anxious" -> AnxiousAura
                                    "Overwhelmed" -> OverwhelmedAura
                                    else -> NeutralAura
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(moodColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.mood,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = activePalette.text
                                )
                            }

                            // Sleep quality widget
                            Text(
                                "Rest: ${entry.sleepQuality}/10",
                                fontSize = 11.sp,
                                color = activePalette.accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Timestamp
                        val formattedDate = SimpleDateFormat("EEEE, MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(entry.dateMillis)
                        Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = activePalette.text.copy(alpha = 0.6f))

                        Spacer(modifier = Modifier.height(8.dp))

                        if (entry.oneSentenceNote.isNotEmpty()) {
                            Text(
                                text = "Context Note: \"${entry.oneSentenceNote}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = activePalette.accent,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        if (entry.freeWriteText.isNotEmpty()) {
                            Text(
                                text = entry.freeWriteText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Serif,
                                color = activePalette.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (entry.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                entry.tags.split(",").forEach { tag ->
                                    val cleanTag = tag.trim()
                                    if (cleanTag.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .background(activePalette.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(cleanTag, fontSize = 10.sp, color = activePalette.accent, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Media attachment badges on the Card
                        if (entry.photoUri != null || entry.audioFilePath != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                if (entry.photoUri != null) {
                                    val isPr = entry.photoUri.startsWith("preset_")
                                    val displayName = if (isPr) entry.photoUri.removePrefix("preset_").replace("_", " ") else "Image file"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(activePalette.accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = "Photo attachment preset", tint = activePalette.accent, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("📷 $displayName", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = activePalette.text)
                                    }
                                }

                                if (entry.audioFilePath != null) {
                                    val isPrAud = entry.audioFilePath.startsWith("preset_")
                                    val displayName = if (isPrAud) entry.audioFilePath.removePrefix("preset_").replace("_", " ") else "Voice file"
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(SageGreen.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "Audio diary track", tint = SageGreen, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("🎵 $displayName", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = SageGreen)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.deleteJournalEntry(entry) },
                                colors = ButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.error,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color.LightGray
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete entry", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 11.sp, color = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue for One-Sentence Context Node
    if (contextOpenedModal) {
        Dialog(onDismissRequest = { contextOpenedModal = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Context Node Note",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SageGreen
                    )
                    Text(
                        "What is contributing to feeling $currentMoodSelected today? This creates a safe context index.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    OutlinedTextField(
                        value = contextNoteInput,
                        onValueChange = { contextNoteInput = it },
                        label = { Text("Describe in 1 sentence") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("context_note_text_field"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { contextOpenedModal = false }) {
                            Text("Skip Context")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { contextOpenedModal = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen)
                        ) {
                            Text("Save Context Node")
                        }
                    }
                }
            }
        }
    }
}

    // Detailed entry view overlay Dialog
    if (selectedViewEntry != null) {
        JournalEntryDetailDialog(
            entry = selectedViewEntry!!,
            viewModel = viewModel,
            onDismiss = { selectedViewEntry = null }
        )
    }
}
}

data class MoodSelectOption(
    val label: String,
    val id: String,
    val color: Color
)

// ==========================================================================================
// B. PERSONAL INSIGHTS & AI TRENDS
// ==========================================================================================

@Composable
fun PersonalInsightsView(viewModel: ClientFlowViewModel) {
    val entries by viewModel.personalEntriesState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val aiSummary by viewModel.currentAIWeeklySummary.collectAsStateWithLifecycle()
    val isGeneratingSum by viewModel.isGeneratingSummary.collectAsStateWithLifecycle()

    var activeChartFilter by remember { mutableStateOf("All") } // Week, Month, All

    // Calendar Selected Day Expanded list
    var calendarSelectedDayEntries by remember { mutableStateOf<List<PersonalJournalEntry>?>(null) }
    var calendarSelectedDateString by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Weekly/Monthly Summary Panel
        item {
            Card(
                border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = SageGreen.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI tool", tint = SageGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Supportive Reflections Tracker",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = SageGreen
                            )
                        }

                        if (isGeneratingSum) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SageGreen, strokeWidth = 2.dp)
                        }
                    }

                    Text(
                        "Generates compassionate summaries. Our algorithm evaluates correlation trends securely.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp
                    )

                    Divider()

                    if (aiSummary.isNotEmpty()) {
                        Text(
                            text = aiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 18.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active summaries generated yet. Click compile summary to scan trends.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.generatePersonalAISummary() },
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personal_ai_recalc"),
                        enabled = !isGeneratingSum
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = "Trigger button")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compile Supportive AI Weekly Summary")
                    }
                }
            }
        }

        // 2. MONHLY PERSONAL CALENDAR GRID
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7EE)), // Soft warm sand background
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, SageGreen.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header with cute calendar drawing & month label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Tune 2026",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF2D332D),
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                "Personal Aura Calendar Grid",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        
                        // Cute illustrated mini calendar doodle drawn live!
                        Canvas(modifier = Modifier.size(36.dp)) {
                            val w = size.width
                            val h = size.height
                            // Board
                            drawRoundRect(
                                color = Color(0xFFF0EAE1),
                                size = Size(w, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                            )
                            // Spiral loops at the top
                            for (i in 0..4) {
                                val cx = w * 0.15f + i * (w * 0.175f)
                                drawLine(
                                    color = Color.DarkGray,
                                    start = Offset(cx, -4f),
                                    end = Offset(cx, 6f),
                                    strokeWidth = 3f
                                )
                            }
                            // Accent line
                            drawRect(
                                color = Color(0xFF708B75),
                                topLeft = Offset(0f, 8f),
                                size = Size(w, h * 0.25f)
                            )
                            // Little checked heart
                            drawCircle(Color(0xFFE57373), radius = 5f, center = Offset(w*0.5f, h*0.65f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Color-coded circles represent your dominant custom emotion for that day. Tap any day to inspect details.",
                        color = Color(0xFF5D6354),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Week Header row - beautiful circular bubble design
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0EAE1), RoundedCornerShape(12.dp))
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { dayLabel ->
                            Text(
                                dayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF435345),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar grids rows matching June 2026 starting on Monday (Day 1)
                    val daysInJune = 30
                    val startingOffset = 1 // Monday start

                    var dayTracker = 1
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (weekRow in 0..5) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                for (cellCol in 0..6) {
                                    val cellIndex = weekRow * 7 + cellCol
                                    if (cellIndex < startingOffset || dayTracker > daysInJune) {
                                        Box(modifier = Modifier.size(32.dp))
                                    } else {
                                        val activeDayNum = dayTracker
                                        dayTracker++

                                        // Lookup entries registered around activeDayNum date offsets in June 2026
                                        val cCalendar = Calendar.getInstance().apply {
                                            set(2026, Calendar.JUNE, activeDayNum)
                                        }

                                        // Find entries matching this specific date
                                        val matchingEntries = entries.filter { entry ->
                                            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
                                            entryCal.get(Calendar.YEAR) == 2026 &&
                                            entryCal.get(Calendar.MONTH) == Calendar.JUNE &&
                                            entryCal.get(Calendar.DAY_OF_MONTH) == activeDayNum
                                        }

                                        val hasEntries = matchingEntries.isNotEmpty()
                                        val dominantMood = matchingEntries.firstOrNull()?.mood

                                        val cellColor = if (hasEntries) {
                                            when (dominantMood) {
                                                "Happy" -> HappyAura
                                                "Productive" -> ProductiveAura
                                                "Calm" -> CalmAura
                                                "Reflective" -> ReflectiveAura
                                                "Neutral" -> NeutralAura
                                                "Anxious" -> AnxiousAura
                                                "Overwhelmed" -> OverwhelmedAura
                                                else -> SageGreen
                                            }
                                        } else {
                                            Color.White.copy(alpha = 0.6f)
                                        }

                                        val isChosen = calendarSelectedDateString == "June $activeDayNum, 2026"

                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(cellColor)
                                                .border(
                                                    width = if (isChosen) 2.dp else if (hasEntries) 1.dp else 1.dp,
                                                    color = if (isChosen) Color(0xFFFFB74D) else if (hasEntries) Color.White.copy(alpha = 0.5f) else Color(0xFFE2DDD0),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    if (hasEntries) {
                                                        calendarSelectedDayEntries = matchingEntries
                                                        calendarSelectedDateString = "June $activeDayNum, 2026"
                                                    } else {
                                                        // Fallback friendly empty notice to avoid confusion
                                                        calendarSelectedDayEntries = emptyList()
                                                        calendarSelectedDateString = "June $activeDayNum, 2026"
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = activeDayNum.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasEntries) Color.White else Color(0xFF5D6354).copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expanded log panel from calendar: EXQUISITE ANALOG NOTEBOOK DESIGN
        if (calendarSelectedDayEntries != null) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Spiral ring notebook styled container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(Color(0xFFFFFDF2)) // Analog vintage notebook paper background
                        .border(1.dp, Color(0xFFD6CEB2), RoundedCornerShape(8.dp))
                ) {
                    // Draw spiral loops at the top of the notepad!
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Color(0xFFF1EDE0))
                    ) {
                        val w = size.width
                        val h = size.height
                        val dotCount = 14
                        val stepX = w / dotCount
                        for (i in 0 until dotCount) {
                            val cx = stepX * i + stepX / 2
                            // Metal binding coil loop
                            drawCircle(
                                color = Color(0xFF7D7A6F),
                                radius = 4f,
                                center = Offset(cx, h * 0.5f)
                            )
                            drawRoundRect(
                                color = Color(0xFFAFAFA6),
                                topLeft = Offset(cx - 3f, -4f),
                                size = Size(6f, h * 0.6f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = SageGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$calendarSelectedDateString Notepad",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF3E2723),
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            IconButton(
                                onClick = { calendarSelectedDayEntries = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close details", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (calendarSelectedDayEntries!!.isEmpty()) {
                            // Friendly analog empty state inside notebook!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No logs for this date. Go draft a brand new mood reflex or vent!",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            calendarSelectedDayEntries!!.forEach { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(
                                            color = Color.White.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(1.dp, Color(0xFFEFE8D0), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Emotions: ${item.mood}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = SageGreen
                                        )
                                        Text(
                                            text = "Sleep quality: ${item.sleepQuality}/10",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    if (item.oneSentenceNote.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Activity: ${item.oneSentenceNote}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF435345),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (item.freeWriteText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.freeWriteText,
                                            fontSize = 11.sp,
                                            color = Color(0xFF5D6354),
                                            lineHeight = 15.sp
                                        )
                                    }

                                    if (item.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            item.tags.split(",").forEach { tag ->
                                                val clean = tag.trim()
                                                if (clean.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(clean, fontSize = 9.sp, color = SageGreen, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. CO-RELATION CANVAS GRAPH
        item {
            Text(
                "Rest vs Mood Dynamic Correlation Line Chart",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                "Correlates Mood weights (1-9) with Sleep scores (1-10) chronologically. Visually confirms emotional triggers",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Date Range selection panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Week", "Month", "All").forEach { itemFilter ->
                    Button(
                        onClick = { activeChartFilter = itemFilter },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeChartFilter == itemFilter) SageGreen else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (activeChartFilter == itemFilter) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Text(itemFilter, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Drawing
            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(SageGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mood Weight Index", fontSize = 10.sp, color = Color.Gray)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(ReflectiveAura, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sleep Quality Index", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw native Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height

                            // Draw baseline grid lines
                            val gridLinesCount = 4
                            for (i in 0..gridLinesCount) {
                                val y = canvasHeight * i / gridLinesCount
                                drawLine(
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(canvasWidth, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // If we have entries, map coordinates and draw lines
                            if (entries.isNotEmpty()) {
                                val itemsToDraw = entries.sortedBy { it.dateMillis }.takeLast(
                                    when (activeChartFilter) {
                                        "Week" -> 7
                                        "Month" -> 15
                                        else -> 30
                                    }
                                )

                                if (itemsToDraw.size > 1) {
                                    val stepX = canvasWidth / (itemsToDraw.size - 1)

                                    val moodPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                                    val sleepPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()

                                    itemsToDraw.forEachIndexed { idx, log ->
                                        val x = idx * stepX
                                        // Mood weight range is 1..10 (Productive=9, Calm=8, Reflective=7, Neutral=5, Anxious=3, Overwhelmed=2)
                                        val yMood = canvasHeight - ((log.moodWeight.toFloat() / 10f) * canvasHeight)
                                        // Sleep score ranges 1..10
                                        val ySleep = canvasHeight - ((log.sleepQuality.toFloat() / 10f) * canvasHeight)

                                        moodPoints.add(androidx.compose.ui.geometry.Offset(x, yMood))
                                        sleepPoints.add(androidx.compose.ui.geometry.Offset(x, ySleep))
                                    }

                                    // Render Mood line (Sage Green)
                                    val pathMood = Path().apply {
                                        moveTo(moodPoints[0].x, moodPoints[0].y)
                                        for (i in 1 until moodPoints.size) {
                                            lineTo(moodPoints[i].x, moodPoints[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = pathMood,
                                        color = SageGreen,
                                        style = Stroke(width = 3.dp.toPx())
                                    )

                                    // Draw Mood points
                                    moodPoints.forEach { pt ->
                                        drawCircle(color = SageGreen, radius = 5.dp.toPx(), center = pt)
                                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                                    }

                                    // Render Sleep line (Reflective Blue)
                                    val pathSleep = Path().apply {
                                        moveTo(sleepPoints[0].x, sleepPoints[0].y)
                                        for (i in 1 until sleepPoints.size) {
                                            lineTo(sleepPoints[i].x, sleepPoints[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = pathSleep,
                                        color = ReflectiveAura,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )

                                    // Draw Sleep points
                                    sleepPoints.forEach { pt ->
                                        drawCircle(color = ReflectiveAura, radius = 4.dp.toPx(), center = pt)
                                        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = pt)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Historical Chronology Timeline Track (Left to Right)",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Reminders & Google calendar sync mock settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interactive Companion Prompts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Get suggested questions & automatically map journaling schedules to your connected calendar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(checkedThumbColor = SageGreen)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ==========================================================================================
// C. PRACTITIONER WORKSPACE DASHBOARD
// ==========================================================================================

@Composable
fun PractitionerCaseloadView(viewModel: ClientFlowViewModel) {
    val patients by viewModel.patientsState.collectAsStateWithLifecycle()
    val rawSessions by viewModel.clinicalSessionsState.collectAsStateWithLifecycle()
    val selectedPatientId by viewModel.currentPatientId.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isClientDataUnlocked by viewModel.isClientDataUnlocked.collectAsStateWithLifecycle()

    if (settings?.clientDataBiometricLocked == true && !isClientDataUnlocked) {
        BiometricLockOverlay(
            title = "Client Records & Caseload Locked",
            description = "Biometric protection is active. Verify identity with fingerprint, face recognition, or PIN to view clinical caseload records.",
            onUnlockBiometric = { activity, onSuccess, onError ->
                viewModel.unlockClientDataWithBiometrics(activity, onSuccess, onError)
            },
            onUnlockPin = { pin ->
                viewModel.unlockClientDataWithPin(pin)
            }
        )
        return
    }

    val currentSelectedPatient = patients.find { it.id == selectedPatientId }

    val maskActive = settings?.maskClientNames ?: false
    val blurActive = settings?.blurClinicalNotes ?: false
    val obfuscateActive = settings?.obfuscateContacts ?: false

    var isAddingNewPatient by remember { mutableStateOf(false) }

    // Patient Form State fields
    var newPatientName by remember { mutableStateOf("") }
    var newPatientEmail by remember { mutableStateOf("") }
    var newPatientPhone by remember { mutableStateOf("") }
    var newPatientDiagnosis by remember { mutableStateOf("Adjustment Disorder (F43.23)") }
    var newPatientHomeworkTitle by remember { mutableStateOf("") }
    var newPatientPhase by remember { mutableStateOf("Assessment") }
    var newPatientSleepDecline by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Dev Sandbox toolbar buttons
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Workspace Prototyping Controls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TealPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Wipe or clear caseload data parameters to start with fresh real clinical records.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.wipeClinicalSandbox() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("sandbox_wipe_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear data", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All Caseload Data", fontSize = 11.sp)
                    }
                }
            }
        }

        // Performance metrics overview grid
        item {
            Text("Practice Aggregate Outcomes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Aggregates files logged securely in local tables, representing compliance curves and fatigue anomalies",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Dynamic computations based on current sandbox state
            val activeFiles = patients.size
            val avgComp = if (patients.isNotEmpty()) (patients.map { it.homeworkProgress }.average() * 100).toInt() else 0
            val scheduledMonth = patients.size * 4 // simulated
            val declineAlerts = patients.filter { it.isDecliningSleep }.size

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricIndicatorCard(
                    title = "Active Cases",
                    value = activeFiles.toString(),
                    color = TealPrimary,
                    modifier = Modifier.weight(1f).testTag("metric_active_cases")
                )
                MetricIndicatorCard(
                    title = "Avg Homework",
                    value = "$avgComp%",
                    color = SageGreen,
                    modifier = Modifier.weight(1f).testTag("metric_homework")
                )
                MetricIndicatorCard(
                    title = "Month Sessions",
                    value = scheduledMonth.toString(),
                    color = ReflectiveAura,
                    modifier = Modifier.weight(1f).testTag("metric_month_sessions")
                )
                MetricIndicatorCard(
                    title = "Sleep Alerts",
                    value = declineAlerts.toString(),
                    color = OverwhelmedAura,
                    modifier = Modifier.weight(1f).testTag("metric_sleep_alerts")
                )
            }
        }

        // Privacy Controls strip panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confidential Protection Shields", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Mask Patient Names", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("Replaces client names with private Clinical IDs", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = maskActive,
                            onCheckedChange = { viewModel.toggleMaskNames(it) },
                            modifier = Modifier.testTag("shield_mask_names")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Obfuscate Contact Details", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("Soft blurs telephone number and email parameters", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = obfuscateActive,
                            onCheckedChange = { viewModel.toggleObfuscateContacts(it) },
                            modifier = Modifier.testTag("shield_contacts")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Blur Subjective Session Notes", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("Confidential consultation text only reveals under active hover focus", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = blurActive,
                            onCheckedChange = { viewModel.toggleBlurNotes(it) },
                            modifier = Modifier.testTag("shield_clinical_notes")
                        )
                    }
                }
            }
        }

        // Caseload Client Header Index
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clinical Caseload Manifest", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Select a patient structure below to examine case files details", color = Color.Gray, fontSize = 11.sp)
                }

                Button(
                    onClick = { isAddingNewPatient = true },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier.testTag("add_client_launcher")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add client")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Client", fontSize = 11.sp)
                }
            }
        }

        // Caseload manifestation
        if (patients.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Your diagnostic caseload is empty. Click seed or tap add client.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            items(patients) { client ->
                val clinicalName = if (maskActive) {
                    "Patient S-ID-${client.id.takeLast(4)}"
                } else {
                    client.name
                }

                val clinicalEmail = if (obfuscateActive) "s.***@***.com" else client.email
                val clinicalPhone = if (obfuscateActive) "+1 (***) ***-****" else client.phone

                val isSelectedCandidate = selectedPatientId == client.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelectedCandidate) 1.5.dp else 1.dp,
                            color = if (isSelectedCandidate) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (isSelectedCandidate) {
                                viewModel.selectPatient(null)
                            } else {
                                viewModel.selectPatient(client.id)
                            }
                        }
                        .testTag("client_card_${client.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelectedCandidate) TealPrimary.copy(alpha = 0.02f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(clinicalName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("ID Reference: ${client.id}", fontSize = 11.sp, color = Color.Gray)
                            }

                            // Alerts badge for sleep decline
                            if (client.isDecliningSleep) {
                                Box(
                                    modifier = Modifier
                                        .background(OverwhelmedAura.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "⚠️ Sleep Decline Alert",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OverwhelmedAura
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Contacts details
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("✉️ $clinicalEmail", fontSize = 11.sp, color = Color.DarkGray)
                            Text("📞 $clinicalPhone", fontSize = 11.sp, color = Color.DarkGray)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Diagnostic: ${client.diagnosis}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Therapeutic Phase Framework: ${client.therapeuticPhase}", fontSize = 11.sp, color = Color.Gray)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Homework Completion: ${(client.homeworkProgress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                // Custom progress indicators bar
                                Box(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(6.dp)
                                        .background(Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(client.homeworkProgress)
                                            .background(SageGreen, RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }

                        // Render sessions list if selected
                        if (isSelectedCandidate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            ClientDetailedExplorationPanel(
                                patient = client,
                                blurNotes = blurActive,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialogue to create client
    if (isAddingNewPatient) {
        Dialog(onDismissRequest = { isAddingNewPatient = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add New Client File", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealPrimary)
                        IconButton(onClick = { isAddingNewPatient = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close overlay")
                        }
                    }

                    Divider()

                    OutlinedTextField(
                        value = newPatientName,
                        onValueChange = { newPatientName = it },
                        label = { Text("Legal Patient Full Name") },
                        modifier = Modifier.fillMaxWidth().testTag("add_client_name"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = newPatientEmail,
                        onValueChange = { newPatientEmail = it },
                        label = { Text("Contact Email Address") },
                        modifier = Modifier.fillMaxWidth().testTag("add_client_email"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = newPatientPhone,
                        onValueChange = { newPatientPhone = it },
                        label = { Text("Mobile Contact Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = newPatientDiagnosis,
                        onValueChange = { newPatientDiagnosis = it },
                        label = { Text("Primary Diagnosis (e.g. F43.23)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = newPatientHomeworkTitle,
                        onValueChange = { newPatientHomeworkTitle = it },
                        label = { Text("Homework Assignment Objective") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    // Phase Selector
                    Text("Therapeutic Alliance Phase", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Assessment", "Active Intervention", "Maintenance").forEach { phase ->
                            Button(
                                onClick = { newPatientPhase = phase },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (newPatientPhase == phase) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (newPatientPhase == phase) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(phase, fontSize = 9.sp)
                            }
                        }
                    }

                    // Sleep anomaly toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Sleep Decline Flag", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Switch(
                            checked = newPatientSleepDecline,
                            onCheckedChange = { newPatientSleepDecline = it }
                        )
                    }

                    Button(
                        onClick = {
                            if (newPatientName.isNotEmpty()) {
                                viewModel.addPatient(
                                    name = newPatientName,
                                    email = newPatientEmail,
                                    phone = newPatientPhone,
                                    diagnosis = newPatientDiagnosis,
                                    homeworkName = newPatientHomeworkTitle,
                                    homeworkProgress = 0.50f,
                                    therapeuticPhase = newPatientPhase,
                                    isDecliningSleep = newPatientSleepDecline
                                )
                                // reset
                                newPatientName = ""
                                newPatientEmail = ""
                                newPatientPhone = ""
                                newPatientHomeworkTitle = ""
                                isAddingNewPatient = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_client_save_trigger")
                    ) {
                        Text("Establish Client File")
                    }
                }
            }
        }
    }
}

@Composable
fun MetricIndicatorCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ==========================================================================================
// CLIENT DETAIL & CONSULT LOG FORM EXPORTS
// ==========================================================================================

@Composable
fun ClientDetailedExplorationPanel(
    patient: Patient,
    blurNotes: Boolean,
    viewModel: ClientFlowViewModel
) {
    val patientSessions by viewModel.getSessionsForSelectedPatient().collectAsStateWithLifecycle(initialValue = emptyList())
    val aiBrief by viewModel.currentClinicalBrief.collectAsStateWithLifecycle()
    val isGenBrief by viewModel.isGeneratingBrief.collectAsStateWithLifecycle()
    val localContext = androidx.compose.ui.platform.LocalContext.current

    var showLogSessionDialog by remember { mutableStateOf(false) }

    // Consult Log Form States
    var consultDuration by remember { mutableStateOf(50f) }
    var consultSessionMood by remember { mutableStateOf("Neutral") }
    var consultObservations by remember { mutableStateOf("") }
    var consultHomeworkCheck by remember { mutableStateOf("") }
    var consultEnergyRating by remember { mutableStateOf(5f) }
    var consultSleepRating by remember { mutableStateOf(5f) }
    var consultFocusTags by remember { mutableStateOf("") }
    var consultNotesText by remember { mutableStateOf("") }
    var consultAttachedMediaPath by remember { mutableStateOf<String?>("Intake_Worksheet_Sarah.pdf") }

    // Multi-Format Clinical Session Note Template Engine States
    var activeNoteTemplateMode by remember { mutableStateOf("Free-form") } // "Free-form", "SOAP", "BIRP"
    // SOAP state inputs
    var soapSubjective by remember { mutableStateOf("") }
    var soapObjective by remember { mutableStateOf("") }
    var soapAssessment by remember { mutableStateOf("") }
    var soapPlan by remember { mutableStateOf("") }
    // BIRP state inputs
    var birpBehavior by remember { mutableStateOf("") }
    var birpIntervention by remember { mutableStateOf("") }
    var birpResponse by remember { mutableStateOf("") }
    var birpPlan by remember { mutableStateOf("") }

    val sessionMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            consultAttachedMediaPath = uri.toString()
        }
    }

    // Theme Trajectory Comparator Selection States
    var compareSessionA by remember { mutableStateOf<ClinicalSessionLog?>(null) }
    var compareSessionB by remember { mutableStateOf<ClinicalSessionLog?>(null) }
    var showCompareEngineView by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1. PRE-SESSION AI BRIEF GENERATOR
        Card(
            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.02f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI brief", tint = TealPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pre-Session Clinical AI Preparation Brief", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealPrimary)
                    }

                    if (isGenBrief) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TealPrimary, strokeWidth = 2.dp)
                    }
                }

                Text(
                    "Synthesizes preceding session logs, anomalies, and sleep rating trends automatically.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                if (aiBrief.isNotEmpty()) {
                    Text(
                        text = aiBrief,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("AI Pre-Session Brief ready. Click execute prep brief.", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = { viewModel.generatePreSessionBrief(patient) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("clinical_ai_prep_trigger"),
                    enabled = !isGenBrief
                ) {
                    Text("Execute Pre-Session Brief Generator", fontSize = 11.sp)
                }
            }
        }

        // Action tools Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showLogSessionDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("log_session_trigger")
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = "Log", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log Consultation", fontSize = 11.sp)
            }

            // Compare Launcher trigger if at least 2 sessions exist
            val canCompare = patientSessions.size >= 2
            Button(
                onClick = {
                    compareSessionA = patientSessions.getOrNull(0)
                    compareSessionB = patientSessions.getOrNull(1)
                    showCompareEngineView = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canCompare) SageGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (canCompare) Color.White else Color.Gray
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .testTag("trajectory_compare_trigger"),
                enabled = canCompare
            ) {
                Icon(Icons.Default.Compare, contentDescription = "Compare", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Compare Trajectory", fontSize = 10.sp)
            }
        }

        // Historical sessions Manifest logs
        Text("Consultation Chronicles", fontWeight = FontWeight.Bold, fontSize = 13.sp)

        if (patientSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No session chronicles logged for this file yet.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patientSessions.forEach { item ->
                    var isHovered by remember { mutableStateOf(false) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isHovered = !isHovered }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(item.dateMillis)
                                Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(TealPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Duration: ${item.durationMinutes}m", fontSize = 9.sp, color = TealPrimary)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(SageGreen.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Mood: ${item.sessionMood}", fontSize = 9.sp, color = SageGreen)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Contact details and notes (blurred if active, clear under hover)
                            val noteModifier = if (blurNotes && !isHovered) {
                                Modifier
                                    .blur(8.dp)
                                    .testTag("blurred_note_block")
                            } else {
                                Modifier.testTag("clear_note_block")
                            }

                            Column(modifier = noteModifier) {
                                Text("Observations: ${item.objectiveObservations}", fontSize = 11.sp)
                                if (item.notes.isNotEmpty()) {
                                    Text("Progress notes: ${item.notes}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("Homework check status: ${item.homeworkCheck}", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)

                                if (item.mediaAttachmentPath != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val isMediaAudio = item.mediaAttachmentPath.endsWith(".mp3") || item.mediaAttachmentPath.contains("Audio")
                                    val filename = item.mediaAttachmentPath.substringAfterLast("/")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(TealPrimary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                            .border(1.dp, TealPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                Toast.makeText(localContext, "Loaded clinical workspace file: $filename", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMediaAudio) Icons.Default.VolumeUp else Icons.Default.Description,
                                            contentDescription = "Clinical File Format Indicator",
                                            tint = TealPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Attached Document: $filename",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                    }
                                }
                            }

                            if (blurNotes && !isHovered) {
                                Text(
                                    "[Clinical Shield Blur Active. Tap card to hover-reveal logs]",
                                    color = OverwhelmedAura,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sleep rating reported: ${item.sleepScore}/10", fontSize = 10.sp, color = Color.DarkGray)
                                IconButton(
                                    onClick = { viewModel.deleteSession(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove session log", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue Log Session Form Modal
    if (showLogSessionDialog) {
        Dialog(onDismissRequest = { showLogSessionDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Log Consultation Notes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealPrimary)
                        IconButton(onClick = { showLogSessionDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close overlay")
                        }
                    }

                    Divider()

                    // Record Duration slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Consultation Length (Minutes)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${consultDuration.toInt()} min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                        }
                        Slider(
                            value = consultDuration,
                            onValueChange = { consultDuration = it },
                            valueRange = 10f..120f,
                            steps = 11,
                            colors = SliderDefaults.colors(thumbColor = TealPrimary, activeTrackColor = TealPrimary)
                        )
                    }

                    // Session mood selection
                    Text("Consultation Dominant Emotion", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Positive", "Neutral", "Difficult").forEach { mood ->
                            Button(
                                onClick = { consultSessionMood = mood },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (consultSessionMood == mood) SageGreen else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (consultSessionMood == mood) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mood, fontSize = 10.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = consultObservations,
                        onValueChange = { consultObservations = it },
                        label = { Text("Physical and Somatic Objective Observations") },
                        modifier = Modifier.fillMaxWidth().testTag("add_session_obs"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    OutlinedTextField(
                        value = consultHomeworkCheck,
                        onValueChange = { consultHomeworkCheck = it },
                        label = { Text("Therapeutic Homework Compliance Audit") },
                        modifier = Modifier.fillMaxWidth().testTag("add_session_hw"),
                        colors = OutlinedTextFieldDefaults.colors()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = consultFocusTags,
                            onValueChange = { consultFocusTags = it },
                            label = { Text("Clinical Tags (CBT, ACT)") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                    }

                    // Sleep / Energy ratings
                    Column {
                        Text("Sleep score index reported: ${consultSleepRating.toInt()}/10", fontSize = 11.sp)
                        Slider(
                            value = consultSleepRating,
                            onValueChange = { consultSleepRating = it },
                            valueRange = 1f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(thumbColor = TealPrimary)
                        )
                    }

                    // Structured Clinical Template Selector Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Structured Clinical Template Engine", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TealPrimary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Free-form", "SOAP", "BIRP").forEach { mode ->
                                val active = activeNoteTemplateMode == mode
                                Button(
                                    onClick = { activeNoteTemplateMode = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(34.dp).testTag("select_note_format_$mode"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    val modeLabel = when (mode) {
                                        "SOAP" -> "🧼 SOAP Form"
                                        "BIRP" -> "🦅 BIRP Form"
                                        else -> "📝 Freeform"
                                    }
                                    Text(modeLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Depending on the selected active Clinical Template Type
                    when (activeNoteTemplateMode) {
                        "SOAP" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("SOAP Methodical Dimensions", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    // Live Diagnostic Auto-filler preset for validation
                                    Text(
                                        text = "⚡ Load Model CBT SOAP Preset",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary,
                                        modifier = Modifier.clickable {
                                            soapSubjective = "Client reports intense presentation anxiety, stating: 'My heart loops and hands tremble whenever speaking in public.' Notes sleep disruption due to high rumination."
                                            soapObjective = "Observable foot-tapping, rapid respiration during speech, fidgeted minorly. Maintained cooperative tone but avoided prolonged direct eye-contact."
                                            soapAssessment = "Cognitive distortions include severe catastrophizing and overgeneralization of failure. Exhibited rapid reduction in distressing triggers via cognitive reframing drill."
                                            soapPlan = "Homework assigned: 5-minute somatic square-breathing logs and daily trigger analysis sheet. Scheduled checkpoint next Monday."
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = soapSubjective,
                                    onValueChange = { soapSubjective = it },
                                    label = { Text("Subjective (S) - Client symptoms & reported emotions") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("soap_s"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = soapObjective,
                                    onValueChange = { soapObjective = it },
                                    label = { Text("Objective (O) - Practitioner somatic findings & MSE") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("soap_o"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = soapAssessment,
                                    onValueChange = { soapAssessment = it },
                                    label = { Text("Assessment (A) - Clinical hypothesis, analysis & metrics") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("soap_a"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = soapPlan,
                                    onValueChange = { soapPlan = it },
                                    label = { Text("Plan (P) - Treatment steps, goals & workbook drill tracker") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("soap_p"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                            }
                        }
                        "BIRP" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("BIRP Behavioral Milestones", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                    // Live Diagnostic Auto-filler preset for validation
                                    Text(
                                        text = "⚡ Load Model ACT BIRP Preset",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary,
                                        modifier = Modifier.clickable {
                                            birpBehavior = "Client appeared listless with low speech volume. Mood self-rating reported at 3/10. Complained of high fatigue."
                                            birpIntervention = "Facilitated structured ACT values-clarification exercise regarding professional development. Explored career value pathways."
                                            birpResponse = "Assimilated positive feedback after 20 mins. Stated: 'Focusing on core values relieves my chronic decision-making dread'."
                                            birpPlan = "Client will outline two values-aligned tiny daily tasks on physical tracking ledger. Scheduled check-in in 8 days."
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = birpBehavior,
                                    onValueChange = { birpBehavior = it },
                                    label = { Text("Behavior (B) - Presenting traits & verbal reports") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("birp_b"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = birpIntervention,
                                    onValueChange = { birpIntervention = it },
                                    label = { Text("Intervention (I) - Applied therapeutic techniques") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("birp_i"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = birpResponse,
                                    onValueChange = { birpResponse = it },
                                    label = { Text("Response (R) - Client's immediate clinical session feedback") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("birp_r"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                OutlinedTextField(
                                    value = birpPlan,
                                    onValueChange = { birpPlan = it },
                                    label = { Text("Plan (P) - Future items & scheduled exercises") },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("birp_p"),
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = consultNotesText,
                                onValueChange = { consultNotesText = it },
                                label = { Text("Subjective Clinical Conversation Notes") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).testTag("freeform_notes_text"),
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                        }
                    }

                    // Worksheets & Documents Attachments Section
                    Text("Session Worksheets / Clinical Intake Attachments", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = TealPrimary)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("session_media_attachments"),
                        colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.04f)),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = "Attach session documents",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (consultAttachedMediaPath == null) "No document attached" else "Active Attachment",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = TealPrimary
                                    )
                                }
                                
                                if (consultAttachedMediaPath != null) {
                                    Text(
                                        text = "Clear",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { consultAttachedMediaPath = null }
                                    )
                                }
                            }

                            if (consultAttachedMediaPath != null) {
                                val isPresetDoc = !consultAttachedMediaPath!!.contains("content://") && !consultAttachedMediaPath!!.contains("file://")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, TealPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (consultAttachedMediaPath!!.endsWith(".mp3") || consultAttachedMediaPath!!.contains("Audio")) Icons.Default.VolumeUp else Icons.Default.Description,
                                        contentDescription = "File format decoration",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = consultAttachedMediaPath!!.substringAfterLast("/"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Picker Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { sessionMediaLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealPrimary.copy(alpha = 0.1f),
                                        contentColor = TealPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp).weight(1f)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = "Pick worksheet", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload From Device", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Quick Presets
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Select Clinical Template Presets:", fontSize = 8.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "Intake_Checklist.pdf",
                                        "CBT_Reflections_Somatic.docx",
                                        "Sleep_Progression_Scale.xlsx",
                                        "Calm_Audio_Session.mp3"
                                    ).forEach { templateName ->
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                                .border(1.dp, TealPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .clickable { consultAttachedMediaPath = templateName }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(templateName, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val compiledStructuredText = when (activeNoteTemplateMode) {
                                "SOAP" -> """
                                    |=== STRUCTURAL SOAP Clinical Note ===
                                    |Subjective (S): ${soapSubjective.ifBlank { "N/A" }}
                                    |Objective (O): ${soapObjective.ifBlank { "N/A" }}
                                    |Assessment (A): ${soapAssessment.ifBlank { "N/A" }}
                                    |Plan (P): ${soapPlan.ifBlank { "N/A" }}
                                """.trimMargin()
                                "BIRP" -> """
                                    |=== STRUCTURAL BIRP Clinical Note ===
                                    |Behavior (B): ${birpBehavior.ifBlank { "N/A" }}
                                    |Intervention (I): ${birpIntervention.ifBlank { "N/A" }}
                                    |Response (R): ${birpResponse.ifBlank { "N/A" }}
                                    |Plan (P): ${birpPlan.ifBlank { "N/A" }}
                                """.trimMargin()
                                else -> consultNotesText
                            }

                            viewModel.addClinicalSession(
                                patientId = patient.id,
                                duration = consultDuration.toInt(),
                                mood = consultSessionMood,
                                observations = consultObservations,
                                homeworkCheck = consultHomeworkCheck,
                                energy = consultEnergyRating.toInt(),
                                sleep = consultSleepRating.toInt(),
                                tags = consultFocusTags,
                                notes = compiledStructuredText,
                                mediaPath = consultAttachedMediaPath
                            )
                            // reset
                            consultObservations = ""
                            consultHomeworkCheck = ""
                            consultNotesText = ""
                            consultFocusTags = ""
                            soapSubjective = ""
                            soapObjective = ""
                            soapAssessment = ""
                            soapPlan = ""
                            birpBehavior = ""
                            birpIntervention = ""
                            birpResponse = ""
                            birpPlan = ""
                            activeNoteTemplateMode = "Free-form"
                            consultAttachedMediaPath = "Intake_Worksheet_Sarah.pdf"
                            showLogSessionDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_session_submit")
                    ) {
                        Text("Save Clinical Session Log Parameters")
                    }
                }
            }
        }
    }

    // Trajectory Comparison Delta overlay
    if (showCompareEngineView && compareSessionA != null && compareSessionB != null) {
        val sA = compareSessionA!!
        val sB = compareSessionB!!
        Dialog(onDismissRequest = { showCompareEngineView = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Delta Trajectory Comparison Viewport", fontWeight = FontWeight.Bold, color = SageGreen)
                        IconButton(onClick = { showCompareEngineView = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close overlay")
                        }
                    }

                    Divider()

                    Text(
                        "Evaluates progress anomalies by contrasting metrics across selected sessions.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    // Delta comparison cards
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Session Baseline (Old)", fontSize = 10.sp, color = Color.Gray)
                                Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(sA.dateMillis), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Duration: ${sA.durationMinutes}m", fontSize = 11.sp)
                                Text("Mood: ${sA.sessionMood}", fontSize = 11.sp)
                                Text("Sleep rate: ${sA.sleepScore}/10", fontSize = 11.sp)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Session Horizon (New)", fontSize = 10.sp, color = Color.Gray)
                                Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(sB.dateMillis), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Duration: ${sB.durationMinutes}m", fontSize = 11.sp)
                                Text("Mood: ${sB.sessionMood}", fontSize = 11.sp)
                                Text("Sleep rate: ${sB.sleepScore}/10", fontSize = 11.sp)
                            }
                        }
                    }

                    // Computed Delta Metrics Table
                    Card(
                        border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(containerColor = SageGreen.copy(alpha = 0.02f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Progress Delta Metrics", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SageGreen)

                            val sleepDiff = sB.sleepScore - sA.sleepScore
                            val durationDiff = sB.durationMinutes - sA.durationMinutes

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sleep Change: ", fontSize = 11.sp)
                                Text(
                                    text = if (sleepDiff >= 0) "+$sleepDiff (Approved)" else "$sleepDiff (Anomaly)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sleepDiff >= 0) SageGreen else OverwhelmedAura
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Notes Focus Length: ", fontSize = 11.sp)
                                Text(
                                    text = if (durationDiff >= 0) "+${durationDiff}m" else "${durationDiff}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(4.dp))

                            Text("Diagnostic Observations Differential", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                            Text("S1 observations: \"${sA.objectiveObservations}\"", fontSize = 11.sp)
                            Text("S2 observations: \"${sB.objectiveObservations}\"", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// D. PRACTITIONER CALENDAR PRESENCE ARCHIVES
// ==========================================================================================

@Composable
fun PractitionerPresencesView(viewModel: ClientFlowViewModel) {
    val rawSessions by viewModel.clinicalSessionsState.collectAsStateWithLifecycle()
    val patients by viewModel.patientsState.collectAsStateWithLifecycle()
    val localContext = androidx.compose.ui.platform.LocalContext.current

    var selectedCalendarPatientFilter by remember { mutableStateOf<String?>("All") }
    var presenceSelectedDayNotes by remember { mutableStateOf<List<ClinicalSessionLog>?>(null) }
    var selectedPresenceDateString by remember { mutableStateOf("") }

    val filteredSessions = if (selectedCalendarPatientFilter == "All") {
        rawSessions
    } else {
        rawSessions.filter { it.patientId == selectedCalendarPatientFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Practice Calendar Presence archives", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Chronological session tracking mapping clinical logs into colored timeline dots based on client mood",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Patient Selector filters
            Text("Filter Presence Calendar by Caseload", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { selectedCalendarPatientFilter = "All" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedCalendarPatientFilter == "All") TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedCalendarPatientFilter == "All") Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("All Caseload", fontSize = 10.sp)
                }

                patients.forEach { patient ->
                    Button(
                        onClick = { selectedCalendarPatientFilter = patient.id },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedCalendarPatientFilter == patient.id) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedCalendarPatientFilter == patient.id) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(patient.name, fontSize = 10.sp)
                    }
                }
            }
        }

        // Calendar Presence Monthly mapping
        item {
            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Clinical Sessions Chronicles - June 2026",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TealPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Week row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { dayLabel ->
                            Text(
                                dayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val daysInJune = 30
                    val startingOffset = 1 // Monday start

                    var dayTracker = 1
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (weekRow in 0..5) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                for (cellCol in 0..6) {
                                    val cellIndex = weekRow * 7 + cellCol
                                    if (cellIndex < startingOffset || dayTracker > daysInJune) {
                                        Box(modifier = Modifier.size(28.dp))
                                    } else {
                                        val activeDayNum = dayTracker
                                        dayTracker++

                                        // Filter sessions on this day
                                        val targetCal = Calendar.getInstance().apply { set(2026, Calendar.JUNE, activeDayNum) }
                                        val matchSessions = filteredSessions.filter { s ->
                                            val sCal = Calendar.getInstance().apply { timeInMillis = s.dateMillis }
                                            sCal.get(Calendar.YEAR) == 2026 &&
                                            sCal.get(Calendar.MONTH) == Calendar.JUNE &&
                                            sCal.get(Calendar.DAY_OF_MONTH) == activeDayNum
                                        }

                                        val hasSession = matchSessions.isNotEmpty()
                                        val cellType = if (hasSession) {
                                            when (matchSessions.firstOrNull()?.sessionMood) {
                                                "Positive" -> CalmAura
                                                "Neutral" -> NeutralAura
                                                "Difficult" -> OverwhelmedAura
                                                else -> TealPrimary
                                            }
                                        } else {
                                            Color.LightGray.copy(alpha = 0.15f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(cellType)
                                                .border(
                                                    width = if (hasSession) 1.5.dp else 0.dp,
                                                    color = if (hasSession) Color.White else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable {
                                                    if (hasSession) {
                                                        presenceSelectedDayNotes = matchSessions
                                                        selectedPresenceDateString = "June $activeDayNum, 2026"
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                activeDayNum.toString(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasSession) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expanded presence logs from calendar selection
        if (presenceSelectedDayNotes != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chronicle Session logs: $selectedPresenceDateString", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealPrimary)
                            IconButton(onClick = { presenceSelectedDayNotes = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close details", modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        presenceSelectedDayNotes!!.forEach { sessionLog ->
                            val clientName = patients.find { it.id == sessionLog.patientId }?.name ?: "Unknown"
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("• Client: $clientName (ID: ${sessionLog.patientId})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("  Duration: ${sessionLog.durationMinutes} mins | Mood: ${sessionLog.sessionMood}", fontSize = 11.sp)
                                Text("  Observations: \"${sessionLog.objectiveObservations}\"", fontSize = 11.sp)
                                if (sessionLog.notes.isNotEmpty()) {
                                    Text("  Session Progress: ${sessionLog.notes}", fontSize = 11.sp, color = Color.Gray)
                                }
                                if (sessionLog.mediaAttachmentPath != null) {
                                    val isMediaAudio = sessionLog.mediaAttachmentPath.endsWith(".mp3") || sessionLog.mediaAttachmentPath.contains("Audio")
                                    val filename = sessionLog.mediaAttachmentPath.substringAfterLast("/")
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(start = 12.dp, top = 4.dp)
                                            .background(TealPrimary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                            .clickable {
                                                Toast.makeText(localContext, "Loaded clinical file: $filename", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMediaAudio) Icons.Default.VolumeUp else Icons.Default.Description,
                                            contentDescription = "Clinical Document Attachment Indicator",
                                            tint = TealPrimary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Worksheet: $filename",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Aggregate Aura statistics summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Outdoors Aura Weights Ledger", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🟢 Emerald / Teal Aura (Positive/Calm):", fontSize = 12.sp)
                        Text(filteredSessions.filter { it.sessionMood == "Positive" }.size.toString() + " logs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🟡 Amber / Indigo Aura (Neutral/Reflective):", fontSize = 12.sp)
                        Text(filteredSessions.filter { it.sessionMood == "Neutral" }.size.toString() + " logs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🔴 Rose / Orange Aura (Difficult/Overwhelmed):", fontSize = 12.sp)
                        Text(filteredSessions.filter { it.sessionMood == "Difficult" }.size.toString() + " logs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
