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

class CanvasLmsAdapter(
    private var authSession: SchoolAuthSession? = null
) : SchoolAdapter {

    companion object {
        private const val TAG = "CanvasLmsAdapter"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override val platformInfo: SchoolPlatformInfo = SchoolPlatformInfo(
        id = "canvas",
        name = "Canvas LMS (Instructure)",
        baseUrl = "https://canvas.instructure.com",
        detectionConfidence = 1.0f,
        authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN,
        supportedFeatures = listOf("COURSES", "MATERIALS", "ASSIGNMENTS", "ANNOUNCEMENTS"),
        statusDescription = "Official Canvas REST API v1"
    )

    override suspend fun detect(url: String): PlatformDetectionResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim().lowercase()
        val isCanvasDomain = cleanUrl.contains("instructure.com") || cleanUrl.contains("canvas")

        if (isCanvasDomain) {
            PlatformDetectionResult(
                isRecognized = true,
                platformInfo = platformInfo.copy(baseUrl = url),
                authOptions = listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN, SchoolAuthType.OAUTH),
                supportsAutomatedSync = true
            )
        } else {
            // Check headers or meta tags for Canvas signature
            try {
                val req = Request.Builder()
                    .url(if (!url.startsWith("http")) "https://$url" else url)
                    .head()
                    .build()
                val resp = httpClient.newCall(req).execute()
                val isCanvasServer = resp.header("X-Canvas-Meta") != null ||
                        resp.header("Set-Cookie")?.contains("canvas") == true

                if (isCanvasServer) {
                    PlatformDetectionResult(
                        isRecognized = true,
                        platformInfo = platformInfo.copy(baseUrl = url),
                        authOptions = listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN, SchoolAuthType.OAUTH),
                        supportsAutomatedSync = true
                    )
                } else {
                    PlatformDetectionResult(
                        isRecognized = false,
                        platformInfo = platformInfo.copy(baseUrl = url, detectionConfidence = 0.0f),
                        authOptions = emptyList(),
                        supportsAutomatedSync = false
                    )
                }
            } catch (e: Exception) {
                PlatformDetectionResult(
                    isRecognized = isCanvasDomain,
                    platformInfo = platformInfo.copy(baseUrl = url),
                    authOptions = if (isCanvasDomain) listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN) else emptyList(),
                    supportsAutomatedSync = isCanvasDomain
                )
            }
        }
    }

    override suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession> = withContext(Dispatchers.IO) {
        try {
            val token = config.token?.trim() ?: return@withContext Result.failure(IllegalArgumentException("Canvas Personal Access Token required"))
            val baseUrl = if (config.schoolUrl.startsWith("http")) config.schoolUrl else "https://${config.schoolUrl}"

            val req = Request.Builder()
                .url("$baseUrl/api/v1/users/self/profile")
                .header("Authorization", "Bearer $token")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Canvas Authentication Failed (HTTP ${resp.code})"))
            }

            val body = resp.body?.string() ?: return@withContext Result.failure(Exception("Empty Canvas response"))
            val json = JSONObject(body)

            val session = SchoolAuthSession(
                platformId = "canvas",
                schoolUrl = baseUrl,
                accessToken = token,
                userDisplayName = json.optString("name", "Canvas Student"),
                userEmail = json.optString("primary_email", ""),
                authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN
            )
            authSession = session
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Canvas authentication error: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): Result<SchoolUser> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated with Canvas"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/api/v1/users/self/profile")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val json = JSONObject(body)

            Result.success(
                SchoolUser(
                    id = json.optString("id"),
                    name = json.optString("name", session.userDisplayName),
                    email = json.optString("primary_email", session.userEmail),
                    avatarUrl = json.optString("avatar_url", null)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/api/v1/courses?enrollment_state=active&include[]=total_students&include[]=teachers")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch Canvas courses: HTTP ${resp.code}"))
            }

            val body = resp.body?.string() ?: "[]"
            val array = JSONArray(body)
            val courses = mutableListOf<Course>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val courseName = obj.optString("name")
                if (courseName.isNotBlank()) {
                    val teachersArray = obj.optJSONArray("teachers")
                    val teacherName = if (teachersArray != null && teachersArray.length() > 0) {
                        teachersArray.getJSONObject(0).optString("display_name", "")
                    } else ""

                    courses.add(
                        Course(
                            id = obj.optString("id"),
                            name = courseName,
                            code = obj.optString("course_code", ""),
                            teacher = teacherName,
                            term = obj.optJSONObject("term")?.optString("name", "") ?: "",
                            sourcePlatform = "Canvas LMS"
                        )
                    )
                }
            }
            Result.success(courses)
        } catch (e: Exception) {
            Log.e(TAG, "Canvas getCourses error", e)
            Result.failure(e)
        }
    }

    override suspend fun getCourseDetails(courseId: String): Result<Course> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/api/v1/courses/$courseId")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val obj = JSONObject(body)

            Result.success(
                Course(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    code = obj.optString("course_code", ""),
                    sourcePlatform = "Canvas LMS"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMaterials(courseId: String): Result<List<Material>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/api/v1/courses/$courseId/files?sort=updated_at&order=desc")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch Canvas files: HTTP ${resp.code}"))
            }

            val body = resp.body?.string() ?: "[]"
            val array = JSONArray(body)
            val materials = mutableListOf<Material>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val fileName = obj.optString("display_name", obj.optString("filename", "File"))
                val contentType = obj.optString("content-type", "")
                val type = when {
                    contentType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
                    contentType.contains("word") || fileName.endsWith(".docx", ignoreCase = true) || fileName.endsWith(".doc", ignoreCase = true) -> "DOCX"
                    contentType.contains("presentation") || fileName.endsWith(".pptx", ignoreCase = true) || fileName.endsWith(".ppt", ignoreCase = true) -> "PPTX"
                    contentType.contains("image") -> "IMAGE"
                    else -> "DOCUMENT"
                }

                materials.add(
                    Material(
                        id = obj.optString("id"),
                        courseId = courseId,
                        courseName = "",
                        title = fileName.substringBeforeLast("."),
                        description = "Uploaded to Canvas. Size: ${obj.optLong("size") / 1024} KB",
                        type = type,
                        sourceUrl = obj.optString("url"),
                        downloadUrl = obj.optString("url"),
                        fileSize = obj.optLong("size", 0L)
                    )
                )
            }
            Result.success(materials)
        } catch (e: Exception) {
            Log.e(TAG, "Canvas getMaterials error", e)
            Result.failure(e)
        }
    }

    override suspend fun getMaterialDetails(materialId: String): Result<Material> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use getMaterials(courseId) for Canvas details"))
    }

    override suspend fun downloadMaterial(material: Material, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val downloadUrl = material.downloadUrl ?: return@withContext Result.failure(IllegalArgumentException("Missing download URL"))
        try {
            val req = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed HTTP ${resp.code}"))
            }

            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAssignments(courseId: String): Result<List<Assignment>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/api/v1/courses/$courseId/assignments?order_by=due_at")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: "[]"
            val array = JSONArray(body)
            val list = mutableListOf<Assignment>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Assignment(
                        id = obj.optString("id"),
                        courseId = courseId,
                        title = obj.optString("name"),
                        pointsPossible = obj.optDouble("points_possible", 100.0)
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAnnouncements(courseId: String): Result<List<Announcement>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        authSession = null
        Result.success(Unit)
    }
}
