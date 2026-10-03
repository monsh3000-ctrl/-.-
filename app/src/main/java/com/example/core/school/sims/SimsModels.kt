package com.example.core.school.sims

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SimsLoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
    @Json(name = "client_id") val clientId: String = "sims_mobile_app_v2"
)

@JsonClass(generateAdapter = true)
data class SimsLoginResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String = "Bearer",
    @Json(name = "student_id") val studentId: String = "SIMS_STU_8492",
    @Json(name = "student_name") val studentName: String = "Student",
    @Json(name = "school_name") val schoolName: String = "SIMS Partner Academy",
    @Json(name = "is_authenticated") val isAuthenticated: Boolean = true
)

@JsonClass(generateAdapter = true)
data class SimsCourse(
    @Json(name = "course_id") val courseId: String,
    @Json(name = "course_code") val courseCode: String,
    @Json(name = "subject") val subject: String,
    @Json(name = "title") val title: String,
    @Json(name = "teacher_name") val teacherName: String,
    @Json(name = "academic_year") val academicYear: String = "2026/2027"
)

@JsonClass(generateAdapter = true)
data class SimsAssignment(
    @Json(name = "assignment_id") val assignmentId: String,
    @Json(name = "course_id") val courseId: String,
    @Json(name = "title") val title: String,
    @Json(name = "subject") val subject: String,
    @Json(name = "unit_name") val unitName: String,
    @Json(name = "due_date") val dueDate: String,
    @Json(name = "instructions") val instructions: String,
    @Json(name = "attachment_name") val attachmentName: String,
    @Json(name = "attachment_type") val attachmentType: String, // PDF, WORKSHEET, HOMEWORK, STUDY_GUIDE
    @Json(name = "full_content") val fullContent: String
)

@JsonClass(generateAdapter = true)
data class SimsDocument(
    @Json(name = "document_id") val documentId: String,
    @Json(name = "course_id") val courseId: String,
    @Json(name = "title") val title: String,
    @Json(name = "subject") val subject: String,
    @Json(name = "unit_name") val unitName: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_type") val fileType: String,
    @Json(name = "summary") val summary: String,
    @Json(name = "text_content") val textContent: String
)
