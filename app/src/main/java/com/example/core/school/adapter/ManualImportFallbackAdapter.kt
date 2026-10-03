package com.example.core.school.adapter

import android.content.Context
import com.example.core.school.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class ManualImportFallbackAdapter(
    private var detectedUrl: String = ""
) : SchoolAdapter {

    override val platformInfo: SchoolPlatformInfo
        get() {
            val domain = try {
                val uri = URI(if (!detectedUrl.startsWith("http")) "https://$detectedUrl" else detectedUrl)
                uri.host ?: detectedUrl
            } catch (e: Exception) {
                detectedUrl.ifBlank { "Custom School Portal" }
            }
            return SchoolPlatformInfo(
                id = "manual_import",
                name = if (domain.isNotBlank() && domain != "Custom School Portal") "Custom Portal ($domain)" else "Direct / Manual Import",
                baseUrl = detectedUrl,
                detectionConfidence = 0.5f,
                authType = SchoolAuthType.MANUAL_IMPORT_ONLY,
                supportedFeatures = listOf("MANUAL_COURSES", "MANUAL_MATERIALS", "GOOGLE_DRIVE_SYNC"),
                statusDescription = "Direct Device & Drive Material Processing"
            )
        }

    override suspend fun detect(url: String): PlatformDetectionResult = withContext(Dispatchers.IO) {
        detectedUrl = url
        PlatformDetectionResult(
            isRecognized = false,
            platformInfo = platformInfo,
            authOptions = listOf(SchoolAuthType.MANUAL_IMPORT_ONLY),
            limitationReason = "This institution portal does not expose an open student REST API and uses custom SSO/MFA security boundaries. You can directly import your course documents, slides, and syllabus files via device storage or Google Drive.",
            supportsAutomatedSync = false
        )
    }

    override suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession> = withContext(Dispatchers.IO) {
        val session = SchoolAuthSession(
            platformId = "manual_import",
            schoolUrl = config.schoolUrl,
            userDisplayName = config.username?.ifBlank { "Student" } ?: "Student",
            authType = SchoolAuthType.MANUAL_IMPORT_ONLY
        )
        Result.success(session)
    }

    override suspend fun getCurrentUser(): Result<SchoolUser> = withContext(Dispatchers.IO) {
        Result.success(SchoolUser(id = "local_student", name = "Student", email = ""))
    }

    override suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun getCourseDetails(courseId: String): Result<Course> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun getMaterials(courseId: String): Result<List<Material>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun getMaterialDetails(materialId: String): Result<Material> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun downloadMaterial(material: Material, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun getAssignments(courseId: String): Result<List<Assignment>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun getAnnouncements(courseId: String): Result<List<Announcement>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }
}
