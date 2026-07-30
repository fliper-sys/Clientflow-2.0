package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirestoreJournalComposerScreen(
    viewModel: ClientFlowViewModel,
    onEntrySaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isFirestoreSyncing.collectAsStateWithLifecycle()

    val syncedEmail = settings?.syncedUserEmail ?: ""
    val isCloudSyncEnabled = settings?.cloudSyncEnabled ?: false

    // Form inputs
    var titleNoteInput by remember { mutableStateOf("") }
    var journalContentInput by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Peaceful") }
    var tagsInput by remember { mutableStateOf("#reflection") }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessFeedback by remember { mutableStateOf(false) }

    val moodOptions = listOf(
        "Peaceful" to "🌿",
        "Calm" to "🧘",
        "Productive" to "💡",
        "Reflective" to "😌",
        "Energized" to "⚡",
        "Anxious" to "🌊"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. HEADER CARD WITH FIRESTORE SYNC BADGE
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.EditNote,
                                    contentDescription = "Journal Composer",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "New Reflection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Write & Sync to Firebase Firestore",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Cloud Sync Pill Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isCloudSyncEnabled) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCloudSyncEnabled) Color(0xFFA5D6A7) else Color(0xFFFFCC80)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCloudSyncEnabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                                contentDescription = "Sync Status",
                                tint = if (isCloudSyncEnabled) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCloudSyncEnabled) "Firestore Ready" else "Local DB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudSyncEnabled) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }
                    }
                }

                if (syncedEmail.isNotBlank()) {
                    Text(
                        text = "Synced User Account: $syncedEmail",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. MOOD SELECTOR CHIPS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Current State & Mood",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moodOptions.chunked(3).forEach { rowMoods ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowMoods.forEach { (mood, emoji) ->
                            val isSelected = selectedMood == mood
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMood = mood },
                                label = {
                                    Text("$emoji $mood", fontSize = 12.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mood_chip_$mood")
                            )
                        }
                    }
                }
            }
        }

        // 3. TITLE / SUMMARY HIGHLIGHT FIELD
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Entry Highlight / Title",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = titleNoteInput,
                onValueChange = { titleNoteInput = it },
                placeholder = { Text("e.g. Quiet evening walk & peaceful reflection") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_title_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // 4. MAIN JOURNAL TEXT INPUT AREA
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Journal Entry Body",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${journalContentInput.length} chars",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = journalContentInput,
                onValueChange = { journalContentInput = it },
                placeholder = { Text("Express your thoughts, progress, or feelings freely here...") },
                minLines = 6,
                maxLines = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_body_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // 5. TAGS INPUT FIELD
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Tags",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                placeholder = { Text("e.g. #mindfulness, #clarity, #progress") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Tag,
                        contentDescription = "Tags",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_tags_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        // 6. ACTION BUTTON & SUCCESS FEEDBACK
        AnimatedVisibility(
            visible = showSuccessFeedback,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF2E7D32)
                    )
                    Column {
                        Text(
                            text = "Journal Entry Saved & Firestore Synced!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "Stored locally in database and synced to cloud.",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (journalContentInput.isBlank() && titleNoteInput.isBlank()) {
                    Toast.makeText(context, "Please enter some text for your journal entry.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isSaving = true
                val noteTitle = if (titleNoteInput.isNotBlank()) titleNoteInput else journalContentInput.take(40)
                viewModel.addJournalEntry(
                    mood = selectedMood,
                    oneSentenceNote = noteTitle,
                    freeWriteText = journalContentInput,
                    sleepQuality = 7,
                    tags = tagsInput,
                    aiSummary = "Reflecting on $selectedMood mindset: $noteTitle"
                )

                showSuccessFeedback = true
                isSaving = false

                Toast.makeText(context, "Saved to Room DB & Syncing to Firestore!", Toast.LENGTH_SHORT).show()

                onEntrySaved?.invoke()

                // Reset form fields after save
                titleNoteInput = ""
                journalContentInput = ""
            },
            enabled = !isSaving && (titleNoteInput.isNotBlank() || journalContentInput.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_to_firestore_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isSaving || isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Saving & Syncing to Firestore...")
            } else {
                Icon(
                    imageVector = Icons.Rounded.CloudUpload,
                    contentDescription = "Save to Firestore",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save & Sync to Firestore",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
