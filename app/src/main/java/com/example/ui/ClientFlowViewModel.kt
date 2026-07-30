package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ClientFlowViewModel(application: Application) : AndroidViewModel(application) {

    // Database and Repository Setup
    private val database: ClientFlowDatabase by lazy {
        Room.databaseBuilder(
            application,
            ClientFlowDatabase::class.java,
            "clientflow_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    val repository: ClientFlowRepository by lazy {
        ClientFlowRepository(database)
    }

    // Reactive State Holders
    val settingsState = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val personalEntriesState = repository.personalEntriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val patientsState = repository.patientsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val clinicalSessionsState = repository.clinicalSessionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // UI Interactive States
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _panicModeActivated = MutableStateFlow(false)
    val panicModeActivated: StateFlow<Boolean> = _panicModeActivated.asStateFlow()

    private val _currentPatientId = MutableStateFlow<String?>(null)
    val currentPatientId: StateFlow<String?> = _currentPatientId.asStateFlow()

    private val _currentAIWeeklySummary = MutableStateFlow<String>("")
    val currentAIWeeklySummary: StateFlow<String> = _currentAIWeeklySummary.asStateFlow()

    private val _isGeneratingSummary = MutableStateFlow(false)
    val isGeneratingSummary: StateFlow<Boolean> = _isGeneratingSummary.asStateFlow()

    private val _currentClinicalBrief = MutableStateFlow<String>("")
    val currentClinicalBrief: StateFlow<String> = _currentClinicalBrief.asStateFlow()

    private val _isGeneratingBrief = MutableStateFlow(false)
    val isGeneratingBrief: StateFlow<Boolean> = _isGeneratingBrief.asStateFlow()

    init {
        // Evaluate initial app lock state based on settings
        viewModelScope.launch {
            settingsState.filterNotNull().collectFirst { settings ->
                if (settings.pinCode.isEmpty() || settings.pinLockEnabled) {
                    _isAppLocked.value = true
                }
            }
        }
    }

    private suspend fun <T> Flow<T>.collectFirst(action: suspend (T) -> Unit) {
        take(1).collect(action)
    }

    // PIN Authentication Functions
    fun unlockApp(pin: String): Boolean {
        val currentSettings = settingsState.value ?: return false
        if (!currentSettings.pinLockEnabled) {
            _isAppLocked.value = false
            return true
        }
        return if (currentSettings.pinCode == pin) {
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun lockApp() {
        _isAppLocked.value = true
    }

    fun activatePanicMode() {
        _panicModeActivated.value = true
    }

    fun deactivatePanicMode() {
        _panicModeActivated.value = false
    }

    // Settings Modification
    fun registerLoginUser(email: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(syncedUserEmail = email) }
        }
    }

    fun updateSelectedMode(mode: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(selectedMode = mode) }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.updateSettings { it.copy(onboardingCompleted = true) }
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            repository.updateSettings {
                AppSettings(
                    id = 1,
                    selectedMode = null,
                    onboardingCompleted = false,
                    pinLockEnabled = false,
                    pinCode = ""
                )
            }
            repository.clearClinicalSandbox()
            _isAppLocked.value = false
            _panicModeActivated.value = false
            _currentPatientId.value = null
            _currentAIWeeklySummary.value = ""
            _currentClinicalBrief.value = ""
        }
    }

    fun togglePinSecurity(enabled: Boolean, code: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(pinLockEnabled = enabled, pinCode = if (enabled) code else "")
            }
            if (!enabled) {
                _isAppLocked.value = false
            }
        }
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(selectedTheme = themeName)
            }
        }
    }

    fun updateAccentColor(accentName: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(selectedAccent = accentName)
            }
        }
    }

    fun updateCloudSync(enabled: Boolean, email: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(cloudSyncEnabled = enabled, syncedUserEmail = email)
            }
        }
    }

    fun toggleMaskNames(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(maskClientNames = enabled) }
        }
    }

    fun toggleBlurNotes(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(blurClinicalNotes = enabled) }
        }
    }

    fun toggleObfuscateContacts(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(obfuscateContacts = enabled) }
        }
    }

    // PERSONAL JOURNAL FUNCTIONS
    fun addJournalEntry(
        mood: String,
        oneSentenceNote: String,
        freeWriteText: String,
        sleepQuality: Int,
        tags: String,
        audioFilePath: String? = null,
        transcribedText: String? = null,
        photoUri: String? = null,
        isLocked: Boolean = false
    ) {
        viewModelScope.launch {
            val entry = PersonalJournalEntry(
                mood = mood,
                oneSentenceNote = oneSentenceNote,
                freeWriteText = freeWriteText,
                sleepQuality = sleepQuality,
                tags = tags,
                audioFilePath = audioFilePath,
                transcribedText = transcribedText,
                photoUri = photoUri,
                isLocked = isLocked
            )
            repository.insertPersonalEntry(entry)
        }
    }

    fun deleteJournalEntry(entry: PersonalJournalEntry) {
        viewModelScope.launch {
            repository.deletePersonalEntry(entry)
        }
    }

    fun generatePersonalAISummary() {
        viewModelScope.launch {
            val entries = personalEntriesState.value
            if (entries.isEmpty()) {
                _currentAIWeeklySummary.value = "You haven't logged any reflections yet. Start logging your mood for a personalized AI summary!"
                return@launch
            }
            _isGeneratingSummary.value = true
            val summary = GeminiHelper.generatePersonalReflection(entries)
            _currentAIWeeklySummary.value = summary
            _isGeneratingSummary.value = false
            repository.updateSettings {
                it.copy(lastAIResponseSummary = summary, lastAIResponseDate = System.currentTimeMillis())
            }
        }
    }

    // PRACTITIONER FUNCTIONS
    fun selectPatient(patientId: String?) {
        _currentPatientId.value = patientId
        _currentClinicalBrief.value = "" // Reset brief on patient change
    }

    fun getSessionsForSelectedPatient(): Flow<List<ClinicalSessionLog>> {
        return _currentPatientId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getSessionsForPatientFlow(id)
        }
    }

    fun addPatient(
        name: String,
        email: String,
        phone: String,
        diagnosis: String,
        homeworkName: String,
        homeworkProgress: Float,
        therapeuticPhase: String,
        isDecliningSleep: Boolean
    ) {
        viewModelScope.launch {
            val pId = "PAT-${name.take(3).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"
            val patient = Patient(
                id = pId,
                name = name,
                email = email,
                phone = phone,
                diagnosis = diagnosis,
                homeworkName = homeworkName,
                homeworkProgress = homeworkProgress,
                therapeuticPhase = therapeuticPhase,
                isDecliningSleep = isDecliningSleep
            )
            repository.insertPatient(patient)
        }
    }

    fun addClinicalSession(
        patientId: String,
        duration: Int,
        mood: String,
        observations: String,
        homeworkCheck: String,
        energy: Int,
        sleep: Int,
        tags: String,
        notes: String,
        mediaPath: String? = null
    ) {
        viewModelScope.launch {
            val session = ClinicalSessionLog(
                patientId = patientId,
                durationMinutes = duration,
                sessionMood = mood,
                objectiveObservations = observations,
                homeworkCheck = homeworkCheck,
                energyScore = energy,
                sleepScore = sleep,
                tags = tags,
                notes = notes,
                mediaAttachmentPath = mediaPath
            )
            repository.insertClinicalSession(session)
        }
    }

    fun deletePatient(patientId: String) {
        viewModelScope.launch {
            repository.deletePatientById(patientId)
            if (_currentPatientId.value == patientId) {
                _currentPatientId.value = null
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteClinicalSessionById(sessionId)
        }
    }

    fun generatePreSessionBrief(patient: Patient) {
        viewModelScope.launch {
            _isGeneratingBrief.value = true
            val sessions = repository.getSessionsForPatient(patient.id)
            val brief = GeminiHelper.generateClinicalPreSessionBrief(patient, sessions)
            _currentClinicalBrief.value = brief
            _isGeneratingBrief.value = false
        }
    }

    // DEV SANDBOX CONTROLS
    fun loadClinicalSandboxDemo() {
        viewModelScope.launch {
            // Clear current caseload
            repository.clearClinicalSandbox()

            // Pre-populate Patients
            val p1 = Patient(
                id = "PAT-SJE-9230",
                name = "Sarah Jenkins",
                email = "s.jenkins@gmail.com",
                phone = "+1 650 382 9110",
                diagnosis = "F43.22 Adjustment Disorder with Anxious Mood",
                homeworkName = "3-column Cognitive Restructuring worksheet",
                homeworkProgress = 0.80f,
                therapeuticPhase = "Active Intervention",
                isDecliningSleep = true,
                createdAtMillis = System.currentTimeMillis() - 86400000 * 10
            )

            val p2 = Patient(
                id = "PAT-MCH-4812",
                name = "Michael Chen",
                email = "m.chen@outlook.com",
                phone = "+1 415 882 1092",
                diagnosis = "F41.1 Generalized Anxiety Disorder",
                homeworkName = "Daily diaphragmatic breathing loops (5 mins)",
                homeworkProgress = 0.40f,
                therapeuticPhase = "Assessment",
                isDecliningSleep = false,
                createdAtMillis = System.currentTimeMillis() - 86400000 * 5
            )

            val p3 = Patient(
                id = "PAT-AAD-2917",
                name = "Amara Adebayo",
                email = "amara.a@yahoo.com",
                phone = "+234 803 772 1923",
                diagnosis = "F33.1 Major Depressive Disorder, Moderate",
                homeworkName = "Behavioral Activation activity scheduling",
                homeworkProgress = 0.95f,
                therapeuticPhase = "Maintenance",
                isDecliningSleep = false,
                createdAtMillis = System.currentTimeMillis() - 86400000 * 15
            )

            repository.insertPatient(p1)
            repository.insertPatient(p2)
            repository.insertPatient(p3)

            // Pre-populate clinical session logs for Sarah
            repository.insertClinicalSession(ClinicalSessionLog(
                patientId = p1.id,
                dateMillis = System.currentTimeMillis() - 86400000 * 7,
                durationMinutes = 50,
                sessionMood = "Neutral",
                objectiveObservations = "Client presents with mild anxious tension regarding career transition. Engaged well with CBT basics.",
                homeworkCheck = "Completed CBT thoughts journal outline with help.",
                energyScore = 6,
                sleepScore = 7,
                tags = "CBT, Adjustment, Career",
                notes = "Encouraged active testing of worries. Client agreed to identify core automatic negative items.",
                mediaAttachmentPath = "intake_worksheet.pdf"
            ))

            repository.insertClinicalSession(ClinicalSessionLog(
                patientId = p1.id,
                dateMillis = System.currentTimeMillis() - 86400000 * 2,
                durationMinutes = 60,
                sessionMood = "Difficult",
                objectiveObservations = "Significant elevation in somatic anxiety triggers, sweating, and rapid speech described. Sleep disrupted.",
                homeworkCheck = "Struggled to complete cognitive restructuring worksheet.",
                energyScore = 4,
                sleepScore = 3,
                tags = "CBT, Somatic Anxiety",
                notes = "Slow-paced diaphragmatic breathing practiced in-session. Focused on grounding through sensory awareness.",
                mediaAttachmentPath = "somatic_diary.jpg"
            ))

            // Pre-populate clinical session logs for Amara
            repository.insertClinicalSession(ClinicalSessionLog(
                patientId = p3.id,
                dateMillis = System.currentTimeMillis() - 86400000 * 12,
                durationMinutes = 55,
                sessionMood = "Positive",
                objectiveObservations = "Marked improvement in mood stability and social engagement after starting behavioral homework.",
                homeworkCheck = "Logged 6/7 days of scheduled activities.",
                energyScore = 8,
                sleepScore = 8,
                tags = "MDD, Behavioral Activation",
                notes = "Explored relapse markers. Patient reports active coping loops feel native now.",
                mediaAttachmentPath = "activity_logs.xlsx"
            ))

            // Pre-populate mock personal journal data to make the Personal dashboard look beautiful when switching
            val entry1 = PersonalJournalEntry(
                dateMillis = System.currentTimeMillis() - 86400000 * 4,
                mood = "Neutral",
                oneSentenceNote = "Had a busy day completing assignments and checking up on family.",
                freeWriteText = "Feeling balanced today, though slightly tired. Managed to keep up with my morning stretches.",
                sleepQuality = 6,
                tags = "#work, #gratitude",
                isLocked = false
            )

            val entry2 = PersonalJournalEntry(
                dateMillis = System.currentTimeMillis() - 86400000 * 2,
                mood = "Calm",
                oneSentenceNote = "Took a long walk in the evening and read my favorite book.",
                freeWriteText = "Extremely peaceful afternoon. The walk really cleared my head. I feel connected and rested.",
                sleepQuality = 8,
                tags = "#relationships, #serene",
                isLocked = false
            )

            val entry3 = PersonalJournalEntry(
                dateMillis = System.currentTimeMillis() - 86400000 * 1,
                mood = "Anxious",
                oneSentenceNote = "Felt tense about the upcoming project milestone presentation tomorrow.",
                freeWriteText = "Woke up with an elevated pulse. Hard to sit tranquil. Tried to focus on breathing techniques, but worry remains high.",
                sleepQuality = 4,
                tags = "#work, #triggers",
                isLocked = false
            )

            val entry4 = PersonalJournalEntry(
                dateMillis = System.currentTimeMillis(),
                mood = "Productive",
                oneSentenceNote = "Crushed the core milestones, built standard interfaces cleanly!",
                freeWriteText = "Very successful day! Solved issues with great focus. Feeling very satisfied with the results.",
                sleepQuality = 9,
                tags = "#success",
                isLocked = false
            )

            repository.insertPersonalEntry(entry1)
            repository.insertPersonalEntry(entry2)
            repository.insertPersonalEntry(entry3)
            repository.insertPersonalEntry(entry4)
        }
    }

    fun wipeClinicalSandbox() {
        viewModelScope.launch {
            repository.clearClinicalSandbox()
        }
    }
}
