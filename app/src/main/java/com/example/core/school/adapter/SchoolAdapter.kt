package com.example.core.school.adapter

import android.content.Context
import com.example.core.school.model.*
import java.io.File

/**
 * Universal School Integration Adapter interface.
 * Normalized interface for interacting with any LMS, school portal, or manual fallback.
 */
interface SchoolAdapter {
    val platformInfo: SchoolPlatformInfo

    suspend fun detect(url: String): PlatformDetectionResult

    suspend fun authenticate(context: Context, config: SchoolAuthConfig): Result<SchoolAuthSession>

    suspend fun getCurrentUser(): Result<SchoolUser>

    suspend fun getCourses(): Result<List<Course>>

    suspend fun getCourseDetails(courseId: String): Result<Course>

    suspend fun getMaterials(courseId: String): Result<List<Material>>

    suspend fun getMaterialDetails(materialId: String): Result<Material>

    suspend fun downloadMaterial(material: Material, destinationFile: File): Result<File>

    suspend fun getAssignments(courseId: String): Result<List<Assignment>>

    suspend fun getAnnouncements(courseId: String): Result<List<Announcement>>

    suspend fun disconnect(): Result<Unit>
}
