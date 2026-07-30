package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiHelper {
    private const val TAG = "GeminiHelper"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Helper to check if key is set and valid
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Generates an empathetic weekly/monthly personal reflection summary based on daily entries.
     */
    suspend fun generatePersonalReflection(entries: List<PersonalJournalEntry>): String = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            return@withContext "You haven't logged any reflections yet. Start logging your mood and notes to receive a personalized AI summary!"
        }

        if (!isApiKeyAvailable()) {
            return@withContext generateFallbackPersonalReflection(entries)
        }

        val entriesSummary = entries.take(15).joinToString("\n") { entry ->
            "- Date: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(entry.dateMillis)}, " +
            "Mood: ${entry.mood} (Weight: ${entry.moodWeight}), " +
            "Sleep: ${entry.sleepQuality}/10, " +
            "Notes: ${entry.oneSentenceNote}. ${entry.freeWriteText}. Tags: ${entry.tags}"
        }

        val prompt = """
            You are ClientFlow's Supportive Wellness Companion. 
            Analyze the following personal wellness logs from the user. 
            Generate a reflective, compassionate weekly summary highlighting positive milestones, potential triggers, and sleep patterns.
            
            Strict constraints:
            1. Use a warm, empathetic, and encouraging voice.
            2. NEVER give diagnostic or clinical medical advice.
            3. Highlight visual correlation between sleep and mood (e.g. "When sleep dipped below 6, anxious states were more frequent").
            4. Keep the summary under 200 words. Split into 3 concise paragraphs with bold subheadings.
            
            Logs:
            $entriesSummary
        """.trimIndent()

        try {
            return@withContext callGeminiApi(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed, using fallback summary", e)
            return@withContext generateFallbackPersonalReflection(entries)
        }
    }

    /**
     * Generates a professional pre-session brief for practitioners based on clinical session history.
     */
    suspend fun generateClinicalPreSessionBrief(patient: Patient, sessions: List<ClinicalSessionLog>): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext generateFallbackClinicalBrief(patient, sessions)
        }

        val sessionsSummary = if (sessions.isEmpty()) {
            "No historical sessions logged yet."
        } else {
            sessions.take(5).joinToString("\n") { s ->
                "- Session date: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(s.dateMillis)}, " +
                "Mood: ${s.sessionMood}, Duration: ${s.durationMinutes}m, Energy: ${s.energyScore}/10, Sleep: ${s.sleepScore}/10, " +
                "Observations: ${s.objectiveObservations}, Homework: ${s.homeworkCheck}. Tags: ${s.tags}"
            }
        }

        val prompt = """
            You are ClientFlow's Chief Clinical Assistant.
            Generate a prep brief for the practitioner assisting patient code: ${patient.id}.
            Focus Phase: ${patient.therapeuticPhase}.
            Current Homework Progress: ${(patient.homeworkProgress * 100).toInt()}%.
            Diagnosis: ${patient.diagnosis}.
            
            Historical sessions (most recent first):
            $sessionsSummary
            
            Strict requirements:
            1. Keep it structured, objective, and highly professional.
            2. Highlight trends in sleep quality, client energy levels, or homework compliance.
            3. Call out active anomalies (e.g., if sleep dropped significantly or session mood turned Difficult).
            4. Suggest the next best starting focus or therapeutic objective.
            5. Keep it clear, bulleted, and under 250 words.
        """.trimIndent()

        try {
            return@withContext callGeminiApi(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed, using fallback clinical brief", e)
            return@withContext generateFallbackClinicalBrief(patient, sessions)
        }
    }

    /**
     * Simulates voice transcription with interactive assistance
     */
    suspend fun simulateVoiceTranscription(topic: String, mood: String): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext "Today I felt $mood of $topic. Recording my thoughts helps me reflect clearly on these feelings."
        }

        val prompt = """
            You are ClientFlow's Voice Transcription Transcriber. 
            The user recorded a spoken audio entry about: '$topic' while feeling '$mood'.
            Generate a natural-sounding, first-person spoken diary fragment (about 3-4 sentences, up to 60 words) reflecting this recording.
            Do not add titles or metadata, write it exactly as a transcribed spoken reflection.
        """.trimIndent()

        try {
            return@withContext callGeminiApi(prompt)
        } catch (e: Exception) {
            return@withContext "I spent some time reflecting on $topic today. I noticed feeling particularly $mood, and giving myself space to express this lets me process what is going on."
        }
    }

    /**
     * Generates a context-aware daily prompt based on recent mood entries/trends and phrased supportively.
     */
    suspend fun generateDailyPrompt(entries: List<PersonalJournalEntry>): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext generateFallbackDailyPrompt(entries)
        }

        val lastFewEntries = entries.take(5).joinToString("\n") { entry ->
            "- Mood: ${entry.mood}, Note: ${entry.oneSentenceNote}, Sleep: ${entry.sleepQuality}/10, Text: ${entry.freeWriteText}"
        }

        val prompt = """
            You are ClientFlow's Supportive Wellness Coach.
            Generate a single context-aware daily journal writing prompt for the user.
            Base it on their recent mood entries or trends:
            $lastFewEntries
            
            Strict constraints:
            1. Phrase it in a warm, supportive, encouraging, and gentle tone.
            2. It must be ONE single prompt/question that inspires self-reflection (e.g., "Since things have felt a bit overwhelming recently, what is one tiny boundary you could hold today to protect your energy?").
            3. Do not add any introductory pleasantries or wrap-around symbols. Just return the prompt statement itself.
            4. Keep it under 25 words.
        """.trimIndent()

        try {
            return@withContext callGeminiApi(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call for prompt failed, using fallback", e)
            return@withContext generateFallbackDailyPrompt(entries)
        }
    }

    /**
     * Generates an AI summary and key reflection insights for a single selected journal entry.
     */
    suspend fun generateSingleEntrySummary(entry: PersonalJournalEntry): String = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            return@withContext generateFallbackSingleEntrySummary(entry)
        }

        val formattedDate = java.text.SimpleDateFormat("EEEE, MMM dd, yyyy", java.util.Locale.getDefault()).format(entry.dateMillis)
        val prompt = """
            You are ClientFlow's Empathetic AI Reflection Assistant.
            Analyze the following personal journal entry:
            - Date: $formattedDate
            - Mood: ${entry.mood}
            - Sleep Quality: ${entry.sleepQuality}/10
            - Reflection Note: ${entry.oneSentenceNote}
            - Detailed Free-write: ${entry.freeWriteText}
            - Transcribed Audio: ${entry.transcribedText ?: "None"}
            - Tags: ${entry.tags}

            Requirements:
            1. Generate a concise, therapeutic 2-3 sentence summary of the emotional state and key insights.
            2. Offer 1 gentle, empowering takeaway for self-care.
            3. Keep the total response warm, compassionate, and under 80 words.
        """.trimIndent()

        try {
            return@withContext callGeminiApi(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call for entry summary failed, using fallback", e)
            return@withContext generateFallbackSingleEntrySummary(entry)
        }
    }

    private fun generateFallbackSingleEntrySummary(entry: PersonalJournalEntry): String {
        val moodInsight = when (entry.mood) {
            "Productive" -> "You experienced clear mental flow and accomplishment."
            "Calm" -> "You cultivated a serene, centered state of mind."
            "Reflective" -> "You engaged in quiet introspection and self-awareness."
            "Anxious" -> "You acknowledged elevated worry with openness and courage."
            "Overwhelmed" -> "You recognized feeling stretched thin, an important signal to slow down."
            else -> "You logged an honest check-in with your thoughts."
        }
        val sleepInsight = if (entry.sleepQuality >= 7) "Your rested sleep quality (${entry.sleepQuality}/10) provided strong support today." else "Rest was lower (${entry.sleepQuality}/10), suggesting extra self-compassion is beneficial."
        return "$moodInsight $sleepInsight Processing these thoughts empowers your wellness journey."
    }

    private fun generateFallbackDailyPrompt(entries: List<PersonalJournalEntry>): String {
        if (entries.isEmpty()) {
            return "How are you feeling as you start this journey today? What is one small expectation you'd like to release?"
        }
        val recentMoods = entries.take(3).map { it.mood }
        val primaryMood = recentMoods.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "Neutral"
        
        return when (primaryMood) {
            "Productive" -> "You've been in a wonderful flow! What's a small way you can honor this focus while planning for restorative rest layout today?"
            "Calm" -> "As you enjoy this peaceful, grounded moment, what is one thing about this baseline you feel grateful for?"
            "Reflective" -> "In this period of thoughtful introspection, what is a quiet realization or insight you've discovered about yourself?"
            "Neutral" -> "With a balanced and stable state today, what's a small, gentle micro-habit that feels good to focus on?"
            "Anxious" -> "Since things have felt a bit elevated and uncertain lately, where in your body do you feel this tension, and how can you offer yourself some soft margin?"
            "Overwhelmed" -> "With pressure running high, if you could take a pause button and let go of just one demanding task today, what would that be?"
            else -> "What is a gentle question you'd like to ask yourself today to check in with your heart?"
        }
    }

    private fun callGeminiApi(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val requestUrl = "$BASE_URL?key=$apiKey"

        val jsonRequest = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                put(JSONObject().apply { put("parts", partsArray) })
            }
            put("contents", contentsArray)
        }

        val body = jsonRequest.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code} - ${response.message}")
            }
            val responseString = response.body?.string() ?: throw Exception("Empty response body")
            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }

    // ==========================================
    // FALLBACKS (Zero-internet / No-key safe buffers)
    // ==========================================

    private fun generateFallbackPersonalReflection(entries: List<PersonalJournalEntry>): String {
        if (entries.isEmpty()) return "No records found. Keep logging entries to see trends!"
        
        // Dynamic analysis of simple list stats
        val moodCounts = entries.groupBy { it.mood }.mapValues { it.value.size }
        val dominantMood = moodCounts.maxByOrNull { it.value }?.key ?: "Neutral"
        val averageSleep = entries.map { it.sleepQuality }.average()
        
        val sb = StringBuilder()
        sb.append("**🌿 Gentle Reflection & Summary (Offline)**\n\n")
        sb.append("Looking back at your recent entries, your most frequently logged mood was **$dominantMood**. ")
        
        when (dominantMood) {
            "Productive" -> sb.append("You have been experiencing high engagement levels and active focus. It's wonderful to see you channeling your energy so effectively! Ensure you balance this with active rest.\n\n")
            "Calm" -> sb.append("You have maintained a peaceful, grounded, and serene base. This calm baseline is an excellent anchor for daily resilience.\n\n")
            "Reflective" -> sb.append("You are in a deeply introspective state, giving serious thought to your relationships or work goals. Writing down what is on your mind helps clear the path ahead.\n\n")
            "Neutral" -> sb.append("You have logged a steady baseline. A neutral state is a valuable stabilizer and a great platform to build daily gratitude.\n\n")
            "Anxious" -> sb.append("You've felt elevated levels of tension or distress recently. Remember to take gentle, slow breaths and allow yourself some compassionate margin.\n\n")
            "Overwhelmed" -> sb.append("Symptomatic and daily pressure is running high. If possible, consider stepping away from intense schedules, and focus purely on microscopic, small steps.\n\n")
        }

        sb.append("**💤 Sleep & Mood Connection**\n")
        sb.append(String.format("Your average sleep rating was **%.1f/10**. ", averageSleep))
        if (averageSleep < 6.0) {
            sb.append("A sleep score below 6.0 often acts as a quiet trigger that intensifies emotional sensitivity, particularly on demanding days. Prioritizing consistent wind-down times might give you added buffer.")
        } else {
            sb.append("This is a solid rest pattern, which is supporting your emotional baseline and giving you the endurance to navigate everyday stress.")
        }
        return sb.toString()
    }

    private fun generateFallbackClinicalBrief(patient: Patient, sessions: List<ClinicalSessionLog>): String {
        val totalSessions = sessions.size
        val avgSleep = if (sessions.isNotEmpty()) sessions.map { it.sleepScore }.average() else 5.0
        val avgEnergy = if (sessions.isNotEmpty()) sessions.map { it.energyScore }.average() else 5.0
        
        val lastSession = sessions.firstOrNull()
        
        val sb = StringBuilder()
        sb.append("### CLINICAL PRE-SESSION PREPARATION BRIEF\n\n")
        sb.append("• **Patient Code:** ${patient.id} (Phase: **${patient.therapeuticPhase}**)\n")
        sb.append("• **Primary Diagnosis:** ${patient.diagnosis}\n")
        sb.append("• **Homework Status:** Homework completion rate is currently at **${(patient.homeworkProgress * 100).toInt()}%** (${patient.homeworkName}).\n\n")
        
        sb.append("#### METRICS TREND & OBSERVATIONS\n")
        sb.append(String.format("- **Total Sessions logged:** %d\n", totalSessions))
        sb.append(String.format("- **Average Sleep Score:** %.1f/10\n", avgSleep))
        sb.append(String.format("- **Average Client Energy:** %.1f/10\n", avgEnergy))
        
        if (lastSession != null) {
            sb.append("- **Last Session Mood:** ${lastSession.sessionMood} (${lastSession.durationMinutes} minutes)\n")
            sb.append("- **Last Logged Note:** \"${lastSession.objectiveObservations}\"\n")
            
            if (lastSession.sleepScore <= 4 || patient.isDecliningSleep) {
                sb.append("⚠️ **ANOMALY ALERT:** Patient has reported declining sleep quality (recent rating of ${lastSession.sleepScore}/10). Risk profile is elevated due to fatigue accumulation.\n")
            }
        } else {
            sb.append("- No sessions have been registered yet. Initial consultation baseline will be established today.\n")
        }
        
        sb.append("\n#### SUGGESTED THERAPEUTIC FOCUS\n")
        when (patient.therapeuticPhase) {
            "Assessment" -> sb.append("1. Focus on establishing alliance, validating initial baseline symptoms, and modeling safety.\n2. Review family/educational origins of distress.")
            "Active Intervention" -> sb.append("1. Review Homework: ${patient.homeworkName} compliance.\n2. Apply target therapy concepts (e.g. CBT Restructuring or distress tolerance skills).\n3. Solicit concrete examples of triggers since last session.")
            "Maintenance" -> sb.append("1. Consolidate coping mechanisms and reinforce self-management success.\n2. Discuss relapse prevention markers and client support channels.")
        }
        
        return sb.toString()
    }
}
