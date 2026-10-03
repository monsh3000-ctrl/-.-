package com.example.core.school.model

/**
 * Normalized representation of an academic course across any educational institution or LMS.
 */
data class Course(
    val id: String,
    val name: String,
    val code: String = "",
    val teacher: String = "",
    val term: String = "",
    val sourcePlatform: String = "School Portal",
    val materialCount: Int = 0,
    val lastSyncAt: Long = System.currentTimeMillis()
)

/**
 * Normalized representation of authorized educational material.
 */
data class Material(
    val id: String,
    val courseId: String,
    val courseName: String,
    val title: String,
    val description: String = "",
    val type: String, // PDF, DOCX, PPTX, TXT, IMAGE, WORKSHEET, SLIDES, READING
    val sourceUrl: String? = null,
    val downloadUrl: String? = null,
    val localFilePath: String? = null,
    val driveFileId: String? = null,
    val fileSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

data class Assignment(
    val id: String,
    val courseId: String,
    val title: String,
    val dueDate: Long? = null,
    val status: String = "OPEN",
    val pointsPossible: Double? = null,
    val submissionTypes: List<String> = emptyList()
)

data class Announcement(
    val id: String,
    val courseId: String,
    val title: String,
    val message: String,
    val postedAt: Long = System.currentTimeMillis(),
    val author: String = ""
)

data class SchoolPlatformInfo(
    val id: String,
    val name: String,
    val baseUrl: String,
    val detectionConfidence: Float = 1.0f,
    val authType: SchoolAuthType = SchoolAuthType.TOKEN_OR_OAUTH,
    val supportedFeatures: List<String> = listOf("COURSES", "MATERIALS", "ASSIGNMENTS"),
    val statusDescription: String = "Active"
)

enum class SchoolAuthType {
    OAUTH,
    PERSONAL_ACCESS_TOKEN,
    TOKEN_OR_OAUTH,
    BROWSER_SSO,
    GOOGLE_ACCOUNT,
    MANUAL_IMPORT_ONLY
}

data class PlatformDetectionResult(
    val isRecognized: Boolean,
    val platformInfo: SchoolPlatformInfo,
    val authOptions: List<SchoolAuthType>,
    val limitationReason: String? = null,
    val supportsAutomatedSync: Boolean = true
)

data class SchoolAuthConfig(
    val schoolUrl: String,
    val authType: SchoolAuthType,
    val token: String? = null,
    val username: String? = null,
    val password: String? = null,
    val cookies: Map<String, String> = emptyMap()
)

data class SchoolAuthSession(
    val platformId: String,
    val schoolUrl: String,
    val accessToken: String? = null,
    val cookies: Map<String, String> = emptyMap(),
    val userDisplayName: String = "",
    val userEmail: String = "",
    val expiresAt: Long? = null,
    val authType: SchoolAuthType = SchoolAuthType.PERSONAL_ACCESS_TOKEN
)

data class SchoolUser(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null
)
