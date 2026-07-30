package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.GeminiHelper
import com.example.data.PersonalJournalEntry
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// 1. MAIN GALLERY VIEW
// ==========================================

@Composable
fun JournalMediaGalleryView(
    viewModel: ClientFlowViewModel,
    onOpenEntry: (PersonalJournalEntry) -> Unit
) {
    val entries by viewModel.personalEntriesState.collectAsState()
    val context = LocalContext.current

    // Filters
    var selectedMediaType by remember { mutableStateOf("All") } // "All", "Photos", "Voice"
    var selectedMoodFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Quick image add launcher for gallery top bar
    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val joinedUris = uris.joinToString("|||") { it.toString() }
            viewModel.addJournalEntry(
                mood = "Calm",
                oneSentenceNote = "Added ${uris.size} visual moment(s) to media gallery",
                freeWriteText = "Captured photos directly from my media gallery.",
                sleepQuality = 7,
                tags = "#gallery, #photos",
                photoUri = uris.first().toString(),
                mediaUrisJson = joinedUris,
                aiSummary = "Visual memory logged with ${uris.size} photo attachment(s)."
            )
            Toast.makeText(context, "Added ${uris.size} photo(s) to journal entry!", Toast.LENGTH_SHORT).show()
        }
    }

    // Filtered entries containing media
    val entriesWithMedia = remember(entries, selectedMediaType, selectedMoodFilter, searchQuery) {
        entries.filter { entry ->
            val hasPhotos = entry.allMediaUris.isNotEmpty()
            val hasAudio = !entry.audioFilePath.isNullOrBlank()
            val matchesMedia = when (selectedMediaType) {
                "Photos" -> hasPhotos
                "Voice" -> hasAudio
                else -> hasPhotos || hasAudio
            }
            val matchesMood = selectedMoodFilter == null || entry.mood.equals(selectedMoodFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    entry.oneSentenceNote.contains(searchQuery, ignoreCase = true) ||
                    entry.freeWriteText.contains(searchQuery, ignoreCase = true) ||
                    entry.tags.contains(searchQuery, ignoreCase = true)

            matchesMedia && matchesMood && matchesQuery
        }
    }

    val totalMediaCount = remember(entries) {
        entries.sumOf { it.allMediaUris.size }
    }

    val totalAudioCount = remember(entries) {
        entries.count { !it.audioFilePath.isNullOrBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // GALLERY HEADER CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SageGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Collections,
                                contentDescription = "Media Gallery",
                                tint = SageGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Journal Media Gallery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "$totalMediaCount photos/images • $totalAudioCount voice recordings",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { multiImagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Photos", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SEARCH FIELD
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search attachments, notes or tags...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // FILTER TABS & MOOD CHIPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedMediaType == "All",
                onClick = { selectedMediaType = "All" },
                label = { Text("All Attachments") },
                leadingIcon = { Icon(Icons.Rounded.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = selectedMediaType == "Photos",
                onClick = { selectedMediaType = "Photos" },
                label = { Text("Photos & Images") },
                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = selectedMediaType == "Voice",
                onClick = { selectedMediaType = "Voice" },
                label = { Text("Voice Notes") },
                leadingIcon = { Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )

            Spacer(modifier = Modifier.width(8.dp))
            Divider(modifier = Modifier.height(20.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.width(8.dp))

            val moods = listOf("Productive", "Calm", "Reflective", "Neutral", "Anxious", "Overwhelmed")
            moods.forEach { mood ->
                val isSelected = selectedMoodFilter.equals(mood, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedMoodFilter = if (isSelected) null else mood },
                    label = { Text(mood) }
                )
            }
        }

        // GALLERY GRID OR EMPTY STATE
        if (entriesWithMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        "No Media Attachments Found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Attach photos, images, or voice notes when creating or editing a journal entry to view them in your gallery wall.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(entriesWithMedia, key = { it.id }) { entry ->
                    JournalMediaGridCard(
                        entry = entry,
                        onClick = { onOpenEntry(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun JournalMediaGridCard(
    entry: PersonalJournalEntry,
    onClick: () -> Unit
) {
    val mediaUris = entry.allMediaUris
    val primaryUri = mediaUris.firstOrNull()
    val formattedDate = remember(entry.dateMillis) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(entry.dateMillis))
    }

    val moodColor = when (entry.mood) {
        "Happy" -> HappyAura
        "Productive" -> ProductiveAura
        "Calm" -> CalmAura
        "Reflective" -> ReflectiveAura
        "Anxious" -> AnxiousAura
        "Overwhelmed" -> OverwhelmedAura
        else -> NeutralAura
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clickable { onClick() }
            .testTag("gallery_media_card_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!primaryUri.isNullOrEmpty()) {
                AsyncImage(
                    model = primaryUri,
                    contentDescription = "Journal Attachment",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Voice / text gradient cover
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(moodColor.copy(alpha = 0.4f), moodColor.copy(alpha = 0.8f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Voice Note",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Voice Recording",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // GRADIENT OVERLAY FOR READABILITY
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // TOP CHIPS: Mood Badge & Multi-photo badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = moodColor,
                    contentColor = Color.White
                ) {
                    Text(
                        text = entry.mood,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (mediaUris.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "+${mediaUris.size - 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (entry.aiSummary.isNotBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = SageGreen,
                            contentColor = Color.White
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Summary",
                                modifier = Modifier.padding(3.dp).size(12.dp)
                            )
                        }
                    }
                }
            }

            // BOTTOM EXCERPT & DATE
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.oneSentenceNote.ifBlank { entry.freeWriteText.ifBlank { "Media Reflection Entry" } },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ==========================================
// 2. JOURNAL ENTRY DETAIL & EDIT SCREEN
// ==========================================

@Composable
fun JournalEntryDetailDialog(
    entry: PersonalJournalEntry,
    viewModel: ClientFlowViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mode state: Read Mode vs Edit Mode
    var isEditMode by remember { mutableStateOf(false) }

    // Read Mode Customizers
    var readerPaperThemeName by remember { mutableStateOf("Warm Ivory") }
    var readerFontSize by remember { mutableStateOf(16f) }
    var readerFontFamilySelection by remember { mutableStateOf("Serif") }

    val paperThemes = remember {
        listOf(
            EasyPalette("Warm Ivory", Color(0xFFFDFBF7), Color(0xFFFFF9E6), Color(0xFF3E2723), Color(0xFF8D6E63)),
            EasyPalette("Calm Mist", Color(0xFFF1F8E9), Color(0xFFE8F5E9), Color(0xFF1B5E20), Color(0xFF4CAF50)),
            EasyPalette("Ocean Air", Color(0xFFE0F7FA), Color(0xFFE0F7FA), Color(0xFF006064), Color(0xFF00ACC1)),
            EasyPalette("Soft Lavender", Color(0xFFFAF5FF), Color(0xFFEDE7F6), Color(0xFF4A148C), Color(0xFF7E57C2)),
            EasyPalette("Velvet Dark", Color(0xFF151821), Color(0xFF222633), Color(0xFFECEFF1), Color(0xFF9FA8DA))
        )
    }
    val activePaper = paperThemes.find { it.name == readerPaperThemeName } ?: paperThemes.first()
    val activeFontFamily = when (readerFontFamilySelection) {
        "Serif" -> FontFamily.Serif
        "Sans-Serif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Serif
    }

    // Editable Form States
    var editMood by remember { mutableStateOf(entry.mood) }
    var editNote by remember { mutableStateOf(entry.oneSentenceNote) }
    var editFreeWrite by remember { mutableStateOf(entry.freeWriteText) }
    var editSleep by remember { mutableStateOf(entry.sleepQuality.toFloat()) }
    var editTags by remember { mutableStateOf(entry.tags) }
    var editMediaUris by remember { mutableStateOf(entry.allMediaUris) }

    // AI Summary State
    var currentAiSummary by remember { mutableStateOf(entry.aiSummary) }
    var isGeneratingAiSummary by remember { mutableStateOf(false) }

    // Lightbox image state
    var selectedLightboxImage by remember { mutableStateOf<String?>(null) }

    // Image Picker for Edit Mode
    val editImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newUris = uris.map { it.toString() }
            editMediaUris = (editMediaUris + newUris).distinct()
        }
    }

    val formattedDate = remember(entry.dateMillis) {
        SimpleDateFormat("EEEE, MMMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date(entry.dateMillis))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("journal_entry_detail_screen"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEditMode) MaterialTheme.colorScheme.surface else activePaper.background
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // HEADER BAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Rounded.EditNote else Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else activePaper.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) "Edit Journal Entry" else "Journal Sanctuary",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isEditMode) MaterialTheme.colorScheme.onSurface else activePaper.text
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Edit Mode Button
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier
                                .background(
                                    if (isEditMode) SageGreen else activePaper.accent.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                                contentDescription = "Toggle Edit Mode",
                                tint = if (isEditMode) Color.White else activePaper.text,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close Dialog Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(activePaper.accent.copy(alpha = 0.15f), CircleShape)
                                .size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = if (isEditMode) MaterialTheme.colorScheme.onSurface else activePaper.text,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isEditMode) {
                    // ==========================================
                    // READ MODE CONTENT
                    // ==========================================

                    // PAPER & FONT CUSTOMIZER BENCH
                    Card(
                        colors = CardDefaults.cardColors(containerColor = activePaper.cardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paper Theme", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activePaper.text)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    paperThemes.forEach { theme ->
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(theme.background)
                                                .border(
                                                    width = if (readerPaperThemeName == theme.name) 2.dp else 1.dp,
                                                    color = if (readerPaperThemeName == theme.name) theme.accent else Color.Gray.copy(alpha = 0.4f),
                                                    shape = CircleShape
                                                )
                                                .clickable { readerPaperThemeName = theme.name }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Font:", fontSize = 11.sp, color = activePaper.text)
                                    listOf("Serif", "Sans-Serif", "Monospace").forEach { family ->
                                        Text(
                                            text = family,
                                            fontSize = 10.sp,
                                            fontWeight = if (readerFontFamilySelection == family) FontWeight.Bold else FontWeight.Normal,
                                            color = if (readerFontFamilySelection == family) activePaper.accent else activePaper.text.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .clickable { readerFontFamilySelection = family }
                                                .padding(horizontal = 4.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Size", fontSize = 10.sp, color = activePaper.text)
                                    IconButton(
                                        onClick = { if (readerFontSize > 12f) readerFontSize -= 2f },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, color = activePaper.text)
                                    }
                                    Text("${readerFontSize.toInt()}pt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activePaper.text)
                                    IconButton(
                                        onClick = { if (readerFontSize < 26f) readerFontSize += 2f },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, color = activePaper.text)
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // DATE & MOOD BADGE
                        item {
                            Column {
                                Text(
                                    text = formattedDate,
                                    fontSize = 12.sp,
                                    color = activePaper.text.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val moodColor = when (entry.mood) {
                                        "Happy" -> HappyAura
                                        "Productive" -> ProductiveAura
                                        "Calm" -> CalmAura
                                        "Reflective" -> ReflectiveAura
                                        "Anxious" -> AnxiousAura
                                        "Overwhelmed" -> OverwhelmedAura
                                        else -> NeutralAura
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = moodColor,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = "Mood: ${entry.mood}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = activePaper.accent.copy(alpha = 0.15f),
                                        contentColor = activePaper.text
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(14.dp), tint = activePaper.accent)
                                            Text(
                                                text = "Sleep: ${entry.sleepQuality}/10",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ONE-SENTENCE REFLECTION NOTE
                        if (entry.oneSentenceNote.isNotBlank()) {
                            item {
                                Text(
                                    text = "\"${entry.oneSentenceNote}\"",
                                    fontSize = (readerFontSize + 2).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = activeFontFamily,
                                    color = activePaper.text,
                                    lineHeight = (readerFontSize + 8).sp
                                )
                            }
                        }

                        // ATTACHED MEDIA GALLERY CAROUSEL
                        if (entry.allMediaUris.isNotEmpty()) {
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Attached Media (${entry.allMediaUris.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = activePaper.text
                                        )
                                        Text(
                                            text = "Tap image to view full screen",
                                            fontSize = 11.sp,
                                            color = activePaper.text.copy(alpha = 0.6f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        entry.allMediaUris.forEach { uri ->
                                            Card(
                                                modifier = Modifier
                                                    .size(130.dp)
                                                    .clickable { selectedLightboxImage = uri },
                                                shape = RoundedCornerShape(14.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Attachment preview",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AUDIO RECORDING PLAYER
                        if (!entry.audioFilePath.isNullOrBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = activePaper.cardBg,
                                    border = BorderStroke(1.dp, activePaper.accent.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.Mic, contentDescription = null, tint = activePaper.accent)
                                            Text(
                                                "Voice Audio Note Attached",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = activePaper.text
                                            )
                                        }
                                        if (!entry.transcribedText.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Transcribed: ${entry.transcribedText}",
                                                fontSize = 12.sp,
                                                color = activePaper.text.copy(alpha = 0.8f),
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // FREE-WRITE REFLECTION TEXT
                        if (entry.freeWriteText.isNotBlank()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = activePaper.cardBg),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = entry.freeWriteText,
                                        fontSize = readerFontSize.sp,
                                        fontFamily = activeFontFamily,
                                        color = activePaper.text,
                                        lineHeight = (readerFontSize + 6).sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        // TAGS CHIPS
                        if (entry.tags.isNotBlank()) {
                            item {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    entry.tags.split(",", " ").filter { it.isNotBlank() }.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = activePaper.accent.copy(alpha = 0.15f),
                                            contentColor = activePaper.text
                                        ) {
                                            Text(
                                                text = if (tag.startsWith("#")) tag else "#$tag",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // GEMINI AI SUMMARY & THERAPEUTIC INSIGHTS CARD
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = SageGreen.copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, SageGreen.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = "AI Sparkle",
                                                tint = SageGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Gemini AI Reflection Summary",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = SageGreen
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                isGeneratingAiSummary = true
                                                viewModel.generateSingleEntryAiSummary(entry) { summary ->
                                                    currentAiSummary = summary
                                                    isGeneratingAiSummary = false
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            if (isGeneratingAiSummary) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = SageGreen
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Rounded.Refresh,
                                                    contentDescription = "Refresh AI Summary",
                                                    tint = SageGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (currentAiSummary.isNotBlank()) {
                                        Text(
                                            text = currentAiSummary,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Tap to generate an empathetic AI summary and therapeutic insight for this entry using Gemini.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                isGeneratingAiSummary = true
                                                viewModel.generateSingleEntryAiSummary(entry) { summary ->
                                                    currentAiSummary = summary
                                                    isGeneratingAiSummary = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Generate AI Summary", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // EDIT MODE CONTENT
                    // ==========================================

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text("Mood Baseline", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Productive", "Calm", "Reflective", "Neutral", "Anxious", "Overwhelmed").forEach { moodOption ->
                                    val isSelected = editMood == moodOption
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editMood = moodOption },
                                        label = { Text(moodOption) }
                                    )
                                }
                            }
                        }

                        item {
                            Text("One-Sentence Note", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            OutlinedTextField(
                                value = editNote,
                                onValueChange = { editNote = it },
                                placeholder = { Text("Summarize your entry in one line...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Text("Detailed Free-Write Reflection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            OutlinedTextField(
                                value = editFreeWrite,
                                onValueChange = { editFreeWrite = it },
                                placeholder = { Text("Express your full thoughts here...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 8
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sleep Quality Score", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${editSleep.toInt()}/10", fontWeight = FontWeight.Bold, color = SageGreen)
                            }
                            Slider(
                                value = editSleep,
                                onValueChange = { editSleep = it },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(thumbColor = SageGreen, activeTrackColor = SageGreen)
                            )
                        }

                        item {
                            Text("Tags (comma separated)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            OutlinedTextField(
                                value = editTags,
                                onValueChange = { editTags = it },
                                placeholder = { Text("e.g. #gratitude, #work") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // MEDIA ATTACHMENTS MANAGER
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Media Attachments (${editMediaUris.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Button(
                                        onClick = { editImagePickerLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Media", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (editMediaUris.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        editMediaUris.forEach { uri ->
                                            Box(modifier = Modifier.size(100.dp)) {
                                                AsyncImage(
                                                    model = uri,
                                                    contentDescription = "Attached photo",
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                IconButton(
                                                    onClick = { editMediaUris = editMediaUris.filter { it != uri } },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "No media attached yet. Tap 'Add Media' to choose photos from device.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // SAVE & DELETE BUTTONS
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.deleteJournalEntry(entry)
                                        Toast.makeText(context, "Journal entry deleted", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OverwhelmedAura),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete")
                                }

                                Button(
                                    onClick = {
                                        val updatedEntry = entry.copy(
                                            mood = editMood,
                                            oneSentenceNote = editNote,
                                            freeWriteText = editFreeWrite,
                                            sleepQuality = editSleep.toInt(),
                                            tags = editTags,
                                            photoUri = editMediaUris.firstOrNull(),
                                            mediaUrisJson = editMediaUris.joinToString("|||"),
                                            aiSummary = currentAiSummary
                                        )
                                        viewModel.updateJournalEntry(updatedEntry)
                                        Toast.makeText(context, "Entry updated successfully!", Toast.LENGTH_SHORT).show()
                                        isEditMode = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(2f)
                                ) {
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Changes")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // LIGHTBOX IMAGE OVERLAY
    if (selectedLightboxImage != null) {
        Dialog(
            onDismissRequest = { selectedLightboxImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { selectedLightboxImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = selectedLightboxImage,
                    contentDescription = "Full view photo",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { selectedLightboxImage = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
