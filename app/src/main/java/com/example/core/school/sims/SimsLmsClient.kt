package com.example.core.school.sims

import android.util.Log
import com.example.core.database.entity.SchoolMaterialEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SimsLmsClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    /**
     * Authenticates with the school's SIMS LMS portal, pulls real course data,
     * homework assignments, and teacher materials, and converts them into SchoolMaterialEntities.
     */
    suspend fun loginAndFetchMaterials(
        portalUrl: String,
        username: String,
        password: String
    ): Result<List<SchoolMaterialEntity>> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = sanitizeUrl(portalUrl)

        val apiService = try {
            Retrofit.Builder()
                .baseUrl(cleanBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SimsApiService::class.java)
        } catch (e: Exception) {
            null
        }

        // Attempt live authentication call
        if (apiService != null) {
            try {
                val loginResponse = apiService.authenticate(
                    SimsLoginRequest(username = username, password = password)
                )

                if (loginResponse.isSuccessful && loginResponse.body()?.isAuthenticated == true) {
                    val token = "Bearer ${loginResponse.body()?.accessToken ?: "sims_auth_token"}"
                    val courses = apiService.getEnrolledCourses(token).body().orEmpty()
                    val resultMaterials = mutableListOf<SchoolMaterialEntity>()

                    for (course in courses) {
                        val assignments = apiService.getCourseAssignments(token, course.courseId).body().orEmpty()
                        for (assignment in assignments) {
                            resultMaterials.add(
                                SchoolMaterialEntity(
                                    title = assignment.title,
                                    originalFileName = assignment.attachmentName,
                                    fileType = assignment.attachmentType,
                                    subject = assignment.subject,
                                    unitName = assignment.unitName,
                                    driveFolderPath = "Google Drive → StudyLock → School Materials → ${assignment.subject} → ${assignment.unitName} → Files",
                                    driveFileId = "sims_${assignment.assignmentId}",
                                    contentSummary = assignment.instructions,
                                    fullTextContent = assignment.fullContent
                                )
                            )
                        }

                        val docs = apiService.getCourseDocuments(token, course.courseId).body().orEmpty()
                        for (doc in docs) {
                            resultMaterials.add(
                                SchoolMaterialEntity(
                                    title = doc.title,
                                    originalFileName = doc.fileName,
                                    fileType = doc.fileType,
                                    subject = doc.subject,
                                    unitName = doc.unitName,
                                    driveFolderPath = "Google Drive → StudyLock → School Materials → ${doc.subject} → ${doc.unitName} → Files",
                                    driveFileId = "sims_${doc.documentId}",
                                    contentSummary = doc.summary,
                                    fullTextContent = doc.textContent
                                )
                            )
                        }
                    }

                    if (resultMaterials.isNotEmpty()) {
                        return@withContext Result.success(resultMaterials)
                    } else {
                        return@withContext Result.failure(Exception("No coursework or materials found on SIMS portal."))
                    }
                } else {
                    return@withContext Result.failure(Exception("SIMS authentication rejected (HTTP ${loginResponse.code()}). Please check your portal credentials."))
                }
            } catch (e: Exception) {
                Log.e("SimsLmsClient", "SIMS connection error: ${e.message}", e)
                return@withContext Result.failure(Exception("Could not connect to SIMS portal at $cleanBaseUrl: ${e.message}"))
            }
        }

        Result.failure(Exception("Invalid or unreachable SIMS portal URL: $cleanBaseUrl"))
    }

    private fun sanitizeUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }
}
