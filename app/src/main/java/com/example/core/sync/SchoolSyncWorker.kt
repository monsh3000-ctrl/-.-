package com.example.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.R
import com.example.core.database.StudyLockDatabase
import com.example.core.database.entity.CourseEntity
import com.example.core.database.entity.SchoolMaterialEntity
import com.example.core.datastore.StudyLockPreferences
import com.example.core.document.DocumentProcessor
import com.example.core.repository.StudyLockRepository
import com.example.core.drive.GoogleDriveFolderManager
import com.example.core.engine.StudyContentGenerator
import com.example.core.school.SchoolPlatformDetector
import com.example.core.school.adapter.CanvasLmsAdapter
import com.example.core.school.model.Material
import com.example.core.school.model.SchoolAuthConfig
import com.example.core.school.model.SchoolAuthType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class SchoolSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SchoolSyncWorker"
        const val WORK_NAME = "studylock_school_sync_worker"
        const val CHANNEL_ID = "studylock_academic_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SchoolSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.i(TAG, "Periodic school sync worker scheduled every 6 hours")
        }

        fun triggerImmediateSync(context: Context): Operation {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeSync = OneTimeWorkRequestBuilder<SchoolSyncWorker>()
                .setConstraints(constraints)
                .build()

            return WorkManager.getInstance(context).enqueue(oneTimeSync)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = StudyLockDatabase.getInstance(applicationContext)
        val preferences = StudyLockPreferences(applicationContext)

        val isSyncEnabled = preferences.isSchoolAutoSyncEnabled.first()
        if (!isSyncEnabled) {
            Log.d(TAG, "Automatic sync is disabled by user settings")
            return@withContext Result.success()
        }

        val activeConn = database.schoolConnectionDao().getActiveConnection()
        if (activeConn == null || !activeConn.isConnected) {
            Log.d(TAG, "No active school connection found for background sync")
            return@withContext Result.success()
        }

        try {
            Log.i(TAG, "Starting background sync for ${activeConn.platformName} (${activeConn.schoolUrl})...")
            val detector = SchoolPlatformDetector()
            val (adapter, detectionResult) = detector.detectPlatform(activeConn.schoolUrl)

            if (!detectionResult.supportsAutomatedSync) {
                Log.d(TAG, "Portal does not support automated background sync: ${detectionResult.limitationReason}")
                return@withContext Result.success()
            }

            // Retrieve courses
            val coursesResult = adapter.getCourses()
            val courses = coursesResult.getOrDefault(emptyList())

            if (courses.isNotEmpty()) {
                val courseEntities = courses.map { c ->
                    CourseEntity(
                        courseExternalId = c.id,
                        name = c.name,
                        code = c.code,
                        teacher = c.teacher,
                        term = c.term,
                        sourcePlatform = activeConn.platformName,
                        materialCount = c.materialCount,
                        lastSyncAt = System.currentTimeMillis()
                    )
                }
                database.courseDao().insertCourses(courseEntities)
            }

            // Check for new materials
            var newMaterialsImported = 0
            val documentProcessor = DocumentProcessor(applicationContext)
            val repository = StudyLockRepository(applicationContext)

            for (course in courses.take(5)) {
                val materialsResult = adapter.getMaterials(course.id)
                val materials = materialsResult.getOrDefault(emptyList())

                for (mat in materials) {
                    val existing = database.schoolMaterialDao().getAllMaterialsList().find {
                        it.sourceUrl == mat.sourceUrl || it.title == mat.title
                    }
                    if (existing == null && mat.downloadUrl != null) {
                        // Download file
                        val destFile = File(applicationContext.cacheDir, "sync_${mat.id}_${mat.title.take(20)}")
                        val downloadRes = adapter.downloadMaterial(mat, destFile)
                        if (downloadRes.isSuccess) {
                            val processedRes = documentProcessor.processFile(destFile, mat.title)
                            val text = processedRes.getOrNull()?.extractedText ?: mat.description

                            val newEntity = SchoolMaterialEntity(
                                title = mat.title,
                                originalFileName = "${mat.title}.${mat.type.lowercase()}",
                                fileType = mat.type,
                                subject = course.name,
                                unitName = "Materials",
                                driveFolderPath = "Study App/School Materials/${course.name}",
                                sourceUrl = mat.sourceUrl,
                                downloadUrl = mat.downloadUrl,
                                contentSummary = processedRes.getOrNull()?.summary ?: "",
                                fullTextContent = text,
                                isDeckGenerated = false,
                                isSyncIncluded = true
                            )
                            val materialId = database.schoolMaterialDao().insertMaterial(newEntity)
                            newMaterialsImported++

                            // Auto generate deck
                            val deckTitle = "${course.name}: ${mat.title}"
                            val deckId = repository.createDeck(
                                title = deckTitle,
                                subject = course.name,
                                description = "Course material from ${course.name}",
                                sourceText = text
                            )
                            repository.generateAndSaveDeckContent(deckId, text, 5, 5)
                            database.schoolMaterialDao().setDeckGenerated(materialId, true)
                        }
                        destFile.delete()
                    }
                }
            }

            database.schoolConnectionDao().updateLastSync(System.currentTimeMillis())

            if (newMaterialsImported > 0) {
                showSyncNotification(
                    title = "New School Materials Synced",
                    body = "Imported $newMaterialsImported new educational files and generated study decks."
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "School background sync failed", e)
            Result.retry()
        }
    }

    private fun showSyncNotification(title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Academic Materials Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for newly synchronized school coursework and AI study decks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }
}
