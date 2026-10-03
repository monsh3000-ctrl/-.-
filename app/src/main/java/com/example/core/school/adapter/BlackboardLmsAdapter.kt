package com.example.core.school.adapter

import android.content.Context
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

class BlackboardLmsAdapter(
    private var authSession: SchoolAuthSession? = null
) : SchoolAdapter {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override val platformInfo: SchoolPlatformInfo = SchoolPlatformInfo(
        id = "blackboard",
        name = "Blackboard Learn",
        baseUrl = "https://blackboard.com",
        detectionConfidence = 1.0f,
        authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN,
        supportedFeatures = listOf("COURSES", "MATERIALS"),
        statusDescription = "Blackboard Learn REST API"
    )

    override suspend fun detect(url: String): PlatformDetectionResult = withContext(Dispatchers.IO) {
        val clean = url.trim().lowercase()
        val isBlackboard = clean.contains("blackboard") || clean.contains("bb9")
        PlatformDetectionResult(
            isRecognized = isBlackboard,
            platformInfo = platformInfo.copy(baseUrl = url),
            authOptions = listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN, SchoolAuthType.BROWSER_SSO),
            supportsAutomatedSync = isBlackboard
        )
    }

    override suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession> = withContext(Dispatchers.IO) {
        val token = config.token?.trim() ?: return@withContext Result.failure(IllegalArgumentException("Blackboard Token required"))
        val baseUrl = if (config.schoolUrl.startsWith("http")) config.schoolUrl else "https://${config.schoolUrl}"
        try {
            val req = Request.Builder()
                .url("$baseUrl/learn/api/public/v1/users/me")
                .header("Authorization", "Bearer $token")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("Blackboard authentication failed HTTP ${resp.code}"))

            val body = resp.body?.string() ?: "{}"
            val json = JSONObject(body)

            val session = SchoolAuthSession(
                platformId = "blackboard",
                schoolUrl = baseUrl,
                accessToken = token,
                userDisplayName = json.optString("name", "Blackboard Student"),
                userEmail = json.optString("contact", ""),
                authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN
            )
            authSession = session
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): Result<SchoolUser> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        Result.success(SchoolUser(id = "bb_user", name = session.userDisplayName, email = session.userEmail))
    }

    override suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val req = Request.Builder()
                .url("${session.schoolUrl}/learn/api/public/v1/users/me/courses")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))

            val body = resp.body?.string() ?: "{}"
            val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<Course>()

            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val courseObj = obj.optJSONObject("course") ?: obj
                val name = courseObj.optString("name", "")
                if (name.isNotBlank()) {
                    list.add(
                        Course(
                            id = courseObj.optString("id", obj.optString("courseId")),
                            name = name,
                            code = courseObj.optString("courseId", ""),
                            sourcePlatform = "Blackboard Learn"
                        )
                    )
                }
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
                .url("${session.schoolUrl}/learn/api/public/v1/courses/$courseId/contents")
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<Material>()

            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val title = obj.optString("title", "Course Content")
                list.add(
                    Material(
                        id = obj.optString("id"),
                        courseId = courseId,
                        courseName = "",
                        title = title,
                        description = obj.optString("body", ""),
                        type = "DOCUMENT"
                    )
                )
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
        Result.failure(UnsupportedOperationException())
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
