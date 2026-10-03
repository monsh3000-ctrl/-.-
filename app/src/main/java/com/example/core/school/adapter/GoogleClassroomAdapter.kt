package com.example.core.school.adapter

import android.content.Context
import android.util.Log
import com.example.core.school.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GoogleClassroomAdapter(
    private var authSession: SchoolAuthSession? = null
) : SchoolAdapter {

    companion object {
        private const val TAG = "GoogleClassroomAdapter"
        private const val BASE_CLASSROOM_API = "https://classroom.googleapis.com/v1"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override val platformInfo: SchoolPlatformInfo = SchoolPlatformInfo(
        id = "classroom",
        name = "Google Classroom",
        baseUrl = "https://classroom.google.com",
        detectionConfidence = 1.0f,
        authType = SchoolAuthType.GOOGLE_ACCOUNT,
        supportedFeatures = listOf("COURSES", "MATERIALS", "ASSIGNMENTS", "ANNOUNCEMENTS"),
        statusDescription = "Google Classroom API v1"
    )

    override suspend fun detect(url: String): PlatformDetectionResult = withContext(Dispatchers.IO) {
        val clean = url.trim().lowercase()
        val isClassroom = clean.contains("classroom.google.com") || clean.contains("google classroom")
        PlatformDetectionResult(
            isRecognized = isClassroom,
            platformInfo = platformInfo,
            authOptions = listOf(SchoolAuthType.GOOGLE_ACCOUNT, SchoolAuthType.OAUTH),
            supportsAutomatedSync = isClassroom
        )
    }

    override suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession> = withContext(Dispatchers.IO) {
        val token = config.token?.trim() ?: return@withContext Result.failure(IllegalArgumentException("Google OAuth token required"))
        try {
            val req = Request.Builder()
                .url("$BASE_CLASSROOM_API/courses?pageSize=5")
                .header("Authorization", "Bearer $token")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Google Classroom auth failed (HTTP ${resp.code})"))
            }

            val session = SchoolAuthSession(
                platformId = "classroom",
                schoolUrl = "https://classroom.google.com",
                accessToken = token,
                userDisplayName = config.username ?: "Google Classroom Student",
                authType = SchoolAuthType.GOOGLE_ACCOUNT
            )
            authSession = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): Result<SchoolUser> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        Result.success(SchoolUser(id = "google_user", name = session.userDisplayName, email = session.userEmail))
    }

    override suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("$BASE_CLASSROOM_API/courses?courseStates=ACTIVE")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))

            val body = resp.body?.string() ?: "{}"
            val coursesArray = JSONObject(body).optJSONArray("courses") ?: JSONArray()
            val list = mutableListOf<Course>()

            for (i in 0 until coursesArray.length()) {
                val obj = coursesArray.getJSONObject(i)
                list.add(
                    Course(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        code = obj.optString("section", ""),
                        term = obj.optString("room", ""),
                        sourcePlatform = "Google Classroom"
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCourseDetails(courseId: String): Result<Course> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun getMaterials(courseId: String): Result<List<Material>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("$BASE_CLASSROOM_API/courses/$courseId/courseWorkMaterials")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            val materialsArray = JSONObject(body).optJSONArray("courseWorkMaterial") ?: JSONArray()
            val list = mutableListOf<Material>()

            for (i in 0 until materialsArray.length()) {
                val obj = materialsArray.getJSONObject(i)
                val materialsSub = obj.optJSONArray("materials") ?: JSONArray()
                for (m in 0 until materialsSub.length()) {
                    val subObj = materialsSub.getJSONObject(m)
                    val driveFileObj = subObj.optJSONObject("driveFile")?.optJSONObject("driveFile")
                    if (driveFileObj != null) {
                        val title = driveFileObj.optString("title", "Classroom Material")
                        list.add(
                            Material(
                                id = driveFileObj.optString("id"),
                                courseId = courseId,
                                courseName = "",
                                title = title,
                                description = obj.optString("description", ""),
                                type = if (title.endsWith(".pdf", ignoreCase = true)) "PDF" else "DOCUMENT",
                                sourceUrl = driveFileObj.optString("alternateLink"),
                                downloadUrl = driveFileObj.optString("alternateLink")
                            )
                        )
                    }
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMaterialDetails(materialId: String): Result<Material> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun downloadMaterial(material: Material, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Google Classroom files are accessed via Google Drive API"))
    }

    override suspend fun getAssignments(courseId: String): Result<List<Assignment>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun getAnnouncements(courseId: String): Result<List<Announcement>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        authSession = null
        Result.success(Unit)
    }
}
