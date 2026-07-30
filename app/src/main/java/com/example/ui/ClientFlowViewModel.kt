package com.example.ui

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import com.example.notifications.NotificationSchedulerManager
import com.example.security.BiometricAuthManager
import com.example.security.BiometricStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ClientFlowViewModel(application: Application) : AndroidViewModel(application) {

    val biometricAuthManager by lazy {
        BiometricAuthManager(getApplication())
    }

    private val _isJournalUnlocked = MutableStateFlow(false)
    val isJournalUnlocked: StateFlow<Boolean> = _isJournalUnlocked.asStateFlow()

    private val _isClientDataUnlocked = MutableStateFlow(false)
    val isClientDataUnlocked: StateFlow<Boolean> = _isClientDataUnlocked.asStateFlow()

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

    val scheduledItemsState = repository.scheduledItemsFlow.stateIn(
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

    // Biometric & Granular Data Security Functions
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Biometric Verification",
        subtitle: String = "Verify fingerprint or face recognition",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        biometricAuthManager.authenticate(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun unlockJournalWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        biometricAuthManager.authenticate(
            activity = activity,
            title = "Unlock Private Journal",
            subtitle = "Verify fingerprint or face recognition to view journal records",
            onSuccess = {
                _isJournalUnlocked.value = true
                onSuccess()
            },
            onError = onError
        )
    }

    fun unlockJournalWithPin(pin: String): Boolean {
        val currentSettings = settingsState.value ?: return true
        if (currentSettings.pinCode.isEmpty() || currentSettings.pinCode == pin) {
            _isJournalUnlocked.value = true
            return true
        }
        return false
    }

    fun relockJournal() {
        _isJournalUnlocked.value = false
    }

    fun unlockClientDataWithBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        biometricAuthManager.authenticate(
            activity = activity,
            title = "Unlock Client Records",
            subtitle = "Verify identity to access sensitive clinical client records",
            onSuccess = {
                _isClientDataUnlocked.value = true
                onSuccess()
            },
            onError = onError
        )
    }

    fun unlockClientDataWithPin(pin: String): Boolean {
        val currentSettings = settingsState.value ?: return true
        if (currentSettings.pinCode.isEmpty() || currentSettings.pinCode == pin) {
            _isClientDataUnlocked.value = true
            return true
        }
        return false
    }

    fun relockClientData() {
        _isClientDataUnlocked.value = false
    }

    fun toggleBiometricMasterLock(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(biometricLockEnabled = enabled) }
            biometricAuthManager.setBiometricEnabled(enabled)
        }
    }

    fun toggleJournalBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(journalBiometricLocked = enabled) }
            biometricAuthManager.setJournalLocked(enabled)
            if (!enabled) {
                _isJournalUnlocked.value = true
            } else {
                _isJournalUnlocked.value = false
            }
        }
    }

    fun toggleClientDataBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(clientDataBiometricLocked = enabled) }
            biometricAuthManager.setClientDataLocked(enabled)
            if (!enabled) {
                _isClientDataUnlocked.value = true
            } else {
                _isClientDataUnlocked.value = false
            }
        }
    }

    fun activatePanicMode() {
        _panicModeActivated.value = true
    }

    fun deactivatePanicMode() {
        _panicModeActivated.value = false
    }

    // Settings & Auth Modification
    fun registerLoginUser(email: String, isSignUp: Boolean = false, passwordInput: String = "ClientFlowSecure2026!") {
        viewModelScope.launch {
            val result = FirestoreSyncManager.authenticateUser(email, isSignUp, passwordInput)
            val authEmail = result.getOrDefault(email)
            repository.updateSettings { it.copy(syncedUserEmail = authEmail, cloudSyncEnabled = true) }
            triggerFirestoreSync()
        }
    }

    private val _isFirestoreSyncing = MutableStateFlow(false)
    val isFirestoreSyncing: StateFlow<Boolean> = _isFirestoreSyncing.asStateFlow()

    fun triggerFirestoreSync() {
        viewModelScope.launch {
            _isFirestoreSyncing.value = true
            try {
                val settings = settingsState.value ?: repository.getSettings()
                val email = settings.syncedUserEmail
                if (email.isNotBlank()) {
                    // 1. Pull remote data from Firestore to sync entries created on other devices
                    FirestoreSyncManager.fetchRemoteDataFromFirestore(
                        email = email,
                        repository = repository
                    )

                    // 2. Push local state to Firestore
                    val entries = personalEntriesState.value
                    val patients = patientsState.value
                    val sessions = clinicalSessionsState.value
                    val schedules = scheduledItemsState.value
                    FirestoreSyncManager.syncAllUserDataToFirestore(
                        email = email,
                        settings = settings,
                        entries = entries,
                        patients = patients,
                        sessions = sessions,
                        schedules = schedules
                    )
                }
            } finally {
                _isFirestoreSyncing.value = false
            }
        }
    }

    // SCHEDULES & PUSH NOTIFICATION FUNCTIONS
    fun addScheduledItem(
        context: Context,
        title: String,
        description: String = "",
        scheduledTimeMillis: Long,
        reminderType: String = "Session",
        patientId: String? = null
    ) {
        viewModelScope.launch {
            val item = ScheduledItem(
                title = title,
                description = description,
                scheduledTimeMillis = scheduledTimeMillis,
                reminderType = reminderType,
                patientId = patientId,
                isCompleted = false,
                notificationScheduled = true
            )
            val insertedId = repository.insertScheduledItem(item)
            
            // Schedule Push Notification via AlarmManager
            NotificationSchedulerManager.scheduleNotification(
                context = context,
                notificationId = insertedId.toInt(),
                title = title,
                content = if (description.isNotBlank()) description else "Scheduled $reminderType reminder in ClientFlow.",
                triggerAtMillis = scheduledTimeMillis
            )

            triggerFirestoreSync()
        }
    }

    fun toggleScheduleCompletion(id: Int, completed: Boolean) {
        viewModelScope.launch {
            repository.updateScheduleCompletion(id, completed)
            triggerFirestoreSync()
        }
    }

    fun deleteSchedule(context: Context, id: Int) {
        viewModelScope.launch {
            repository.deleteScheduleById(id)
            NotificationSchedulerManager.cancelNotification(context, id)
            triggerFirestoreSync()
        }
    }

    fun triggerInstantPushNotification(context: Context, title: String, content: String) {
        NotificationSchedulerManager.triggerInstantTestNotification(context, title, content)
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

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(isDarkMode = enabled)
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
            triggerFirestoreSync()
        }
    }

    fun updateMaskClientNames(enabled: Boolean) {
        toggleMaskNames(enabled)
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
        isLocked: Boolean = false,
        mediaUrisJson: String = "",
        aiSummary: String = ""
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
                isLocked = isLocked,
                mediaUrisJson = mediaUrisJson,
                aiSummary = aiSummary
            )
            repository.insertPersonalEntry(entry)
            triggerFirestoreSync()
        }
    }

    fun addSpokenJournalEntryAndSummarize(
        mood: String,
        oneSentenceNote: String,
        transcribedText: String,
        audioFilePath: String? = null,
        sleepQuality: Int = 5,
        tags: String = "#spoken, #voice_entry",
        generateAiSummaryImmediately: Boolean = true,
        onCompleted: ((PersonalJournalEntry, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val tempEntry = PersonalJournalEntry(
                mood = mood,
                oneSentenceNote = oneSentenceNote,
                freeWriteText = transcribedText,
                transcribedText = transcribedText,
                audioFilePath = audioFilePath ?: "spoken_record_${System.currentTimeMillis()}.mp3",
                sleepQuality = sleepQuality,
                tags = tags
            )
            val summary = if (generateAiSummaryImmediately) {
                GeminiHelper.generateSingleEntrySummary(tempEntry)
            } else ""

            val finalEntry = tempEntry.copy(aiSummary = summary)
            repository.insertPersonalEntry(finalEntry)
            triggerFirestoreSync()
            onCompleted?.invoke(finalEntry, summary)
        }
    }

    fun updateJournalEntry(entry: PersonalJournalEntry) {
        viewModelScope.launch {
            repository.insertPersonalEntry(entry)
            triggerFirestoreSync()
        }
    }

    fun generateSingleEntryAiSummary(entry: PersonalJournalEntry, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val summary = GeminiHelper.generateSingleEntrySummary(entry)
            val updated = entry.copy(aiSummary = summary)
            repository.insertPersonalEntry(updated)
            triggerFirestoreSync()
            onResult(summary)
        }
    }

    fun deleteJournalEntry(entry: PersonalJournalEntry) {
        viewModelScope.launch {
            repository.deletePersonalEntry(entry)
            triggerFirestoreSync()
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

    fun addPatient(patient: Patient) {
        viewModelScope.launch {
            repository.insertPatient(patient)
            triggerFirestoreSync()
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
            triggerFirestoreSync()
        }
    }

    fun addClinicalSessionLog(session: ClinicalSessionLog) {
        viewModelScope.launch {
            repository.insertClinicalSession(session)
            triggerFirestoreSync()
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

    // DEV DATA MANAGEMENT CONTROLS
    fun loadClinicalSandboxDemo() {
        wipeClinicalSandbox()
    }

    fun wipeClinicalSandbox() {
        viewModelScope.launch {
            repository.clearClinicalSandbox()
        }
    }
}
