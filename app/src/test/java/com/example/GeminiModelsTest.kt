package com.example

import com.example.data.repository.GeminiModels
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiModelsTest {
    private val live = listOf(
        "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash-001",
        "gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.5-flash-preview-09-2026",
        "gemini-2.5-flash-preview-tts", "gemini-2.5-flash-image", "text-embedding-004", "gemini-3-pro-preview"
    )

    @Test
    fun rankKeepsTextModelsNewestFirst() {
        assertEquals(
            listOf(
                "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash-preview-09-2026", "gemini-3-pro-preview",
                "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash-001"
            ),
            GeminiModels.rank(live)
        )
    }

    @Test
    fun newestOfAFamilyComesFirstThenBackups() {
        val order = GeminiModels.attemptOrder(GeminiModels.AUTO_FLASH_LITE, live)
        assertEquals("gemini-3.5-flash-lite", order[0])
        assertEquals("gemini-2.5-flash-lite", order[1])
        assertEquals("gemini-3.5-flash", order[2])
        assertEquals("gemini-3-pro-preview", GeminiModels.attemptOrder(GeminiModels.AUTO_PRO, live)[0])
    }

    @Test
    fun fixedModelFirstAndFallbackWhenNoList() {
        assertEquals("my-model", GeminiModels.attemptOrder("my-model", live)[0])
        assertEquals("gemini-2.5-flash", GeminiModels.attemptOrder(GeminiModels.AUTO_FLASH, emptyList())[0])
    }
}
