package com.example.core.school.adapter

import android.content.Context
import android.util.Log
import com.example.core.school.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MoodleLmsAdapter(
    private var authSession: SchoolAuthSession? = null
) : SchoolAdapter {

    companion object {
        private const val TAG = "MoodleLmsAdapter"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override val platformInfo: SchoolPlatformInfo = SchoolPlatformInfo(
        id = "moodle",
        name = "Moodle LMS",
        baseUrl = "https://moodle.org",
        detectionConfidence = 1.0f,
        authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN,
        supportedFeatures = listOf("COURSES", "MATERIALS", "ASSIGNMENTS"),
        statusDescription = "Moodle Mobile Web Services REST API"
    )

    override suspend fun detect(url: String): PlatformDetectionResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim().lowercase()
        val isMoodleDomain = cleanUrl.contains("moodle")
        try {
            val reqUrl = if (!url.startsWith("http")) "https://$url" else url
            val req = Request.Builder()
                .url("$reqUrl/webservice/rest/server.php")
                .head()
                .build()
            val resp = httpClient.newCall(req).execute()
            val hasMoodleWs = resp.code != 404

            PlatformDetectionResult(
                isRecognized = isMoodleDomain || hasMoodleWs,
                platformInfo = platformInfo.copy(baseUrl = url),
                authOptions = listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN),
                supportsAutomatedSync = isMoodleDomain || hasMoodleWs
            )
        } catch (e: Exception) {
            PlatformDetectionResult(
                isRecognized = isMoodleDomain,
                platformInfo = platformInfo.copy(baseUrl = url),
                authOptions = if (isMoodleDomain) listOf(SchoolAuthType.PERSONAL_ACCESS_TOKEN) else emptyList(),
                supportsAutomatedSync = isMoodleDomain
            )
        }
    }

    override suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession> = withContext(Dispatchers.IO) {
        try {
            val token = config.token?.trim() ?: return@withContext Result.failure(IllegalArgumentException("Moodle Web Services Token required"))
            val baseUrl = if (config.schoolUrl.startsWith("http")) config.schoolUrl else "https://${config.schoolUrl}"

            val postBody = FormBody.Builder()
                .add("wstoken", token)
                .add("wsfunction", "core_webservice_get_site_info")
                .add("moodlewsrestformat", "json")
                .build()

            val req = Request.Builder()
                .url("$baseUrl/webservice/rest/server.php")
                .post(postBody)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Moodle Authentication Failed (HTTP ${resp.code})"))
            }

            val body = resp.body?.string() ?: "{}"
            val json = JSONObject(body)

            if (json.has("exception")) {
                return@withContext Result.failure(Exception(json.optString("message", "Invalid Moodle token")))
            }

            val session = SchoolAuthSession(
                platformId = "moodle",
                schoolUrl = baseUrl,
                accessToken = token,
                userDisplayName = json.optString("fullname", "Moodle Student"),
                userEmail = json.optString("username", ""),
                authType = SchoolAuthType.PERSONAL_ACCESS_TOKEN
            )
            authSession = session
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Moodle authentication error", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): Result<SchoolUser> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        Result.success(
            SchoolUser(
                id = "moodle_user",
                name = session.userDisplayName,
                email = session.userEmail
            )
        )
    }

    override suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val postBody = FormBody.Builder()
                .add("wstoken", session.accessToken ?: "")
                .add("wsfunction", "core_course_get_enrolled_courses_by_timeline_classification")
                .add("classification", "all")
                .add("moodlewsrestformat", "json")
                .build()

            val req = Request.Builder()
                .url("${session.schoolUrl}/webservice/rest/server.php")
                .post(postBody)
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            val json = JSONObject(body)
            val coursesArray = json.optJSONArray("courses") ?: JSONArray()
            val courses = mutableListOf<Course>()

            for (i in 0 until coursesArray.length()) {
                val obj = coursesArray.getJSONObject(i)
                courses.add(
                    Course(
                        id = obj.optString("id"),
                        name = obj.optString("fullname"),
                        code = obj.optString("shortname", ""),
                        sourcePlatform = "Moodle LMS"
                    )
                )
            }
            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCourseDetails(courseId: String): Result<Course> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Use getCourses()"))
    }

    override suspend fun getMaterials(courseId: String): Result<List<Material>> = withContext(Dispatchers.IO) {
        val session = authSession ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        try {
            val postBody = FormBody.Builder()
                .add("wstoken", session.accessToken ?: "")
                .add("wsfunction", "core_course_get_contents")
                .add("courseid", courseId)
                .add("moodlewsrestformat", "json")
                .build()

            val req = Request.Builder()
                .url("${session.schoolUrl}/webservice/rest/server.php")
                .post(postBody)
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: "[]"
            val sectionsArray = JSONArray(body)
            val materials = mutableListOf<Material>()

            for (s in 0 until sectionsArray.length()) {
                val section = sectionsArray.getJSONObject(s)
                val sectionName = section.optString("name", "Section")
                val modulesArray = section.optJSONArray("modules") ?: continue

                for (m in 0 until modulesArray.length()) {
                    val mod = modulesArray.getJSONObject(m)
                    val modContents = mod.optJSONArray("contents") ?: continue
                    for (c in 0 until modContents.length()) {
                        val file = modContents.getJSONObject(c)
                        val fileName = file.optString("filename", "Material")
                        val fileUrl = file.optString("fileurl")
                        val downloadUrl = if (session.accessToken != null) "$fileUrl?token=${session.accessToken}" else fileUrl

                        materials.add(
                            Material(
                                id = file.optString("fileurl").hashCode().toString(),
                                courseId = courseId,
                                courseName = "",
                                title = fileName.substringBeforeLast("."),
                                description = "Section: $sectionName",
                                type = if (fileName.endsWith(".pdf", ignoreCase = true)) "PDF" else "DOCUMENT",
                                sourceUrl = fileUrl,
                                downloadUrl = downloadUrl,
                                fileSize = file.optLong("filesize", 0L)
                            )
                        )
                    }
                }
            }
            Result.success(materials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMaterialDetails(materialId: String): Result<Material> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException())
    }

    override suspend fun downloadMaterial(material: Material, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        val downloadUrl = material.downloadUrl ?: return@withContext Result.failure(IllegalArgumentException("Missing URL"))
        try {
            val req = Request.Builder().url(downloadUrl).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))

            resp.body?.byteStream()?.use { input ->
                FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
