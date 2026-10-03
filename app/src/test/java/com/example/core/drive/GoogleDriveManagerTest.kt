package com.example.core.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveManagerTest {

    private lateinit var context: Context
    private lateinit var driveManager: GoogleDriveManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        driveManager = GoogleDriveManager(context)
    }

    @Test
    fun testLinkAccountAndGetSignedInUser() = runBlocking {
        val user = driveManager.linkAccountWithEmail("student.test@school.edu", "Student Tester")
        assertEquals("student.test@school.edu", user.email)
        assertEquals("Student Tester", user.displayName)
        assertTrue(user.isConnected)

        val signedInUser = driveManager.getSignedInUser()
        assertNotNull(signedInUser)
        assertEquals("student.test@school.edu", signedInUser?.email)

        val flowUser = driveManager.currentUserFlow.first()
        assertNotNull(flowUser)
        assertEquals("student.test@school.edu", flowUser?.email)
    }

    @Test
    fun testCreateFolderAndUploadTextFile() = runBlocking {
        // Authenticate
        driveManager.linkAccountWithEmail("alex@gmail.com", "Alex Chen")

        // Create Folder
        val folderResult = driveManager.createFolder("Biology 101")
        assertTrue(folderResult.isSuccess)
        val folder = folderResult.getOrThrow()
        assertEquals("Biology 101", folder.name)
        assertTrue(folder.isFolder)

        // Upload Text File
        val fileResult = driveManager.uploadTextFile(
            fileName = "Cell_Membrane_Summary.txt",
            content = "Phospholipid bilayer with embedded transport proteins.",
            parentFolderId = folder.id,
            mimeType = "text/plain"
        )
        assertTrue(fileResult.isSuccess)
        val file = fileResult.getOrThrow()
        assertEquals("Cell_Membrane_Summary.txt", file.name)
        assertFalse(file.isFolder)
        assertEquals(folder.id, file.parentId)
    }

    @Test
    fun testSignOutClearsUser() = runBlocking {
        driveManager.linkAccountWithEmail("logout.test@gmail.com", "Logout User")
        assertNotNull(driveManager.getSignedInUser())

        driveManager.signOut()
        val afterSignOut = driveManager.getSignedInUser()
        assertNull(afterSignOut)
    }
}
