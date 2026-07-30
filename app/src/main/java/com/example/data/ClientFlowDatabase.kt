package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// 1. ENTITIES
// ==========================================

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val selectedMode: String? = null, // "Personal" or "Practitioner"
    val onboardingCompleted: Boolean = false,
    val pinLockEnabled: Boolean = false,
    val pinCode: String = "",
    val maskClientNames: Boolean = false,
    val obfuscateContacts: Boolean = false,
    val blurClinicalNotes: Boolean = false,
    val lastAIResponseSummary: String = "",
    val lastAIResponseDate: Long = 0,
    val selectedTheme: String = "Natural Tones", // "Natural Tones", "Cosmic Slate", "Calm Lavender", "Sunset Warmth"
    val selectedAccent: String = "Sage", // "Mint", "Sage", "Ocean", "Indigo", "Lavender", "Rose", "Amber", "Terracotta", "Slate", "Gold"
    val cloudSyncEnabled: Boolean = false,
    val syncedUserEmail: String = ""
)

@Entity(tableName = "personal_journal_entries")
data class PersonalJournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val mood: String = "Neutral", // "Productive", "Calm", "Reflective", "Neutral", "Anxious", "Overwhelmed"
    val oneSentenceNote: String = "",
    val audioFilePath: String? = null,
    val transcribedText: String? = null,
    val freeWriteText: String = "",
    val sleepQuality: Int = 5, // 1 to 10
    val tags: String = "", // e.g. "#gratitude, #work"
    val photoUri: String? = null,
    val isLocked: Boolean = false
) {
    val moodWeight: Int
        get() = when (mood) {
            "Happy" -> 10
            "Productive" -> 9
            "Calm" -> 8
            "Reflective" -> 7
            "Neutral" -> 5
            "Anxious" -> 3
            "Overwhelmed" -> 2
            else -> 5
        }
}

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey val id: String, // Clinical ID (e.g. PAT-CODE-84)
    val name: String,
    val email: String,
    val phone: String,
    val diagnosis: String = "Adjustment Disorder",
    val homeworkName: String = "",
    val homeworkProgress: Float = 0.0f, // 0.0f to 1.0f
    val therapeuticPhase: String = "Assessment", // "Assessment", "Active Intervention", "Maintenance"
    val isDecliningSleep: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "clinical_session_logs")
data class ClinicalSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 50,
    val sessionMood: String = "Neutral", // "Positive", "Neutral", "Difficult"
    val objectiveObservations: String = "",
    val homeworkCheck: String = "",
    val energyScore: Int = 5, // 1 to 10
    val sleepScore: Int = 5,  // 1 to 10
    val tags: String = "", // e.g. "CBT, ACT"
    val notes: String = "",
    val mediaAttachmentPath: String? = null
)

// ==========================================
// 2. DAOS
// ==========================================

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}

@Dao
interface PersonalJournalEntryDao {
    @Query("SELECT * FROM personal_journal_entries ORDER BY dateMillis DESC")
    fun getAllEntriesFlow(): Flow<List<PersonalJournalEntry>>

    @Query("SELECT * FROM personal_journal_entries ORDER BY dateMillis DESC")
    suspend fun getAllEntries(): List<PersonalJournalEntry>

    @Query("SELECT * FROM personal_journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Int): PersonalJournalEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PersonalJournalEntry)

    @Delete
    suspend fun deleteEntry(entry: PersonalJournalEntry)
}

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY createdAtMillis DESC")
    fun getAllPatientsFlow(): Flow<List<Patient>>

    @Query("SELECT * FROM patients ORDER BY createdAtMillis DESC")
    suspend fun getAllPatients(): List<Patient>

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    suspend fun getPatientById(id: String): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient)

    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatientById(id: String)

    @Query("DELETE FROM patients")
    suspend fun clearAllPatients()
}

@Dao
interface ClinicalSessionLogDao {
    @Query("SELECT * FROM clinical_session_logs ORDER BY dateMillis DESC")
    fun getAllSessionsFlow(): Flow<List<ClinicalSessionLog>>

    @Query("SELECT * FROM clinical_session_logs WHERE patientId = :patientId ORDER BY dateMillis DESC")
    fun getSessionsForPatientFlow(patientId: String): Flow<List<ClinicalSessionLog>>

    @Query("SELECT * FROM clinical_session_logs WHERE patientId = :patientId ORDER BY dateMillis DESC")
    suspend fun getSessionsForPatient(patientId: String): List<ClinicalSessionLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ClinicalSessionLog)

    @Query("DELETE FROM clinical_session_logs WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    @Query("DELETE FROM clinical_session_logs")
    suspend fun clearAllSessions()
}

// ==========================================
// 3. DATABASE
// ==========================================

@Database(
    entities = [
        AppSettings::class,
        PersonalJournalEntry::class,
        Patient::class,
        ClinicalSessionLog::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ClientFlowDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun personalJournalEntryDao(): PersonalJournalEntryDao
    abstract fun patientDao(): PatientDao
    abstract fun clinicalSessionLogDao(): ClinicalSessionLogDao
}

// ==========================================
// 4. REPOSITORY
// ==========================================

class ClientFlowRepository(private val db: ClientFlowDatabase) {
    val settingsFlow: Flow<AppSettings?> = db.appSettingsDao().getSettingsFlow()
    val personalEntriesFlow: Flow<List<PersonalJournalEntry>> = db.personalJournalEntryDao().getAllEntriesFlow()
    val patientsFlow: Flow<List<Patient>> = db.patientDao().getAllPatientsFlow()
    val clinicalSessionsFlow: Flow<List<ClinicalSessionLog>> = db.clinicalSessionLogDao().getAllSessionsFlow()

    suspend fun getSettings(): AppSettings {
        return db.appSettingsDao().getSettings() ?: AppSettings().also {
            db.appSettingsDao().saveSettings(it)
        }
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        val current = getSettings()
        db.appSettingsDao().saveSettings(update(current))
    }

    suspend fun insertPersonalEntry(entry: PersonalJournalEntry) {
        db.personalJournalEntryDao().insertEntry(entry)
    }

    suspend fun deletePersonalEntry(entry: PersonalJournalEntry) {
        db.personalJournalEntryDao().deleteEntry(entry)
    }

    fun getSessionsForPatientFlow(patientId: String): Flow<List<ClinicalSessionLog>> {
        return db.clinicalSessionLogDao().getSessionsForPatientFlow(patientId)
    }

    suspend fun getSessionsForPatient(patientId: String): List<ClinicalSessionLog> {
        return db.clinicalSessionLogDao().getSessionsForPatient(patientId)
    }

    suspend fun insertPatient(patient: Patient) {
        db.patientDao().insertPatient(patient)
    }

    suspend fun deletePatientById(id: String) {
        db.patientDao().deletePatientById(id)
    }

    suspend fun insertClinicalSession(session: ClinicalSessionLog) {
        db.clinicalSessionLogDao().insertSession(session)
    }

    suspend fun deleteClinicalSessionById(id: Int) {
        db.clinicalSessionLogDao().deleteSessionById(id)
    }

    suspend fun clearClinicalSandbox() {
        db.patientDao().clearAllPatients()
        db.clinicalSessionLogDao().clearAllSessions()
    }
}
