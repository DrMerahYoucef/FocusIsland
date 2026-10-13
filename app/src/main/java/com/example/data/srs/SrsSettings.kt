package com.example.data.srs

import java.time.LocalTime

data class SrsSettings(
    val newCardsPerDay: Int = 20,
    val maxReviewsPerDay: Int = 200,
    val startingEaseFactor: Float = 2.5f,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val notificationsEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val explainModeEnabled: Boolean = false,
    val customPromptOverride: String? = null,
    /** A model id, or "newest of a family" (see GeminiModels.AUTO_CHOICES). */
    val selectedGeminiModel: String = "auto:flash",
    val availableGeminiModels: List<String> = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite"
    )
) {
    /** Model ids the user added by hand (the built-in defaults are not "custom"). */
    val customGeminiModels: List<String> get() = availableGeminiModels.filter { it !in DEFAULT_MODELS }

    companion object {
        val DEFAULT_MODELS = SrsSettings(selectedGeminiModel = "").availableGeminiModels
    }
}
