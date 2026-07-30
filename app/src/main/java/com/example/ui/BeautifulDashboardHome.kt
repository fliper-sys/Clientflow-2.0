package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ClinicalSessionLog
import com.example.data.Patient
import com.example.data.PersonalJournalEntry
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BeautifulDashboardHome(
    viewModel: ClientFlowViewModel,
    activeMode: String,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val personalEntries by viewModel.personalEntriesState.collectAsStateWithLifecycle()
    val patients by viewModel.patientsState.collectAsStateWithLifecycle()
    val clinicalSessions by viewModel.clinicalSessionsState.collectAsStateWithLifecycle()
    val scheduledItems by viewModel.scheduledItemsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()

    var showGiftClaimsSuccess by remember { mutableStateOf(false) }
    var streakClaimedCount by rememberSaveable { mutableStateOf(7) }
    var giftSurpriseClaimed by rememberSaveable { mutableStateOf(false) }

    // Interactivity state maps for checklists (remembers check states in runtime)
    val checkedPersonalTodos = remember { mutableStateMapOf<String, Boolean>() }
    val checkedPractitionerTodos = remember { mutableStateMapOf<String, Boolean>() }

    // Selected entry modal drill-down
    var activeReflectDetailsModal by remember { mutableStateOf<PersonalJournalEntry?>(null) }

    // Interactive Fullscreen Overlays State
    var activeRantSession by remember { mutableStateOf(false) }
    var activeReflectSession by remember { mutableStateOf(false) }
    var showConstructorDialog by remember { mutableStateOf(false) }
    var selectedGraphIndex by remember { mutableStateOf(4) } // Default selection Fri 19th


    // Diary Constructor Toggles (custom settings to prioritize custom activities modes)
    var isRantEnabled by remember { mutableStateOf(true) }
    var isReflectEnabled by remember { mutableStateOf(true) }
    var isEmotionsEnabled by remember { mutableStateOf(true) }
    var isGratitudeEnabled by remember { mutableStateOf(true) }
    var isGoodDeedsEnabled by remember { mutableStateOf(true) }
    var isTasksEnabled by remember { mutableStateOf(true) }

    // Live custom items
    val customGratitudes = remember { mutableStateListOf<String>() }
    val customGoodDeeds = remember { mutableStateMapOf<String, Boolean>() }

    // Next upcoming patient from real caseload
    val nextUpcomingPatient = patients.sortedBy { it.id }.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("beautiful_home_screen_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HEADER WELCOME SECTION & STREAK TRACK
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeMode == "Personal") "Good Morning," else "Good Day, Dr. Madison",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeMode == "Personal") "Ready for your morning check-in?" else "Ready to review recent journals & chronicles?",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gift Surprise Box Claim
                        IconButton(
                            onClick = {
                                if (!giftSurpriseClaimed) {
                                    giftSurpriseClaimed = true
                                    streakClaimedCount += 1
                                    showGiftClaimsSuccess = true
                                    Toast.makeText(context, "🎁 Daily surprise claimed! You received 50 healing points!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "🎁 Already checked in and claimed today's gift summary!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .background(
                                    color = if (giftSurpriseClaimed) Color.LightGray.copy(alpha = 0.4f) else Color(0xFFFFD54F),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .size(40.dp)
                                .testTag("home_gift_claim")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CardGiftcard,
                                contentDescription = "Daily gift box claim",
                                tint = if (giftSurpriseClaimed) Color.DarkGray else Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Flame streak tracker badge
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFFFECB3), RoundedCornerShape(12.dp))
                                .clickable {
                                    Toast.makeText(context, "🔥 Solid $streakClaimedCount-day mindfulness streak! Keeping progress intact.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "Streak flame indicator",
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = streakClaimedCount.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFD84315)
                            )
                        }
                    }
                }
            }
        }

        // Clinician Caseload Dashboard Section (Primary for Practitioner Mode)
        if (activeMode != "Personal") {
            item {
                ClinicianCaseloadDashboard(
                    viewModel = viewModel,
                    patients = patients,
                    clinicalSessions = clinicalSessions,
                    scheduledItems = scheduledItems,
                    maskClientNames = settings?.maskClientNames ?: false,
                    onNavigateToPatientDetail = { patientId ->
                        onNavigate("patient_detail/$patientId")
                    },
                    onLogSession = { patientId ->
                        onNavigate("log_session/$patientId")
                    }
                )
            }
        }

        // Schedules & Push Notifications Section
        item {
            SchedulesAndNotificationsSection(
                viewModel = viewModel,
                scheduledItems = scheduledItems,
                syncedEmail = settings?.syncedUserEmail
            )
        }

        // 2. WEEKLY MOOD CALENDAR STRIP (With Dynamic check-in faces and animated custom cartoon googly eyes)
        item {
            Column {
                Text(
                    text = "Weekly Wellness Sync",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Create last 7 days ending with Fri June 19, 2026.
                    val calendarList = listOf(
                        ("Mon" to 15),
                        ("Tue" to 16),
                        ("Wed" to 17),
                        ("Thu" to 18),
                        ("Fri" to 19),
                        ("Sat" to 20),
                        ("Sun" to 21)
                    )

                    calendarList.forEachIndexed { idx, (dayName, dateNum) ->
                        // Match check-in entries for June dateNum
                        val matchingEntry = personalEntries.find { entry ->
                            val ec = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
                            ec.get(Calendar.YEAR) == 2026 &&
                                    ec.get(Calendar.MONTH) == Calendar.JUNE &&
                                    ec.get(Calendar.DAY_OF_MONTH) == dateNum
                        }

                        val isToday = dateNum == 19
                        val isSelected = selectedGraphIndex == idx
                        val hasEntry = matchingEntry != null

                        val dominantMood = matchingEntry?.mood ?: "Dotted"
                        val moodColor = when (dominantMood) {
                            "Happy" -> HappyAura
                            "Productive" -> ProductiveAura
                            "Calm" -> CalmAura
                            "Reflective" -> ReflectiveAura
                            "Neutral" -> NeutralAura
                            "Anxious" -> AnxiousAura
                            "Overwhelmed" -> OverwhelmedAura
                            else -> Color.LightGray.copy(alpha = 0.15f)
                        }

                        Card(
                            modifier = Modifier
                                .width(68.dp)
                                .height(96.dp)
                                .border(
                                    width = if (isSelected) 2.4.dp else if (isToday) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    selectedGraphIndex = idx
                                    if (hasEntry) {
                                        activeReflectDetailsModal = matchingEntry
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "No chronicle entry logged for $dayName June $dateNum. Click 'Reflect' below to journal now!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasEntry) moodColor else Color.LightGray.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dayName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasEntry) Color.White.copy(alpha = 0.85f) else Color.Gray
                                )

                                // GORGEOUS CUTE ANIMATED EYE DESIGN CHAT CANVAS
                                Canvas(modifier = Modifier.size(32.dp)) {
                                    val sizePx = size.width
                                    val centerX = sizePx / 2f
                                    val centerY = sizePx / 2f

                                    if (hasEntry) {
                                        // Dynamic cute eye drawings based on reported moods!
                                        when (matchingEntry?.mood) {
                                            "Happy", "Productive" -> {
                                                // Wide shiny open circular googly eyes
                                                drawCircle(color = Color.White, radius = 9f, center = Offset(centerX - 10f, centerY))
                                                drawCircle(color = Color.White, radius = 9f, center = Offset(centerX + 10f, centerY))
                                                // Cute black pupils looking forward with bright reflections
                                                drawCircle(color = Color.Black, radius = 4f, center = Offset(centerX - 9f, centerY - 1f))
                                                drawCircle(color = Color.Black, radius = 4f, center = Offset(centerX + 11f, centerY - 1f))
                                                drawCircle(color = Color.White, radius = 1.5f, center = Offset(centerX - 10f, centerY - 2f))
                                                drawCircle(color = Color.White, radius = 1.5f, center = Offset(centerX + 10f, centerY - 2f))
                                                // Blushing pink cheeks
                                                drawCircle(color = Color.Red.copy(alpha = 0.3f), radius = 3.5f, center = Offset(centerX - 15f, centerY + 6f))
                                                drawCircle(color = Color.Red.copy(alpha = 0.3f), radius = 3.5f, center = Offset(centerX + 15f, centerY + 6f))
                                            }
                                            "Calm" -> {
                                                // Content happy arc sleepy lines
                                                val arcPathL = Path().apply {
                                                    addArc(
                                                        oval = Rect(centerX - 17f, centerY - 6f, centerX - 3f, centerY + 6f),
                                                        startAngleDegrees = 180f,
                                                        sweepAngleDegrees = 180f
                                                    )
                                                }
                                                val arcPathR = Path().apply {
                                                    addArc(
                                                        oval = Rect(centerX + 3f, centerY - 6f, centerX + 17f, centerY + 6f),
                                                        startAngleDegrees = 180f,
                                                        sweepAngleDegrees = 180f
                                                    )
                                                }
                                                drawPath(path = arcPathL, color = Color.White, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                                                drawPath(path = arcPathR, color = Color.White, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                                            }
                                            "Anxious" -> {
                                                // Squinting googly eyes looking at each other
                                                drawCircle(color = Color.White, radius = 7.5f, center = Offset(centerX - 9f, centerY))
                                                drawCircle(color = Color.White, radius = 7.5f, center = Offset(centerX + 9f, centerY))
                                                drawCircle(color = Color.Black, radius = 3f, center = Offset(centerX - 7f, centerY))
                                                drawCircle(color = Color.Black, radius = 3f, center = Offset(centerX + 7f, centerY))
                                            }
                                            "Overwhelmed" -> {
                                                // Spiral eyes (crossed or spiral swirls)
                                                drawCircle(color = Color.White, radius = 8f, center = Offset(centerX - 9f, centerY))
                                                drawCircle(color = Color.White, radius = 8f, center = Offset(centerX + 9f, centerY))
                                                // Crossed 'X' drawing helper
                                                drawLine(color = Color.Red, start = Offset(centerX - 12f, centerY - 3f), end = Offset(centerX - 6f, centerY + 3f), strokeWidth = 2.5f)
                                                drawLine(color = Color.Red, start = Offset(centerX - 6f, centerY - 3f), end = Offset(centerX - 12f, centerY + 3f), strokeWidth = 2.5f)
                                                drawLine(color = Color.Red, start = Offset(centerX + 6f, centerY - 3f), end = Offset(centerX + 12f, centerY + 3f), strokeWidth = 2.5f)
                                                drawLine(color = Color.Red, start = Offset(centerX + 12f, centerY - 3f), end = Offset(centerX + 6f, centerY + 3f), strokeWidth = 2.5f)
                                            }
                                            else -> {
                                                // Neutral standard horizontal lines
                                                drawLine(color = Color.White, start = Offset(centerX - 14f, centerY), end = Offset(centerX - 4f, centerY), strokeWidth = 3f, cap = StrokeCap.Round)
                                                drawLine(color = Color.White, start = Offset(centerX + 4f, centerY), end = Offset(centerX + 14f, centerY), strokeWidth = 3f, cap = StrokeCap.Round)
                                            }
                                        }
                                    } else {
                                        // Sleeping dotted lines for un-checked-in elements
                                        val sleepPath = Path().apply {
                                            addArc(
                                                oval = Rect(centerX - 14f, centerY - 2f, centerX - 2f, centerY + 6f),
                                                startAngleDegrees = 0f,
                                                sweepAngleDegrees = 180f
                                            )
                                            addArc(
                                                oval = Rect(centerX + 2f, centerY - 2f, centerX + 14f, centerY + 6f),
                                                startAngleDegrees = 0f,
                                                sweepAngleDegrees = 180f
                                            )
                                        }
                                        drawPath(path = sleepPath, color = Color.Gray.copy(alpha = 0.5f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                                    }
                                }

                                Text(
                                    text = dateNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasEntry) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2B. RECHARTS-BASED MOOD & CLINICAL TREND ALIGNMENT TRAJECTORY GRAPH
        item {
            RechartsTrendLineGraph(
                personalEntries = personalEntries,
                selectedGraphIndex = selectedGraphIndex,
                onIndexSelected = { selectedGraphIndex = it }
            )
        }

        // 3. SPECIAL PRACTITIONER: UPCOMING SESSION ALERTS
        if (activeMode == "Practitioner") {
            item {
                if (nextUpcomingPatient != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Direct clinical flow click-through
                                viewModel.selectPatient(nextUpcomingPatient.id)
                                onNavigate("Primary") // Go to caseload (it will load the details!)
                                Toast.makeText(context, "Opening direct file panel for client ${nextUpcomingPatient.name}", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("practitioner_alert_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE9E7)),
                        border = BorderStroke(1.5.dp, Color(0xFFFFCCBC)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = "Active alert symbol",
                                    tint = Color(0xFFD84315),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "UPCOMING CLINICAL SESSION ALERT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD84315)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Next Client: ${nextUpcomingPatient.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Diagnostic focus: ${nextUpcomingPatient.diagnosis}\nPhase status: ${nextUpcomingPatient.therapeuticPhase}",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "AI Prep Brief compilation ready • CBT homework tracker enabled",
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Open File ➔",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD84315)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("Primary") }
                            .testTag("practitioner_alert_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = "Add client",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Clinical Caseload Empty",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Tap to view your caseload manager and register your first client.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. ACTION INTERACTION LAUNCHERS & CUSTOM CONSTRUCTOR BENCH
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Constructor Controller Bench
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .shadow(4.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)), // Warm light-green organic sand aesthetic
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFC5E1A5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Diary Settings & Constructor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF33691E)
                                )
                            }
                            Button(
                                onClick = { showConstructorDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("🔧 Customize", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Russian: Конструктор дневника. Tailor active modules, custom tracking metrics, and prioritized features directly on your home feed.",
                            fontSize = 10.5.sp,
                            color = Color(0xFF558B2F),
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Reflections & Explorations Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                if (activeMode == "Personal") {
                    // Rant & Reflect Launchers rendered side-by-side conditionally
                    if (isRantEnabled || isReflectEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isRantEnabled) {
                                // "Rant" Card (Blue background card, Chaos doodle Canvas on the right)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(130.dp)
                                        .clickable {
                                            activeRantSession = true
                                        }
                                        .testTag("rant_launcher_card"),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5)),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.6f)
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Rant",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Say whatever's on your mind. Raw vent storm tool.",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f),
                                                lineHeight = 14.sp
                                            )
                                        }

                                        // Custom Vector Chaos doodle rendering on Canvas right side!
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.48f)
                                                .align(Alignment.CenterEnd)
                                        ) {
                                            val w = size.width
                                            val h = size.height

                                            // 1. Speach bubble at top left of canvas
                                            drawRoundRect(
                                                color = Color.White,
                                                topLeft = Offset(w * 0.1f, h * 0.15f),
                                                size = Size(w * 0.45f, h * 0.25f),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                                            )
                                            // Speach bubble tail
                                            val tail = Path().apply {
                                                moveTo(w * 0.2f, h * 0.4f)
                                                lineTo(w * 0.15f, h * 0.48f)
                                                lineTo(w * 0.3f, h * 0.4f)
                                                close()
                                            }
                                            drawPath(tail, Color.White)

                                            // Bubble dots
                                            drawCircle(color = Color(0xFFFFD54F), radius = 3.5f, center = Offset(w * 0.2f, h * 0.27f))
                                            drawCircle(color = Color(0xFFFFD54F), radius = 3.5f, center = Offset(w * 0.322f, h * 0.27f))
                                            drawCircle(color = Color(0xFFFFD54F), radius = 3.5f, center = Offset(w * 0.45f, h * 0.27f))

                                            // 2. Tangle/Scribble doodle lines
                                            val scribblePath = Path()
                                            val centerX = w * 0.62f
                                            val centerY = h * 0.58f
                                            val radius = w * 0.25f

                                            for (i in 0..45) {
                                                val angle = (i * 1.5).toFloat()
                                                val offsetRadius = radius * (0.4f + 0.5f * kotlin.math.sin(i * 3.7f))
                                                val px = centerX + offsetRadius * kotlin.math.cos(angle)
                                                val py = centerY + offsetRadius * kotlin.math.sin(angle)
                                                if (i == 0) {
                                                    scribblePath.moveTo(px, py)
                                                } else {
                                                    scribblePath.lineTo(px, py)
                                                }
                                            }
                                            drawPath(scribblePath, Color.Black, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

                                            // 3. Cool orange/amber crisp explosion star at bottom right
                                            val sx = centerX + 20f
                                            val sy = centerY + 24f
                                            val starPath = Path()
                                            val points = 8
                                            val outerRadius = 24f
                                            val innerRadius = 10f
                                            for (i in 0 until points * 2) {
                                                val r = if (i % 2 == 0) outerRadius else innerRadius
                                                val ang = (i * Math.PI / points).toFloat()
                                                val px = sx + r * kotlin.math.cos(ang)
                                                val py = sy + r * kotlin.math.sin(ang)
                                                if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
                                            }
                                            starPath.close()
                                            drawPath(starPath, Color(0xFFFF6D00))
                                        }
                                    }
                                }
                            }

                            if (isReflectEnabled) {
                                // "Reflect" Card (Green background card, dialogue balloon live on Canvas)
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(130.dp)
                                        .clickable {
                                            activeReflectSession = true
                                        }
                                        .testTag("reflect_launcher_card"),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.6f)
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Reflect",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Start guided morning prompts and goals.",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.85f),
                                                lineHeight = 14.sp
                                            )
                                        }

                                        // Custom dialogue speech balloon with heart on Canvas right side!
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(0.48f)
                                                .align(Alignment.CenterEnd)
                                        ) {
                                            val w = size.width
                                            val h = size.height

                                            // Bubble L (Purple concentric circles bubble)
                                            val lcx = w * 0.35f
                                            val lcy = h * 0.45f
                                            drawCircle(color = Color(0xFFB39DDB), radius = w * 0.22f, center = Offset(lcx, lcy))

                                            // Little tail L
                                            val tailL = Path().apply {
                                                moveTo(lcx - 10f, lcy + w * 0.18f)
                                                lineTo(lcx - 24f, lcy + w * 0.26f)
                                                lineTo(lcx - 2f, lcy + w * 0.2f)
                                                close()
                                            }
                                            drawPath(path = tailL, color = Color(0xFFB39DDB))

                                            // Concentric circles inside Bubble L
                                            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = w * 0.15f, center = Offset(lcx, lcy), style = Stroke(width = 2f))
                                            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = w * 0.08f, center = Offset(lcx, lcy), style = Stroke(width = 2.5f))

                                            // Bubble R (Mint green speech bubble with heart)
                                            val rcx = w * 0.68f
                                            val rcy = h * 0.65f
                                            drawCircle(color = Color(0xFFA5D6A7), radius = w * 0.22f, center = Offset(rcx, rcy))

                                            // Little tail R
                                            val tailR = Path().apply {
                                                moveTo(rcx + 10f, rcy + w * 0.18f)
                                                lineTo(rcx + 22f, rcy + w * 0.26f)
                                                lineTo(rcx + 2f, rcy + w * 0.2f)
                                                close()
                                            }
                                            drawPath(path = tailR, color = Color(0xFFA5D6A7))

                                            // Draw tiny custom green heart inside right bubble
                                            val heartPath = Path().apply {
                                                val hSize = 14f
                                                val rx = rcx
                                                val ry = rcy - 4f
                                                moveTo(rx, ry + hSize / 4)
                                                cubicTo(rx - hSize / 2, ry - hSize / 2, rx - hSize, ry + hSize / 4, rx, ry + hSize)
                                                cubicTo(rx + hSize, ry + hSize / 4, rx + hSize / 2, ry - hSize / 2, rx, ry + hSize / 4)
                                            }
                                            drawPath(heartPath, Color(0xFF1B5E20))

                                            // Draw cute pink curly swirls
                                            val swirl = Path().apply {
                                                moveTo(w * 0.2f, h * 0.85f)
                                                cubicTo(w * 0.3f, h * 0.75f, w * 0.35f, h * 0.95f, w * 0.45f, h * 0.88f)
                                            }
                                            drawPath(swirl, Color(0xFFF48FB1), style = Stroke(width = 3f, cap = StrokeCap.Round))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // A. EMOTIONS TAG GERMAN TRACKER WIDGET
                    if (isEmotionsEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2DDD0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "What do you feel today?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E332E)
                                )
                                Text(
                                    "Russian: Что вы чувствуете сегодня? Quick check-in emotions:",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                val emotionBubbles = listOf(
                                    "🍦 Calm", "🌪️ Anxiety", "☀️ Joy", "🌸 Happiness",
                                    "🌿 Gratitude", "✨ Inspiration", "🌧️ Sadness",
                                    "👻 Fear", "🥱 Apathy", "🔥 Anger", "🍿 Excitement"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    emotionBubbles.forEach { emotion ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                                                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    viewModel.addJournalEntry(
                                                        mood = emotion.substringAfter(" "),
                                                        oneSentenceNote = "Quick baseline sync: Feeling $emotion",
                                                        freeWriteText = "Registered immediate emotional aura check-in for $emotion on the constructor desk.",
                                                        sleepQuality = 7,
                                                        tags = "QuickBaseline, ${emotion.substringAfter(" ")}"
                                                    )
                                                    Toast.makeText(context, "Recorded $emotion baseline aura check-in securely!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(emotion, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // B. GRATITUDE BULLET LEDGER
                    if (isGratitudeEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)), // Notepad page cream
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFFFF59D))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Gratitude Notepad (Благодарность)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF3E2723)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                customGratitudes.forEach { item ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("✨", fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(item, fontSize = 12.sp, color = Color(0xFF5D4037))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                var userGratDraft by remember { mutableStateOf("") }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = userGratDraft,
                                        onValueChange = { userGratDraft = it },
                                        placeholder = { Text("Write something...", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (userGratDraft.isNotBlank()) {
                                                customGratitudes.add(userGratDraft)
                                                viewModel.addJournalEntry(
                                                    mood = "Calm",
                                                    oneSentenceNote = "Gratitude: $userGratDraft",
                                                    freeWriteText = "Saved gratitude point: $userGratDraft",
                                                    sleepQuality = 8,
                                                    tags = "Gratitude"
                                                )
                                                userGratDraft = ""
                                                Toast.makeText(context, "Saved gratitude point to database!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4E157)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("+ Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // C. GOOD DEEDS REGISTRY WIDGET
                    if (isGoodDeedsEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)), // Warm rose tint
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFF8BBD0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFD81B60), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Good Deeds Registry (Добрые дела)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF880E4F)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                customGoodDeeds.forEach { (deed, checked) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                customGoodDeeds[deed] = !checked
                                                if (customGoodDeeds[deed] == true) {
                                                    Toast.makeText(context, "🎀 Awesome! One good deed checked! Warm hearts.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (checked) Color(0xFFD81B60) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = deed,
                                            fontSize = 12.sp,
                                            color = if (checked) Color.Gray else Color.Black,
                                            style = androidx.compose.ui.text.TextStyle(
                                                textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // D. DAILY TASKS & SLEEPY KITTYcompanion
                    if (isTasksEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(3.dp, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF7F0)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFE5DDD0))
                        ) {
                            val activePersonalTasks = remember { mutableStateListOf(
                                "Read 10 pages of a book" to false,
                                "Organize your workspace" to false,
                                "Complete morning tea breathing" to false
                            ) }
                            val allDone = activePersonalTasks.all { it.second }

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Daynest To-Do Tasks Checklist",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                activePersonalTasks.forEachIndexed { i, (task, done) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activePersonalTasks[i] = task to !done
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (done) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(task, fontSize = 12.sp, color = if (done) Color.Gray else Color.Black)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // SLEEPY CUTE WAVE-CAT MASCOTdrawn live inside Compose canvas!
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF1EDE0), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Canvas(modifier = Modifier.size(54.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val r = size.minDimension / 2.5f
                                        
                                        // Face
                                        drawCircle(Color.White, radius = r, center = Offset(w*0.5f, h*0.65f))
                                        drawCircle(Color.Black, radius = r, style = Stroke(width = 2f), center = Offset(w*0.5f, h*0.65f))
                                        
                                        // Left Ear
                                        val leftEar = Path().apply {
                                            moveTo(w*0.53f - r, h*0.65f - r*0.7f)
                                            lineTo(w*0.55f - r*1.3f, h*0.65f - r*1.3f)
                                            lineTo(w*0.5f - r*0.5f, h*0.65f - r)
                                            close()
                                        }
                                        drawPath(leftEar, Color.White)
                                        drawPath(leftEar, Color.Black, style = Stroke(width = 2f))
                                        
                                        // Right Ear
                                        val rightEar = Path().apply {
                                            moveTo(w*0.47f + r, h*0.65f - r*0.7f)
                                            lineTo(w*0.45f + r*1.3f, h*0.65f - r*1.3f)
                                            lineTo(w*0.50f + r*0.5f, h*0.65f - r)
                                            close()
                                        }
                                        drawPath(rightEar, Color.White)
                                        drawPath(rightEar, Color.Black, style = Stroke(width = 2f))
                                        
                                        // Sleepy closed eye curves
                                        val eyeL = Path().apply {
                                            addArc(
                                                oval = Rect(w*0.5f - r*0.6f, h*0.6f, w*0.5f - r*0.2f, h*0.68f),
                                                startAngleDegrees = 0f,
                                                sweepAngleDegrees = 180f
                                            )
                                        }
                                        val eyeR = Path().apply {
                                            addArc(
                                                oval = Rect(w*0.5f + r*0.2f, h*0.6f, w*0.5f + r*0.6f, h*0.68f),
                                                startAngleDegrees = 0f,
                                                sweepAngleDegrees = 180f
                                            )
                                        }
                                        drawPath(eyeL, Color.Black, style = Stroke(width = 2f, cap = StrokeCap.Round))
                                        drawPath(eyeR, Color.Black, style = Stroke(width = 2f, cap = StrokeCap.Round))

                                        // Tiny nose
                                        drawCircle(Color(0xFFF48FB1), radius = 3f, center = Offset(w*0.5f, h*0.68f))

                                        // Waving paw! If done, the paw is lifted high
                                        if (allDone) {
                                            drawCircle(Color.White, radius = 9f, center = Offset(w*0.8f, h*0.35f))
                                            drawCircle(Color.Black, radius = 9f, center = Offset(w*0.8f, h*0.35f), style = Stroke(width = 2.5f))
                                            // Red tiny heart symbol
                                            val heart = Path().apply {
                                                val hx = w * 0.2f
                                                val hy = h * 0.3f
                                                moveTo(hx, hy)
                                                cubicTo(hx-6f, hy-6f, hx-12f, hy, hx, hy + 9f)
                                                cubicTo(hx+12f, hy, hx+6f, hy-6f, hx, hy)
                                            }
                                            drawPath(heart, Color(0xFFE57373))
                                        } else {
                                            drawCircle(Color.White, radius = 8f, center = Offset(w*0.24f, h*0.85f))
                                            drawCircle(Color.Black, radius = 8f, center = Offset(w*0.24f, h*0.85f), style = Stroke(width = 2f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (allDone) "Meow! Waving hello. You finished all your prioritized tasks today! ❤️" else "Miau... Check off all tasks above to wake up the Sleepy Kitty mascot!",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (allDone) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Practitioner Action Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // "Caseload Map" (Deep Blue Card)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clickable {
                                    onNavigate("Primary")
                                    Toast.makeText(context, "Loading patient caseload list diagnostic graphs...", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("caseload_map_card"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF283593)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.6f)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Caseload Files",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )

                                    Text(
                                        text = "Manage diagnostics and AI pre-session outlines.",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        lineHeight = 13.sp
                                    )
                                }

                                // Interactive nodes/graph Canvas drawings
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.48f)
                                        .align(Alignment.CenterEnd)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val n1 = Offset(w * 0.3f, h * 0.3f)
                                    val n2 = Offset(w * 0.7f, h * 0.45f)
                                    val n3 = Offset(w * 0.4f, h * 0.75f)

                                    // Draw connection lines
                                    drawLine(Color.White.copy(alpha = 0.5f), n1, n2, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                                    drawLine(Color.White.copy(alpha = 0.5f), n2, n3, strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                                    drawLine(Color.White.copy(alpha = 0.5f), n1, n3, strokeWidth = 2f)

                                    // Draw node circles
                                    drawCircle(Color(0xFF80DEEA), radius = 10f, center = n1)
                                    drawCircle(Color(0xFFFFCC80), radius = 13f, center = n2)
                                    drawCircle(Color(0xFFCE93D8), radius = 8f, center = n3)
                                }
                            }
                        }

                        // "Clinical Chronicles" (Emerald Card)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clickable {
                                    onNavigate("Secondary")
                                    Toast.makeText(context, "Opening counselor daily clinical presences calendar...", Toast.LENGTH_SHORT).show()
                                }
                                .testTag("clinical_chronicles_card"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00695C)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.6f)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Presences Chronology",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Monitor daily schedule presence color-coded auras.",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        lineHeight = 13.sp
                                    )
                                }

                                // Interactive mini calendar drawing
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.48f)
                                        .align(Alignment.CenterEnd)
                                ) {
                                    val w = size.width
                                    val h = size.height

                                    // Outline calendar card
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.15f),
                                        topLeft = Offset(w * 0.15f, h * 0.15f),
                                        size = Size(w * 0.7f, h * 0.7f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                                    )

                                    // Top bar
                                    drawRoundRect(
                                        color = Color(0xFF26A69A),
                                        topLeft = Offset(w * 0.15f, h * 0.15f),
                                        size = Size(w * 0.7f, h * 0.22f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                                    )

                                    // Small grid spots
                                    val startX = w * 0.25f
                                    val startY = h * 0.48f
                                    val stepX = w * 0.18f
                                    val stepY = h * 0.16f

                                    for (row in 0..1) {
                                        for (col in 0..2) {
                                            val cx = startX + col * stepX
                                            val cy = startY + row * stepY
                                            val dotColor = when ((row + col) % 3) {
                                                0 -> Color(0xFFFF8A65)
                                                1 -> Color(0xFFA5D6A7)
                                                else -> Color.White.copy(alpha = 0.3f)
                                            }
                                            drawCircle(dotColor, radius = 6.5f, center = Offset(cx, cy))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. CARD SUMMARIES FROM LAST SESSIONS
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_last_session_summaries_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (activeMode == "Personal") "Last Reflection Summary" else "Recent Consultation Chronicle Summaries",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (activeMode == "Personal") {
                        val lastEntry = personalEntries.sortedByDescending { it.dateMillis }.firstOrNull()
                        if (lastEntry != null) {
                            val sdf = SimpleDateFormat("EE, MMM dd hh:mm a", Locale.getDefault())
                            val dateString = sdf.format(Date(lastEntry.dateMillis))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            when (lastEntry.mood) {
                                                "Happy" -> HappyAura
                                                "Productive" -> ProductiveAura
                                                "Calm" -> CalmAura
                                                "Reflective" -> ReflectiveAura
                                                "Anxious" -> AnxiousAura
                                                "Overwhelmed" -> OverwhelmedAura
                                                else -> NeutralAura
                                            }, CircleShape
                                        )
                                )
                                Text(
                                    text = "Logged $dateString (Mood: ${lastEntry.mood})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "“${lastEntry.oneSentenceNote.ifEmpty { lastEntry.freeWriteText.take(120) }}”",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "No reflection logs filed in database yet. Journal today to kickstart local metrics!",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        // Practitioner last session records
                        val lastSession = clinicalSessions.sortedByDescending { it.dateMillis }.firstOrNull()
                        if (lastSession != null) {
                            val patientName = patients.find { it.id == lastSession.patientId }?.name ?: lastSession.patientId
                            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            val sessionDate = sdf.format(Date(lastSession.dateMillis))

                            Text(
                                text = "Session with $patientName on $sessionDate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Observations: \"${lastSession.objectiveObservations}\"",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CBT Progress Notes: \"${lastSession.notes}\"",
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                color = Color.DarkGray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "No consult chronicles documented yet. Use 'Log Consultation' inside client tabs to append archives.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // 6. TODO SCHEDULES (Interactive click-to-toggle checklist!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_todo_schedule_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Mindful Agenda Tasks",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Complete interactive daily habits to keep local wellness index in sync.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val todoItems = if (activeMode == "Personal") {
                        listOf(
                            "Morning Mindfulness Deep Breathing Loop",
                            "Log Mood Reflection Entry",
                            "Submit Weekly Sleep Metrics (1-10 rating)",
                            "Verify CBT Cognitive Restructuring Worksheet"
                        )
                    } else {
                        listOf(
                            "Review AI-generated clinical pre-session brief outlines",
                            "Compile practitioner weekly presence slots",
                            "Archive June billing consulting logs",
                            "Update CBT homework submission checklists"
                        )
                    }

                    todoItems.forEach { todo ->
                        val isChecked = if (activeMode == "Personal") {
                            // If personal check "Log Mood Reflection Entry" automatically if they check-in today (June 19)
                            val hasTodayCheckIn = personalEntries.any { e ->
                                val ec = Calendar.getInstance().apply { timeInMillis = e.dateMillis }
                                ec.get(Calendar.YEAR) == 2026 &&
                                        ec.get(Calendar.MONTH) == Calendar.JUNE &&
                                        ec.get(Calendar.DAY_OF_MONTH) == 19
                            }
                            if (todo == "Log Mood Reflection Entry" && hasTodayCheckIn) {
                                true
                            } else {
                                checkedPersonalTodos[todo] ?: false
                            }
                        } else {
                            checkedPractitionerTodos[todo] ?: false
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (todo == "Log Mood Reflection Entry" && isChecked) {
                                        Toast.makeText(context, "Log Mood is completed automatically when you write in your journal feed!", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    if (activeMode == "Personal") {
                                        checkedPersonalTodos[todo] = !isChecked
                                        val status = if (!isChecked) "Completed" else "Incomplete"
                                        Toast.makeText(context, "$todo set to $status!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        checkedPractitionerTodos[todo] = !isChecked
                                        val status = if (!isChecked) "Completed" else "Incomplete"
                                        Toast.makeText(context, "$todo set to $status!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = "Todo completion checkbox",
                                tint = if (isChecked) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = todo,
                                fontSize = 12.sp,
                                color = if (isChecked) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                fontWeight = if (isChecked) FontWeight.Light else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal view for wellness strip check-ins drilldown
    val detailsEntry = activeReflectDetailsModal
    if (detailsEntry != null) {
        JournalEntryDetailDialog(
            entry = detailsEntry,
            viewModel = viewModel,
            onDismiss = { activeReflectDetailsModal = null }
        )
    }

    // 1. CONSTRUCTOR DIALOG
    if (showConstructorDialog) {
        Dialog(onDismissRequest = { showConstructorDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9FB)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("constructor_configuration_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔧 Constructor Activity Bench",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Select which modular activities you wish to prioritize and keep enabled on your primary feed dashboard. Custom-built and saved instantly.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row switches
                    val toggles = listOf(
                        Triple("🌪️ Rant Launcher Card", isRantEnabled, { b: Boolean -> isRantEnabled = b }),
                        Triple("🌸 Reflect Launcher Card", isReflectEnabled, { b: Boolean -> isReflectEnabled = b }),
                        Triple("🍦 Emotions Tag Tracker", isEmotionsEnabled, { b: Boolean -> isEmotionsEnabled = b }),
                        Triple("🌿 Gratitude Notepad", isGratitudeEnabled, { b: Boolean -> isGratitudeEnabled = b }),
                        Triple("🎀 Good Deeds registry", isGoodDeedsEnabled, { b: Boolean -> isGoodDeedsEnabled = b }),
                        Triple("🐱 Daily Tasks Companion", isTasksEnabled, { b: Boolean -> isTasksEnabled = b })
                    )

                    toggles.forEach { (name, state, onToggle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(!state) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = state,
                                onCheckedChange = { onToggle(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = SageGreen)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showConstructorDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Personalized Config", color = Color.White)
                    }
                }
            }
        }
    }

    // 2. IMMERSIVE RANT SESSION OVERLAY
    if (activeRantSession) {
        var rantDraft by remember { mutableStateOf("") }
        var stormLevel by remember { mutableStateOf("Anarchy Storm") }
        var isBurningState by remember { mutableStateOf(false) }
        var burningProgress by remember { mutableStateOf(0f) }

        // Animate burning effect if burning
        LaunchedEffect(isBurningState) {
            if (isBurningState) {
                while (burningProgress < 1f) {
                    delay(30)
                    burningProgress += 0.05f
                }
                // Save and reset
                viewModel.addJournalEntry(
                    mood = "Cathartic Vent",
                    oneSentenceNote = "Immersive Vent Storm: Burning off stress.",
                    freeWriteText = "[BURNT STRESS LEDGER] " + rantDraft,
                    sleepQuality = 5,
                    tags = "Rant, Catharsis, $stormLevel"
                )
                Toast.makeText(context, "🎆 Clean air in. Burnt stress dissolved into the clouds!", Toast.LENGTH_LONG).show()
                activeRantSession = false
                isBurningState = false
                burningProgress = 0f
                rantDraft = ""
            }
        }

        Dialog(onDismissRequest = { if (!isBurningState) activeRantSession = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)), // Dark charcoal sky
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("immersive_rant_overlay")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌪️ Storm Rant Cabinet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFFE57373)
                        )
                        if (!isBurningState) {
                            IconButton(onClick = { activeRantSession = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.LightGray)
                            }
                        }
                    }

                    Text(
                        text = "Russian: Камера выплеска гнева. Write any worry down. No filters, no judgment. Press 'Burn & Dissolve' to destroy the stress forever.",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )

                    // Storm levels selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Mild", "Frustrated", "Furious", "Anarchy Storm").forEach { level ->
                            val selected = stormLevel == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) Color(0xFFFF5252) else Color(0xFF2C2B30),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { stormLevel = level }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(level, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Large typing ledger
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF252429), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                    ) {
                        if (!isBurningState) {
                            OutlinedTextField(
                                value = rantDraft,
                                onValueChange = { rantDraft = it },
                                placeholder = { Text("Unleash the storm here... text burns in real-time.", color = Color.DarkGray, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxSize(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        } else {
                            // Burning animation text!
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(1f - burningProgress)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                LinearProgressIndicator(
                                    progress = burningProgress,
                                    color = Color(0xFFFF7043),
                                    trackColor = Color(0xFF3E2723),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("🔥 DISPERSING IN RAGING SPARKS...", fontSize = 11.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    // REAL-TIME BURNING doodle reactive Canvas!
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)) {
                        val w = size.width
                        val h = size.height

                        // Draw recursive scribble loop that scales in complexity based on word or character length!
                        val scribblePath = Path()
                        val cx = w / 2f
                        val cy = h / 2f
                        val scaleFactor = rantDraft.length.coerceAtMost(250)
                        val radius = h * 0.45f

                        if (scaleFactor > 0 && !isBurningState) {
                            for (i in 0 until (scaleFactor / 2 + 5)) {
                                val angle = i * 0.4f
                                val r = radius * (0.3f + 0.6f * kotlin.math.sin(i * 1.7f))
                                val px = cx + r * kotlin.math.cos(angle)
                                val py = cy + r * kotlin.math.sin(angle)
                                if (i == 0) scribblePath.moveTo(px, py) else scribblePath.lineTo(px, py)
                            }
                            drawPath(
                                scribblePath,
                                color = if (stormLevel == "Anarchy Storm") Color(0xFFFF1744) else Color(0xFFFFB74D),
                                style = Stroke(width = 3f + (scaleFactor * 0.05f), cap = StrokeCap.Round)
                            )
                        } else if (isBurningState) {
                            // Draw burning embers flying up!
                            for (i in 0..12) {
                                val dotX = cx + (i * 30f - 180f) + (burningProgress * 15f)
                                val dotY = cy + (kotlin.math.sin(i + burningProgress * 10f) * 20f) - (burningProgress * h)
                                drawCircle(
                                    color = Color(0xFFE64A19).copy(alpha = 1f - burningProgress),
                                    radius = 4f + (i % 3),
                                    center = Offset(dotX, dotY)
                                )
                            }
                        } else {
                            // Empty calming silent cloud outline
                            drawRoundRect(
                                color = Color.Gray.copy(alpha = 0.3f),
                                topLeft = Offset(w * 0.3f, h * 0.3f),
                                size = Size(w * 0.4f, h * 0.4f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                                style = Stroke(width = 2f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { if (!isBurningState) activeRantSession = false },
                            enabled = !isBurningState,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF302E35)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                        
                        Button(
                            onClick = { isBurningState = true },
                            enabled = rantDraft.isNotBlank() && !isBurningState,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔥 Dissolve Stress", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 3. MORNING GUIDED REFLECT SESSION OVERLAY
    if (activeReflectSession) {
        var reflectDraft1 by remember { mutableStateOf("") }
        var reflectDraft2 by remember { mutableStateOf("") }
        var reflectDraft3 by remember { mutableStateOf("") }
        var reflectionStep by remember { mutableStateOf(1) } // 1 = Gratitude, 2 = Yesterday, 3 = Intention

        Dialog(onDismissRequest = { activeReflectSession = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF8)), // Fine cream parchment paper look
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("morning_guided_reflect_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📓 Step $reflectionStep of 3: Reflect Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { activeReflectSession = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    // Custom horizontal parchment ledger style progress indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color(0xFFEDE8D5), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(reflectionStep / 3f)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                        )
                    }

                    Divider()

                    when (reflectionStep) {
                        1 -> {
                            Text(
                                "🌿 Step 1: Cultivating Safety & Comfort",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                            Text(
                                "List 3 small entities, soft sounds, or warm tastes that brought comforting protection to you recently:",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = reflectDraft1,
                                onValueChange = { reflectDraft1 = it },
                                placeholder = { Text("e.g., Warm hazelnut tea, birds chirping on the roof, soft knit socks...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        2 -> {
                            Text(
                                "🪐 Step 2: Compassionate Assessment",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                            Text(
                                "Reflecting on yesterday: What is one self-compassionate lesson or gentle truth you want to carry forward today?",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = reflectDraft2,
                                onValueChange = { reflectDraft2 = it },
                                placeholder = { Text("e.g., I did my absolute best yesterday and that is more than enough...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        3 -> {
                            Text(
                                "👑 Step 3: Minimalist Intentions setting",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                            Text(
                                "What single, concrete, realistic intention or gentle micro-assignment will govern your peaceful energy today?",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = reflectDraft3,
                                onValueChange = { reflectDraft3 = it },
                                placeholder = { Text("e.g., Walk outside for five minutes, call my partner, take deep belly breaths...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (reflectionStep > 1) {
                            Button(
                                onClick = { reflectionStep -= 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECEFF1)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back", color = Color.Black)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (reflectionStep < 3) {
                                    reflectionStep += 1
                                } else {
                                    // Save full summary
                                    val fullLog = StringBuilder().apply {
                                        appendLine("Reflections Diary Event:")
                                        if (reflectDraft1.isNotBlank()) appendLine("- Comfort list: $reflectDraft1")
                                        if (reflectDraft2.isNotBlank()) appendLine("- Comfort lessons: $reflectDraft2")
                                        if (reflectDraft3.isNotBlank()) appendLine("- Future Micro-assignments: $reflectDraft3")
                                    }.toString()

                                    viewModel.addJournalEntry(
                                        mood = "Reflective",
                                        oneSentenceNote = "Completed full three-step Guided Reflection Ledger.",
                                        freeWriteText = fullLog,
                                        sleepQuality = 8,
                                        tags = "Reflection, Self-Love, Guided"
                                    )
                                    Toast.makeText(context, "📓 Warmly checked in! Ledger recorded to database.", Toast.LENGTH_LONG).show()
                                    activeReflectSession = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (reflectionStep == 3) "Save Ledger" else "Continue", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// RECHARTS TREND GRAPH COMPONENTS FOR QUANTIFIED-SELF ALIGNMENT
// ==========================================================================================

@Composable
fun RechartsTrendLineGraph(
    personalEntries: List<PersonalJournalEntry>,
    selectedGraphIndex: Int,
    onIndexSelected: (Int) -> Unit
) {
    var chartFilterMode by remember { mutableStateOf("Dual Alignment") } // "Dual Alignment", "Mood Curve", "Sleep Line"
    val context = LocalContext.current
    
    val calendarList = listOf(
        "Mon" to 15,
        "Tue" to 16,
        "Wed" to 17,
        "Thu" to 18,
        "Fri" to 19,
        "Sat" to 20,
        "Sun" to 21
    )

    val trendPoints = remember(personalEntries) {
        calendarList.map { (dayName, dateNum) ->
            val matchingEntry = personalEntries.find { entry ->
                val ec = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
                ec.get(Calendar.YEAR) == 2026 &&
                        ec.get(Calendar.MONTH) == Calendar.JUNE &&
                        ec.get(Calendar.DAY_OF_MONTH) == dateNum
            }
            
            // Fallback interpolated values for standard demo visual layout continuity
            val fallbackMood = when(dateNum) {
                15 -> 7.0f
                16 -> 5.0f
                17 -> 8.0f
                18 -> 4.0f
                19 -> 9.0f
                20 -> 8.0f
                else -> 7.0f
            }
            val fallbackMoodName = when(fallbackMood.toInt()) {
                10 -> "Happy"
                9 -> "Productive"
                8 -> "Calm"
                7 -> "Reflective"
                5 -> "Neutral"
                4 -> "Anxious"
                else -> "Overwhelmed"
            }
            
            val fallbackSleep = when(dateNum) {
                15 -> 6f
                16 -> 5f
                17 -> 7f
                18 -> 4f
                19 -> 8f
                20 -> 8f
                else -> 7f
            }

            TrendPoint(
                dayName = dayName,
                dateNum = dateNum,
                moodName = matchingEntry?.mood ?: fallbackMoodName,
                moodValue = matchingEntry?.moodWeight?.toFloat() ?: fallbackMood,
                sleepValue = matchingEntry?.sleepQuality?.toFloat() ?: fallbackSleep,
                entry = matchingEntry
            )
        }
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val orangeAccent = Color(0xFFFF9100) // Sleep track color
    val selectedPoint = trendPoints.getOrNull(selectedGraphIndex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recharts_trend_graph_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with filters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📈 Wellness Trajectory",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(themePrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Recharts Eng",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = themePrimary
                            )
                        }
                    }
                    Text(
                        text = "Synchronized fluctuations over the past week",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Analytics View Filter Toggles
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Dual", "Mood", "Sleep").forEach { option ->
                        val active = when (option) {
                            "Dual" -> chartFilterMode == "Dual Alignment"
                            "Mood" -> chartFilterMode == "Mood Curve"
                            else -> chartFilterMode == "Sleep Line"
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    chartFilterMode = when (option) {
                                        "Dual" -> "Dual Alignment"
                                        "Mood" -> "Mood Curve"
                                        else -> "Sleep Line"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = option,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) themePrimary else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legends Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chartFilterMode == "Dual Alignment" || chartFilterMode == "Mood Curve") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(themePrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mood Intensity (2-10)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (chartFilterMode == "Dual Alignment" || chartFilterMode == "Sleep Line") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(orangeAccent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sleep Quality (1-10)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The Graphic Canvas + Y-Axis wrapper
            Row(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                // A. Y-Axis Label Column
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("10 - Happy", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text("7.5 - Good", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text("5.0 - Neut", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text("2.0 - Vuln", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }

                // B. Chart Rendering surface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val paddingX = w * 0.05f
                        val chartW = w * 0.9f
                        val topY = h * 0.08f
                        val bottomY = h * 0.9f
                        val chartH = bottomY - topY

                        // 1. Draw 4 faint dashed grid lines
                        val dottedPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        for (i in 0..3) {
                            val lineY = topY + (chartH * (i / 3f))
                            drawLine(
                                color = themeOnSurface.copy(alpha = 0.08f),
                                start = Offset(paddingX, lineY),
                                end = Offset(paddingX + chartW, lineY),
                                strokeWidth = 1.5f,
                                pathEffect = dottedPathEffect
                            )
                        }

                        // 2. Map coordinates for line paths
                        val pointsCount = trendPoints.size
                        val xStep = chartW / (pointsCount - 1).coerceAtLeast(1)

                        val moodPoints = trendPoints.mapIndexed { index, point ->
                            val fraction = (point.moodValue - 2.0f) / 8.0f // range 2 to 10 is 8 values
                            val px = paddingX + index * xStep
                            val py = bottomY - (fraction * chartH)
                            Offset(px, py)
                        }

                        val sleepPoints = trendPoints.mapIndexed { index, point ->
                            val fraction = (point.sleepValue - 1.0f) / 9.0f // range 1 to 10 is 9 values
                            val px = paddingX + index * xStep
                            val py = bottomY - (fraction * chartH)
                            Offset(px, py)
                        }

                        // 3. Draw vertical crosshair guide over active touch
                        if (selectedGraphIndex in 0 until pointsCount) {
                            val lineX = paddingX + selectedGraphIndex * xStep
                            drawLine(
                                color = themePrimary.copy(alpha = 0.25f),
                                start = Offset(lineX, topY),
                                end = Offset(lineX, bottomY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                        }

                        // 4. Draw Mood Path and Area Gradient (if selected)
                        if (chartFilterMode == "Dual Alignment" || chartFilterMode == "Mood Curve") {
                            // Spline / bezier path
                            val path = Path()
                            if (moodPoints.isNotEmpty()) {
                                path.moveTo(moodPoints[0].x, moodPoints[0].y)
                                for (i in 1 until moodPoints.size) {
                                    val prev = moodPoints[i - 1]
                                    val curr = moodPoints[i]
                                    val cp1X = prev.x + (curr.x - prev.x) / 2f
                                    val cp1Y = prev.y
                                    val cp2X = prev.x + (curr.x - prev.x) / 2f
                                    val cp2Y = curr.y
                                    path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, curr.x, curr.y)
                                }
                            }

                            // Area fill gradient first
                            val fillPath = Path().apply {
                                addPath(path)
                                if (moodPoints.isNotEmpty()) {
                                    lineTo(moodPoints.last().x, bottomY)
                                    lineTo(moodPoints.first().x, bottomY)
                                    close()
                                }
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        themePrimary.copy(alpha = 0.22f),
                                        themePrimary.copy(alpha = 0.00f)
                                    ),
                                    startY = topY,
                                    endY = bottomY
                                )
                            )

                            // Main stroked bezier line
                            drawPath(
                                path = path,
                                color = themePrimary,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Circular node indicators
                            moodPoints.forEachIndexed { idx, pt ->
                                val isSelected = idx == selectedGraphIndex
                                drawCircle(
                                    color = if (isSelected) Color.White else themePrimary,
                                    radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = themePrimary,
                                    radius = if (isSelected) 3.dp.toPx() else 2.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        // 5. Draw Sleep Quality Path and Area Gradient (if selected)
                        if (chartFilterMode == "Dual Alignment" || chartFilterMode == "Sleep Line") {
                            val path = Path()
                            if (sleepPoints.isNotEmpty()) {
                                path.moveTo(sleepPoints[0].x, sleepPoints[0].y)
                                for (i in 1 until sleepPoints.size) {
                                    val prev = sleepPoints[i - 1]
                                    val curr = sleepPoints[i]
                                    val cp1X = prev.x + (curr.x - prev.x) / 2f
                                    val cp1Y = prev.y
                                    val cp2X = prev.x + (curr.x - prev.x) / 2f
                                    val cp2Y = curr.y
                                    path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, curr.x, curr.y)
                                }
                            }

                            // Secondary orange fill gradient
                            val fillPath = Path().apply {
                                addPath(path)
                                if (sleepPoints.isNotEmpty()) {
                                    lineTo(sleepPoints.last().x, bottomY)
                                    lineTo(sleepPoints.first().x, bottomY)
                                    close()
                                }
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        orangeAccent.copy(alpha = 0.16f),
                                        orangeAccent.copy(alpha = 0.00f)
                                    ),
                                    startY = topY,
                                    endY = bottomY
                                )
                            )

                            drawPath(
                                path = path,
                                color = orangeAccent,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            sleepPoints.forEachIndexed { idx, pt ->
                                val isSelected = idx == selectedGraphIndex
                                drawCircle(
                                    color = if (isSelected) Color.White else orangeAccent,
                                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = orangeAccent,
                                    radius = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }

                    // Elegant overlays of 7 horizontal touch detectors
                    Row(modifier = Modifier.fillMaxSize()) {
                        trendPoints.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onIndexSelected(index)
                                    }
                            )
                        }
                    }
                }
            }

            // X-Axis day names labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, top = 4.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trendPoints.forEachIndexed { index, point ->
                    val isSelected = index == selectedGraphIndex
                    Text(
                        text = "${point.dayName} ${point.dateNum}",
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) themePrimary else Color.Gray,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onIndexSelected(index)
                        }
                    )
                }
            }

            // Beautiful Recharts Hover Tooltip Box overlay
            selectedPoint?.let { point ->
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📅 Trajectory Spot: June ${point.dateNum} (${point.dayName})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (point.entry != null) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Logged ✔", fontSize = 8.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Interpolated Pathway", fontSize = 8.sp, fontStyle = FontStyle.Italic, color = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "🎭 Mood Status: ${point.moodName} (${point.moodValue.toInt()}/10)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                "💤 Restoration: ${point.sleepValue.toInt()}/10 quality",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        val noteText = when {
                            point.entry != null && point.entry.oneSentenceNote.isNotBlank() -> point.entry.oneSentenceNote
                            point.entry != null && point.entry.freeWriteText.isNotBlank() -> point.entry.freeWriteText
                            else -> "No context note written. Keep checking in to build premium statistics!"
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📝 Note: \"${noteText.take(110)}${if(noteText.length > 110) "..." else ""}\"",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color.Gray,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

data class TrendPoint(
    val dayName: String,
    val dateNum: Int,
    val moodName: String,
    val moodValue: Float,
    val sleepValue: Float,
    val entry: PersonalJournalEntry?
)

