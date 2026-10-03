package com.example.core.school

import android.util.Patterns
import com.example.core.school.adapter.*
import com.example.core.school.model.PlatformDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class SchoolPlatformDetector {

    private val adapters: List<SchoolAdapter> = listOf(
        CanvasLmsAdapter(),
        MoodleLmsAdapter(),
        GoogleClassroomAdapter(),
        BlackboardLmsAdapter()
    )

    fun normalizeUrl(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return try {
            val uri = URI(trimmed)
            val host = uri.host ?: trimmed
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val path = uri.path ?: ""
            "$scheme://$host$port$path".trimEnd('/')
        } catch (e: Exception) {
            trimmed
        }
    }

    fun isValidSchoolUrl(url: String): Boolean {
        val normalized = normalizeUrl(url)
        return normalized.isNotBlank() && (
                Patterns.WEB_URL.matcher(normalized).matches() ||
                        normalized.contains(".")
                )
    }

    suspend fun detectPlatform(inputUrl: String): Pair<SchoolAdapter, PlatformDetectionResult> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(inputUrl)

        // Try specialized adapters first
        for (adapter in adapters) {
            try {
                val result = adapter.detect(normalized)
                if (result.isRecognized) {
                    return@withContext Pair(adapter, result)
                }
            } catch (e: Exception) {
                // Continue to next adapter
            }
        }

        // Fallback adapter for custom portals / unsupported automated systems
        val fallback = ManualImportFallbackAdapter(normalized)
        val fallbackResult = fallback.detect(normalized)
        Pair(fallback, fallbackResult)
    }
}
