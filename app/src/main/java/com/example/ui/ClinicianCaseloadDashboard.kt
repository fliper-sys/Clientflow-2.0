package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ClinicalSessionLog
import com.example.data.Patient
import com.example.data.ScheduledItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ClinicianCaseloadDashboard(
    viewModel: ClientFlowViewModel,
    patients: List<Patient>,
    clinicalSessions: List<ClinicalSessionLog>,
    scheduledItems: List<ScheduledItem>,
    maskClientNames: Boolean,
    onNavigateToPatientDetail: (String) -> Unit = {},
    onLogSession: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterPhase by remember { mutableStateOf("All") } // "All", "Active Intervention", "Assessment", "Maintenance", "At Risk"

    // Dialog Modal States
    var showAddPatientDialog by remember { mutableStateOf(false) }
    var showAddScheduleModal by remember { mutableStateOf(false) }
    var showStreakMilestonesModal by remember { mutableStateOf(false) }
    var selectedPatientForSchedule by remember { mutableStateOf<Patient?>(null) }
    var showLogSessionModalForPatient by remember { mutableStateOf<Patient?>(null) }

    // Computed Caseload Metrics & Streaks
    val clinicianStreak = remember(clinicalSessions, scheduledItems) {
        calculateClinicianStreak(clinicalSessions, scheduledItems)
    }
    val totalPatients = patients.size
    val activeInterventionsCount = patients.count { it.therapeuticPhase.equals("Active Intervention", ignoreCase = true) }
    val assessmentCount = patients.count { it.therapeuticPhase.equals("Assessment", ignoreCase = true) }
    val maintenanceCount = patients.count { it.therapeuticPhase.equals("Maintenance", ignoreCase = true) }
    val averageHomeworkProgress = if (patients.isNotEmpty()) {
        (patients.map { it.homeworkProgress }.average() * 100).toInt()
    } else 0

    val atRiskCount = patients.count { it.isDecliningSleep || it.homeworkProgress < 0.3f }
    val upcomingTodayCount = scheduledItems.count { !it.isCompleted }

    // Filtered Patient List
    val filteredPatients = patients.filter { patient ->
        val nameMatch = patient.name.contains(searchQuery, ignoreCase = true) || patient.id.contains(searchQuery, ignoreCase = true)
        val diagMatch = patient.diagnosis.contains(searchQuery, ignoreCase = true)
        val phaseMatch = when (selectedFilterPhase) {
            "All" -> true
            "At Risk" -> patient.isDecliningSleep || patient.homeworkProgress < 0.3f
            else -> patient.therapeuticPhase.equals(selectedFilterPhase, ignoreCase = true)
        }
        (nameMatch || diagMatch) && phaseMatch
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clinician_caseload_dashboard_container")
    ) {
        // 1. DASHBOARD HEADER & QUICK ACTIONS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .shadow(8.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MedicalServices,
                                contentDescription = "Clinician Icon",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clinician Caseload Center",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "High-level summary of active patients, progress notes, and upcoming sessions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    // Mask Names Toggle Chip Indicator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (maskClientNames) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable {
                                viewModel.updateMaskClientNames(!maskClientNames)
                                Toast.makeText(
                                    context,
                                    if (!maskClientNames) "HIPAA Privacy Mask Enabled" else "Full Patient Names Visible",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (maskClientNames) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Privacy Mask",
                                modifier = Modifier.size(16.dp),
                                tint = if (maskClientNames) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (maskClientNames) "Masked" else "Unmasked",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showAddPatientDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("add_new_patient_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "New Patient", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Patient", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showAddScheduleModal = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("schedule_appointment_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.EventAvailable, contentDescription = "Schedule", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Schedule Session", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. GLANCEABLE CLINICIAN USAGE STREAK & REWARD MILESTONE CARD
        ClinicianStreakSummaryCard(
            streakDays = clinicianStreak,
            onClick = { showStreakMilestonesModal = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        // 3. CASELOAD KPI METRICS STRIP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Metric 1: Total Active Caseload
            CaseloadKpiCard(
                title = "Total Caseload",
                value = totalPatients.toString(),
                subtitle = "$activeInterventionsCount in Active Tx",
                icon = Icons.Rounded.Groups,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // Metric 2: Upcoming Appointments
            CaseloadKpiCard(
                title = "Upcoming Sessions",
                value = upcomingTodayCount.toString(),
                subtitle = "${scheduledItems.size} Scheduled Total",
                icon = Icons.Rounded.Schedule,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )

            // Metric 3: At Risk / Attention Required
            CaseloadKpiCard(
                title = "Attention Flags",
                value = atRiskCount.toString(),
                subtitle = "Sleep / Homework Low",
                icon = Icons.Rounded.WarningAmber,
                accentColor = if (atRiskCount > 0) Color(0xFFE53935) else Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }

        // 3. UPCOMING APPOINTMENTS TIMELINE SECTION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
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
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Upcoming Sessions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upcoming Appointments & Push Alerts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(onClick = { showAddScheduleModal = true }) {
                        Text("+ Add Schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (scheduledItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No upcoming appointments scheduled. Tap '+ Add Schedule' above to schedule a session with push alerts.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    val sdf = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
                    scheduledItems.sortedBy { it.scheduledTimeMillis }.take(4).forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.isCompleted,
                                        onCheckedChange = { completed ->
                                            viewModel.toggleScheduleCompletion(item.id, completed)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (item.isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.reminderType} • ${sdf.format(Date(item.scheduledTimeMillis))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            viewModel.triggerInstantPushNotification(
                                                context,
                                                "Reminder: ${item.title}",
                                                "Upcoming clinical session scheduled at ${sdf.format(Date(item.scheduledTimeMillis))}"
                                            )
                                            Toast.makeText(context, "Push reminder sent!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsActive,
                                            contentDescription = "Send Push Reminder",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteSchedule(context, item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Schedule",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. PATIENT CASELOAD ROSTER HEADER & FILTER BAR
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Patient Roster (${filteredPatients.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Avg HW Compliance: $averageHomeworkProgress%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search patient name, ID, or diagnosis...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("caseload_search_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Phase Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("All", "Active Intervention", "Assessment", "Maintenance", "At Risk")
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilterPhase == filter,
                        onClick = { selectedFilterPhase = filter },
                        label = {
                            Text(
                                text = if (filter == "At Risk") "At Risk ($atRiskCount)" else filter,
                                fontSize = 12.sp,
                                fontWeight = if (selectedFilterPhase == filter) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filter == "At Risk") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = if (filter == "At Risk") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // 5. PATIENT CASELOAD CARDS LIST
        if (filteredPatients.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonSearch,
                        contentDescription = "No patients found",
                        tint = Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No patients match your search or filter.",
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Tap 'Add Patient' at the top to add a new client to your caseload.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredPatients.forEach { patient ->
                    ClinicianPatientCaseloadCard(
                        patient = patient,
                        maskClientNames = maskClientNames,
                        clinicalSessions = clinicalSessions.filter { it.patientId == patient.id },
                        onLogSession = { showLogSessionModalForPatient = patient },
                        onScheduleSession = {
                            selectedPatientForSchedule = patient
                            showAddScheduleModal = true
                        },
                        onViewDetail = { onNavigateToPatientDetail(patient.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. RECENT CLINICAL SESSION LOGS ACTIVITY STREAM
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.HistoryEdu,
                            contentDescription = "Recent Activity",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recent Clinical Activity & Session Logs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${clinicalSessions.size} Total Notes",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (clinicalSessions.isEmpty()) {
                    Text(
                        text = "No clinical session notes recorded yet. Select 'Log Note' on any patient above to record a session.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    clinicalSessions.sortedByDescending { it.dateMillis }.take(5).forEach { session ->
                        val matchingPatient = patients.find { it.id == session.patientId }
                        val displayName = if (maskClientNames) {
                            matchingPatient?.id ?: session.patientId
                        } else {
                            matchingPatient?.name ?: session.patientId
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${sdf.format(Date(session.dateMillis))} • ${session.durationMinutes} mins",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (session.objectiveObservations.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Obs: ${session.objectiveObservations}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (session.homeworkCheck.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "HW Status: ${session.homeworkCheck}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MODAL DIALOGS
    if (showAddPatientDialog) {
        AddPatientModalDialog(
            onDismiss = { showAddPatientDialog = false },
            onSavePatient = { name, diagnosis, email, phone, phase, hwName ->
                val newPatient = Patient(
                    id = "PAT-CODE-${(100..999).random()}",
                    name = name,
                    email = email.trim(),
                    phone = phone.trim(),
                    diagnosis = diagnosis.trim(),
                    therapeuticPhase = phase,
                    homeworkName = hwName.trim(),
                    homeworkProgress = 0.0f
                )
                viewModel.addPatient(newPatient)
                Toast.makeText(context, "Patient added to caseload successfully!", Toast.LENGTH_SHORT).show()
                showAddPatientDialog = false
            }
        )
    }

    if (showAddScheduleModal) {
        AddScheduleDialog(
            onDismiss = {
                showAddScheduleModal = false
                selectedPatientForSchedule = null
            },
            onSaveSchedule = { title, desc, delayMins, reminderType ->
                val triggerMillis = System.currentTimeMillis() + (delayMins * 60 * 1000L)
                val fullTitle = if (selectedPatientForSchedule != null) {
                    val pName = if (maskClientNames) selectedPatientForSchedule!!.id else selectedPatientForSchedule!!.name
                    "$pName: $title"
                } else title

                viewModel.addScheduledItem(
                    context = context,
                    title = fullTitle,
                    description = desc,
                    scheduledTimeMillis = triggerMillis,
                    reminderType = reminderType,
                    patientId = selectedPatientForSchedule?.id
                )
                Toast.makeText(context, "Session scheduled & push notification set!", Toast.LENGTH_SHORT).show()
                showAddScheduleModal = false
                selectedPatientForSchedule = null
            }
        )
    }

    if (showLogSessionModalForPatient != null) {
        LogClinicalSessionDialog(
            patient = showLogSessionModalForPatient!!,
            maskClientNames = maskClientNames,
            onDismiss = { showLogSessionModalForPatient = null },
            onSaveLog = { obs, hwCheck, energy, notes ->
                val sessionLog = ClinicalSessionLog(
                    patientId = showLogSessionModalForPatient!!.id,
                    dateMillis = System.currentTimeMillis(),
                    durationMinutes = 50,
                    objectiveObservations = obs,
                    homeworkCheck = hwCheck,
                    energyScore = energy,
                    notes = notes
                )
                viewModel.addClinicalSessionLog(sessionLog)
                Toast.makeText(context, "Clinical session note saved!", Toast.LENGTH_SHORT).show()
                showLogSessionModalForPatient = null
            }
        )
    }

    if (showStreakMilestonesModal) {
        ClinicianStreakMilestoneDialog(
            streakDays = clinicianStreak,
            onDismiss = { showStreakMilestonesModal = false }
        )
    }
}

@Composable
fun CaseloadKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = accentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ClinicianPatientCaseloadCard(
    patient: Patient,
    maskClientNames: Boolean,
    clinicalSessions: List<ClinicalSessionLog>,
    onLogSession: () -> Unit,
    onScheduleSession: () -> Unit,
    onViewDetail: () -> Unit
) {
    val context = LocalContext.current
    val displayName = if (maskClientNames) patient.id else patient.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clickable { onViewDetail() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Patient Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Patient Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = patient.diagnosis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Therapeutic Phase Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (patient.therapeuticPhase) {
                        "Active Intervention" -> MaterialTheme.colorScheme.primaryContainer
                        "Assessment" -> MaterialTheme.colorScheme.tertiaryContainer
                        "Maintenance" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = patient.therapeuticPhase,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Homework Progress Bar
            if (patient.homeworkName.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HW: ${patient.homeworkName}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(patient.homeworkProgress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { patient.homeworkProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (patient.homeworkProgress > 0.6f) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Footer Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLogSession,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = "Log Note", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Note", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onScheduleSession,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.AlarmAdd, contentDescription = "Schedule", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Schedule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Push homework alert sent to $displayName", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = "Send Push Alert",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddPatientModalDialog(
    onDismiss: () -> Unit,
    onSavePatient: (name: String, diagnosis: String, email: String, phone: String, phase: String, hwName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("Generalized Anxiety Disorder") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("Active Intervention") }
    var homeworkName by remember { mutableStateOf("Thought record & CBT journal") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Patient to Caseload",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Patient Full Name") },
                    placeholder = { Text("e.g. Sarah Jenkins") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = diagnosis,
                    onValueChange = { diagnosis = it },
                    label = { Text("Primary Diagnosis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = homeworkName,
                    onValueChange = { homeworkName = it },
                    label = { Text("Assigned Homework / Worksheet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Therapeutic Phase:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Assessment", "Active Intervention", "Maintenance").forEach { itemPhase ->
                        FilterChip(
                            selected = phase == itemPhase,
                            onClick = { phase = itemPhase },
                            label = { Text(itemPhase, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSavePatient(name, diagnosis, email, phone, phase, homeworkName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Save Patient")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Patient to Caseload", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LogClinicalSessionDialog(
    patient: Patient,
    maskClientNames: Boolean,
    onDismiss: () -> Unit,
    onSaveLog: (observations: String, hwCheck: String, energyScore: Int, notes: String) -> Unit
) {
    val displayName = if (maskClientNames) patient.id else patient.name
    var observations by remember { mutableStateOf("") }
    var hwCheck by remember { mutableStateOf("Completed assigned CBT worksheet") }
    var energyScore by remember { mutableIntStateOf(7) }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Log Clinical Session",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Client: $displayName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Objective Observations") },
                    placeholder = { Text("e.g. Client displayed reduced affect, articulated cognitive reframing...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = hwCheck,
                    onValueChange = { hwCheck = it },
                    label = { Text("Homework / Progress Check") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Therapeutic Notes / Next Steps") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSaveLog(observations, hwCheck, energyScore, notes)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save Note")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Clinical Note", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// CLINICIAN STREAK & MILESTONE COMPONENTS WITH CELEBRATION ANIMATIONS
// ==========================================

data class ClinicianMilestone(
    val targetDays: Int,
    val title: String,
    val rewardName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String
)

val CLINICIAN_MILESTONES = listOf(
    ClinicianMilestone(
        targetDays = 3,
        title = "Quick Note Apprentice",
        rewardName = "Clinical SOAP Note Template Pack",
        icon = Icons.Rounded.Bolt,
        description = "Maintain 3 consecutive days of caseload updates and session reviews."
    ),
    ClinicianMilestone(
        targetDays = 7,
        title = "Weekly Consistency Master",
        rewardName = "Caseload AI Diagnostic Trend Booster",
        icon = Icons.Rounded.Psychology,
        description = "Log full 7-day uninterrupted clinical patient documentation."
    ),
    ClinicianMilestone(
        targetDays = 14,
        title = "Dedicated Healer Shield",
        rewardName = "Burnout Protection & Wellness Analytics",
        icon = Icons.Rounded.VerifiedUser,
        description = "Maintain 2 consecutive weeks of balanced patient care."
    ),
    ClinicianMilestone(
        targetDays = 30,
        title = "Elite Caseload Director",
        rewardName = "Master Clinical Export & CEU Honor Badge",
        icon = Icons.Rounded.EmojiEvents,
        description = "Achieve 30 days of seamless clinical practice excellence."
    )
)

fun calculateClinicianStreak(
    sessions: List<ClinicalSessionLog>,
    scheduledItems: List<ScheduledItem>
): Int {
    val activeDays = mutableSetOf<String>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    sessions.forEach {
        activeDays.add(sdf.format(Date(it.dateMillis)))
    }
    scheduledItems.filter { it.isCompleted }.forEach {
        activeDays.add(sdf.format(Date(it.scheduledTimeMillis)))
    }

    // Include current session activity day
    activeDays.add(sdf.format(Date()))

    var streak = 0
    val checkCal = Calendar.getInstance()
    while (true) {
        val dateStr = sdf.format(checkCal.time)
        if (activeDays.contains(dateStr)) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return maxOf(1, streak)
}

// Particle model for streak milestone celebration
private data class ConfettiParticle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val size: Float,
    val shapeType: Int, // 0 = circle, 1 = rectangle ribbon, 2 = star sparkle
    val rotationSpeed: Float
)

@Composable
fun StreakMilestoneCelebrationEffect(
    triggerKey: Int,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    val particles = remember(triggerKey) {
        val random = Random(triggerKey + 42)
        val palette = listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF6D00), // Deep Orange
            Color(0xFFFFAB00), // Amber
            Color(0xFF00E676), // Emerald
            Color(0xFF7C4DFF), // Purple
            Color(0xFFFF4081)  // Hot Pink
        )
        List(36) {
            val angle = random.nextDouble(0.0, Math.PI * 2)
            val speed = random.nextDouble(180.0, 520.0).toFloat()
            ConfettiParticle(
                startX = 0.5f,
                startY = 0.45f,
                velocityX = (cos(angle) * speed).toFloat(),
                velocityY = (sin(angle) * speed - 120f).toFloat(),
                color = palette[random.nextInt(palette.size)],
                size = random.nextDouble(6.0, 14.0).toFloat(),
                shapeType = random.nextInt(3),
                rotationSpeed = random.nextDouble(-360.0, 360.0).toFloat()
            )
        }
    }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0) {
            animationProgress.snapTo(0f)
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
            )
        }
    }

    if (animationProgress.value > 0f && animationProgress.value < 1f) {
        val t = animationProgress.value
        val alpha = (1f - t).coerceIn(0f, 1f)

        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            particles.forEach { particle ->
                val currentX = (particle.startX * canvasW) + (particle.velocityX * t * (canvasW / 400f))
                val currentY = (particle.startY * canvasH) + (particle.velocityY * t * (canvasH / 400f)) + (280f * t * t)
                val currentRotation = particle.rotationSpeed * t

                rotate(degrees = currentRotation, pivot = Offset(currentX, currentY)) {
                    when (particle.shapeType) {
                        0 -> {
                            // Circular particle
                            drawCircle(
                                color = particle.color.copy(alpha = alpha),
                                radius = (particle.size / 2f) * (1f - (t * 0.3f)),
                                center = Offset(currentX, currentY)
                            )
                        }
                        1 -> {
                            // Ribbon rectangle
                            drawRect(
                                color = particle.color.copy(alpha = alpha),
                                topLeft = Offset(currentX - particle.size / 2f, currentY - particle.size / 4f),
                                size = Size(particle.size * 1.4f, particle.size * 0.6f)
                            )
                        }
                        else -> {
                            // Sparkle cross
                            val half = particle.size * 0.7f
                            drawLine(
                                color = particle.color.copy(alpha = alpha),
                                start = Offset(currentX - half, currentY),
                                end = Offset(currentX + half, currentY),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = particle.color.copy(alpha = alpha),
                                start = Offset(currentX, currentY - half),
                                end = Offset(currentX, currentY + half),
                                strokeWidth = 3f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicianStreakSummaryCard(
    streakDays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMilestone = CLINICIAN_MILESTONES.firstOrNull { it.targetDays >= streakDays }
        ?: CLINICIAN_MILESTONES.last()

    val previousTarget = CLINICIAN_MILESTONES.lastOrNull { it.targetDays < currentMilestone.targetDays }?.targetDays ?: 0
    val targetProgress = if (currentMilestone.targetDays > previousTarget) {
        ((streakDays - previousTarget).toFloat() / (currentMilestone.targetDays - previousTarget).toFloat()).coerceIn(0.05f, 1.0f)
    } else 1.0f

    val daysRemaining = maxOf(0, currentMilestone.targetDays - streakDays)
    val isMilestoneAchieved = daysRemaining == 0

    // Smooth Compose Animated Progress Bar
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "streakProgressBar"
    )

    // Breathing flame pulse transition animation
    val infiniteTransition = rememberInfiniteTransition(label = "streak_pulse_transition")
    val flamePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flamePulseScale"
    )

    val borderGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderGlowAlpha"
    )

    var celebrationTrigger by remember { mutableIntStateOf(0) }

    Card(
        modifier = modifier
            .shadow(
                elevation = if (isMilestoneAchieved) 6.dp else 3.dp,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                celebrationTrigger++
                onClick()
            }
            .testTag("clinician_streak_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isMilestoneAchieved) 1.5.dp else 1.dp,
            color = if (isMilestoneAchieved) Color(0xFFFF9800).copy(alpha = borderGlowAlpha) else Color(0xFFFFB74D).copy(alpha = 0.45f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Top Row: Streak Flame Badge & Target Milestone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streak Flame Chip with pulsing animation
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFECB3)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "Clinician Streak Flame",
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier
                                    .size(17.dp)
                                    .scale(flamePulseScale)
                            )
                            Text(
                                text = "$streakDays-Day Practice Streak",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color(0xFFD84315)
                            )
                        }
                    }

                    // Milestone Badge with Chevron
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMilestoneAchieved) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMilestoneAchieved) Icons.Rounded.CheckCircle else Icons.Rounded.EmojiEvents,
                                    contentDescription = "Next Milestone Trophy",
                                    tint = if (isMilestoneAchieved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isMilestoneAchieved) "Unlocked: ${currentMilestone.title}" else "Next: ${currentMilestone.title}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMilestoneAchieved) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "View Streak Milestones",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar towards Milestone with Animated Transition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMilestoneAchieved) "Milestone Achieved! 🎉" else "$daysRemaining ${if (daysRemaining == 1) "day" else "days"} to unlock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isMilestoneAchieved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$streakDays / ${currentMilestone.targetDays} Days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMilestoneAchieved) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Animated Linear Progress
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isMilestoneAchieved) Color(0xFF43A047) else Color(0xFFFF8F00),
                    trackColor = Color(0xFFFFE0B2).copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Perk Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CardGiftcard,
                        contentDescription = "Reward Perk",
                        tint = Color(0xFFD84315),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Reward: ${currentMilestone.rewardName}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Celebration Particle Canvas on user tap
            StreakMilestoneCelebrationEffect(
                triggerKey = celebrationTrigger,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun ClinicianStreakMilestoneDialog(
    streakDays: Int,
    onDismiss: () -> Unit
) {
    var celebrationTrigger by remember { mutableIntStateOf(1) } // auto trigger on open
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(14.dp, RoundedCornerShape(24.dp))
                .testTag("clinician_streak_dialog")
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFECB3)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Whatshot,
                                    contentDescription = "Flame Icon",
                                    tint = Color(0xFFFF6D00),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Practice Streaks & Perks",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Caseload Consistency Engine",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close Dialog")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Highlight Banner with spring scale entrance
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🔥 $streakDays-Day Active Practice",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color(0xFFBF360C)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Consistent documentation protects clinical accuracy and unlocks practitioner productivity perks.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5D4037),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reward Milestone Roadmap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = {
                                celebrationTrigger++
                                Toast.makeText(context, "🎉 Sparkles activated! Keep up the dedication.", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Sparkle celebration", modifier = Modifier.size(14.dp), tint = Color(0xFFFF8F00))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Celebrate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Milestone Cards List with animated state
                    CLINICIAN_MILESTONES.forEach { milestone ->
                        val isUnlocked = streakDays >= milestone.targetDays
                        val isCurrent = !isUnlocked && (CLINICIAN_MILESTONES.firstOrNull { it.targetDays >= streakDays } == milestone)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isUnlocked -> Color(0xFFE8F5E9)
                                isCurrent -> Color(0xFFFFF8E1)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isUnlocked -> Color(0xFF81C784)
                                    isCurrent -> Color(0xFFFFB74D)
                                    else -> Color.Transparent
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isUnlocked -> Color(0xFFC8E6C9)
                                        isCurrent -> Color(0xFFFFE082)
                                        else -> Color.LightGray.copy(alpha = 0.5f)
                                    }
                                ) {
                                    Icon(
                                        imageVector = milestone.icon,
                                        contentDescription = milestone.title,
                                        modifier = Modifier
                                            .padding(7.dp)
                                            .size(18.dp),
                                        tint = when {
                                            isUnlocked -> Color(0xFF2E7D32)
                                            isCurrent -> Color(0xFFE65100)
                                            else -> Color.Gray
                                        }
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${milestone.targetDays} Days • ${milestone.title}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isUnlocked) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                isUnlocked -> Color(0xFF2E7D32)
                                                isCurrent -> Color(0xFFEF6C00)
                                                else -> Color.Gray.copy(alpha = 0.3f)
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isUnlocked -> "Unlocked ✓"
                                                    isCurrent -> "$streakDays/${milestone.targetDays}d"
                                                    else -> "Locked"
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUnlocked || isCurrent) Color.White else Color.DarkGray,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Perk: ${milestone.rewardName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isUnlocked) Color(0xFF2E7D32) else Color(0xFFD84315)
                                    )
                                    Text(
                                        text = milestone.description,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            celebrationTrigger++
                            Toast.makeText(context, "🌟 Milestone progress synced!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Whatshot, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Keep Streak Alive", fontWeight = FontWeight.Bold)
                    }
                }

                // Confetti particle canvas overlay
                StreakMilestoneCelebrationEffect(
                    triggerKey = celebrationTrigger,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}
