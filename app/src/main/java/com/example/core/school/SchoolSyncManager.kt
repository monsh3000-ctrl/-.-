package com.example.core.school

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.database.StudyLockDatabase
import com.example.core.database.entity.CourseEntity
import com.example.core.database.entity.SchoolConnectionEntity
import com.example.core.database.entity.SchoolMaterialEntity
import com.example.core.datastore.StudyLockPreferences
import com.example.core.document.DocumentProcessor
import com.example.core.drive.GoogleDriveFolderManager
import com.example.core.drive.GoogleDriveOAuthHelper
import com.example.core.engine.StudyContentGenerator
import com.example.core.repository.StudyLockRepository
import com.example.core.school.model.PlatformDetectionResult
import com.example.core.school.model.SchoolAuthConfig
import com.example.core.school.model.SchoolAuthType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SchoolSyncManager(
    private val context: Context,
    private val database: StudyLockDatabase = StudyLockDatabase.getInstance(context),
    private val preferences: StudyLockPreferences = StudyLockPreferences(context),
    private val contentGenerator: StudyContentGenerator
) {
    companion object {
        private const val TAG = "SchoolSyncManager"
    }

    private val detector = SchoolPlatformDetector()
    private val documentProcessor = DocumentProcessor(context)
    private val driveOAuthHelper = GoogleDriveOAuthHelper(context)
    private val driveFolderManager = GoogleDriveFolderManager(context, preferences, database)

    /**
     * Connects to any school portal entered by the student,
     * detects the LMS platform, authenticates legitimately,
     * and discovers real courses and educational materials.
     */
    suspend fun connectAndIngestSchool(
        schoolUrl: String,
        authTokenOrPassword: String = "",
        studentUsername: String = "",
        onProgress: suspend (step: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<PlatformDetectionResult> = withContext(Dispatchers.IO) {
        try {
            onProgress("Analyzing school portal ($schoolUrl)...", 0.15f)

            val normalizedUrl = detector.normalizeUrl(schoolUrl)
            val (adapter, detectionResult) = detector.detectPlatform(normalizedUrl)

            onProgress("Detected: ${detectionResult.platformInfo.name}", 0.35f)

            if (!detectionResult.supportsAutomatedSync) {
                // Legitimate fallback: Portal cannot be scraped without violating security boundaries
                val connection = SchoolConnectionEntity(
                    platformName = detectionResult.platformInfo.name,
                    schoolUrl = normalizedUrl,
                    username = studentUsername,
                    isKeepSignedIn = true,
                    isConnected = true,
                    connectedAt = System.currentTimeMillis(),
                    lastSyncAt = System.currentTimeMillis(),
                    isAutoSyncEnabled = false
                )
                database.schoolConnectionDao().insertOrUpdate(connection)
                preferences.setSchoolConnection(detectionResult.platformInfo.name, normalizedUrl, studentUsername, true)

                onProgress("Portal configured for direct material imports.", 1.0f)
                return@withContext Result.success(detectionResult)
            }

            // Authenticate with adapter
            onProgress("Authenticating with ${detectionResult.platformInfo.name}...", 0.50f)
            val authConfig = SchoolAuthConfig(
                schoolUrl = normalizedUrl,
                authType = detectionResult.authOptions.firstOrNull() ?: SchoolAuthType.PERSONAL_ACCESS_TOKEN,
                token = authTokenOrPassword.ifBlank { null },
                username = studentUsername.ifBlank { null }
            )

            val authResult = adapter.authenticate(context, authConfig)
            if (authResult.isFailure) {
                return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
            }

            val session = authResult.getOrThrow()

            // Save school connection
            val connection = SchoolConnectionEntity(
                platformName = detectionResult.platformInfo.name,
                schoolUrl = normalizedUrl,
                username = session.userEmail.ifBlank { session.userDisplayName }.ifBlank { studentUsername },
                isKeepSignedIn = true,
                isConnected = true,
                connectedAt = System.currentTimeMillis(),
                lastSyncAt = System.currentTimeMillis(),
                isAutoSyncEnabled = true
            )
            database.schoolConnectionDao().insertOrUpdate(connection)
            preferences.setSchoolConnection(detectionResult.platformInfo.name, normalizedUrl, connection.username, true)

            // Discover actual courses
            onProgress("Discovering enrolled academic courses...", 0.70f)
            val coursesResult = adapter.getCourses()
            val courses = coursesResult.getOrDefault(emptyList())

            if (courses.isNotEmpty()) {
                val entities = courses.map { c ->
                    CourseEntity(
                        courseExternalId = c.id,
                        name = c.name,
                        code = c.code,
                        teacher = c.teacher,
                        term = c.term,
                        sourcePlatform = detectionResult.platformInfo.name,
                        materialCount = c.materialCount,
                        lastSyncAt = System.currentTimeMillis()
                    )
                }
                database.courseDao().insertCourses(entities)
            }

            // Sync materials for discovered courses
            onProgress("Checking course syllabus & materials...", 0.85f)
            var totalMaterials = 0
            val repository = StudyLockRepository(context)

            for (course in courses.take(3)) {
                val matResult = adapter.getMaterials(course.id)
                val materials = matResult.getOrDefault(emptyList())
                for (mat in materials.take(4)) {
                    val destFile = File(context.cacheDir, "init_${mat.id}_${mat.title.take(15)}")
                    val dlRes = adapter.downloadMaterial(mat, destFile)
                    val text = if (dlRes.isSuccess) {
                        val doc = documentProcessor.processFile(destFile, mat.title).getOrNull()
                        doc?.extractedText ?: mat.description
                    } else mat.description
                    destFile.delete()

                    val matEntity = SchoolMaterialEntity(
                        title = mat.title,
                        originalFileName = "${mat.title}.${mat.type.lowercase()}",
                        fileType = mat.type,
                        subject = course.name,
                        unitName = "Course Materials",
                        driveFolderPath = "Study App/School Materials/${course.name}",
                        sourceUrl = mat.sourceUrl,
                        downloadUrl = mat.downloadUrl,
                        contentSummary = text.take(200),
                        fullTextContent = text,
                        isDeckGenerated = false,
                        isSyncIncluded = true
                    )
                    val insertedId = database.schoolMaterialDao().insertMaterial(matEntity)
                    totalMaterials++

                    if (text.length > 50) {
                        val deckId = repository.createDeck(
                            title = "${course.name}: ${mat.title}",
                            subject = course.name,
                            description = "Academic deck for ${mat.title}",
                            sourceText = text
                        )
                        repository.generateAndSaveDeckContent(deckId, text, 5, 5)
                        database.schoolMaterialDao().setDeckGenerated(insertedId, true)
                    }
                }
            }

            onProgress("Connection established! Discovered ${courses.size} courses and $totalMaterials materials.", 1.0f)
            Result.success(detectionResult)
        } catch (e: Exception) {
            Log.e(TAG, "School ingestion error", e)
            Result.failure(e)
        }
    }

    /**
     * Imports a user-selected document from device storage (PDF, DOCX, PPTX, image, or text),
     * extracts text, stores it under the chosen course, uploads to Google Drive if connected,
     * and generates an AI-grounded study deck.
     */
    suspend fun importLocalDocument(
        uri: Uri,
        fileName: String,
        courseName: String,
        unitName: String = "Course Documents"
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val processResult = documentProcessor.processUri(uri, fileName)
            val doc = processResult.getOrThrow()

            // Ensure course exists
            val existingCourse = database.courseDao().getAllCourses().find { it.name.equals(courseName, ignoreCase = true) }
            val courseId = if (existingCourse != null) {
                existingCourse.id
            } else {
                database.courseDao().insertCourse(
                    CourseEntity(
                        name = courseName,
                        code = courseName.take(4).uppercase(),
                        sourcePlatform = "Manual Import"
                    )
                )
            }

            val driveFolder = "Study App/School Materials/$courseName"
            val materialEntity = SchoolMaterialEntity(
                courseId = courseId,
                title = doc.title,
                originalFileName = fileName,
                fileType = doc.fileType,
                subject = courseName,
                unitName = unitName,
                driveFolderPath = driveFolder,
                contentSummary = doc.summary,
                fullTextContent = doc.extractedText,
                isDeckGenerated = false,
                isSyncIncluded = true
            )

            val materialId = database.schoolMaterialDao().insertMaterial(materialEntity)

            // Auto-generate grounded study deck
            val repository = StudyLockRepository(context)
            val deckTitle = "$courseName: ${doc.title}"
            val deckId = repository.createDeck(
                title = deckTitle,
                subject = courseName,
                description = "${doc.fileType} (${doc.pageCount} pages) imported for $courseName.",
                sourceText = doc.extractedText
            )
            repository.generateAndSaveDeckContent(deckId, doc.extractedText, 5, 5)
            database.schoolMaterialDao().setDeckGenerated(materialId, true)

            // If Google Drive is authorized, upload the file
            val accessToken = driveOAuthHelper.getValidAccessToken()
            if (accessToken != null) {
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                driveFolderManager.uploadMaterialFile(tempFile, courseName, null, accessToken, materialId)
                tempFile.delete()
            }

            driveFolderManager.refreshHierarchy()
            Result.success(materialId)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing local document", e)
            Result.failure(e)
        }
    }

    suspend fun disconnectSchool() = withContext(Dispatchers.IO) {
        database.schoolConnectionDao().clearConnection()
        preferences.clearSchoolConnection()
    }

    suspend fun autoGenerateDeckFromMaterial(material: SchoolMaterialEntity): Long? = withContext(Dispatchers.IO) {
        val repo = StudyLockRepository(context)
        val text = material.fullTextContent.ifBlank { material.contentSummary }
        if (text.isBlank()) return@withContext null
        val deckId = repo.createDeck(
            title = "${material.subject}: ${material.title}",
            subject = material.subject,
            description = "Generated from school material: ${material.title}",
            sourceText = text
        )
        repo.generateAndSaveDeckContent(deckId, text, 5, 5)
        database.schoolMaterialDao().setDeckGenerated(material.id, true)
        deckId
    }

    suspend fun syncNewMaterials(): List<SchoolMaterialEntity> = withContext(Dispatchers.IO) {
        database.schoolMaterialDao().getAllMaterialsList()
    }
}
