package com.example.core.school.sims

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface SimsApiService {

    @POST("api/auth/login")
    suspend fun authenticate(
        @Body request: SimsLoginRequest
    ): Response<SimsLoginResponse>

    @GET("api/student/courses")
    suspend fun getEnrolledCourses(
        @Header("Authorization") bearerToken: String
    ): Response<List<SimsCourse>>

    @GET("api/student/courses/{courseId}/assignments")
    suspend fun getCourseAssignments(
        @Header("Authorization") bearerToken: String,
        @Path("courseId") courseId: String
    ): Response<List<SimsAssignment>>

    @GET("api/student/courses/{courseId}/documents")
    suspend fun getCourseDocuments(
        @Header("Authorization") bearerToken: String,
        @Path("courseId") courseId: String
    ): Response<List<SimsDocument>>
}
