package devcli.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceServiceTest {

    @Test
    fun `findMatchingWorkspaces matches exact and suffix paths`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "github.com/user/repo1/.git").apply { parentFile.mkdirs(); mkdirs() }
            File(tempDir, "gitlab.com/group/repo2/.git").apply { parentFile.mkdirs(); mkdirs() }

            val service = WorkspaceService(projectsRoot = tempDir)

            val matchedExact = service.findMatchingWorkspaces("github.com/user/repo1")
            assertEquals(listOf("github.com/user/repo1"), matchedExact)

            val matchedPartial = service.findMatchingWorkspaces("user/repo1")
            assertEquals(listOf("github.com/user/repo1"), matchedPartial)

            val matchedRepoOnly = service.findMatchingWorkspaces("repo2")
            assertEquals(listOf("gitlab.com/group/repo2"), matchedRepoOnly)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `findMatchingWorkspaces does not match partial repo name suffixes`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }
        try {
            File(tempDir, "github.com/user/my-repo/.git").apply { parentFile.mkdirs(); mkdirs() }
            File(tempDir, "github.com/user/repo/.git").apply { parentFile.mkdirs(); mkdirs() }

            val service = WorkspaceService(projectsRoot = tempDir)
            val matched = service.findMatchingWorkspaces("repo")
            assertEquals(listOf("github.com/user/repo"), matched)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `fetchWorkspaces returns failure for non-existent workspace`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)
            val result = service.fetchWorkspaces("nonexistent/repo")
            assertTrue(result.isFailure)
            assertEquals("Repository 'nonexistent/repo' not found in ${tempDir.path}", result.exceptionOrNull()?.message)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `fetchWorkspaces returns empty success when no repos exist`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)
            val result = service.fetchWorkspaces()
            assertTrue(result.isSuccess)
            assertEquals(emptyList(), result.getOrNull())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getWorkspace returns empty success when no repos exist and no repoRef given`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)
            val result = service.getWorkspace(null)
            assertTrue(result.isSuccess)
            assertEquals(emptyList(), result.getOrNull())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `listWorkspaceDetails handles existing workspace details cleanly`() {
        val tempDir = File.createTempFile("devws_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "github.com/user/repo1/.git").apply { parentFile.mkdirs(); mkdirs() }

            val service = WorkspaceService(projectsRoot = tempDir)
            val details = service.listWorkspaceDetails()

            assertEquals(1, details.size)
            assertEquals("github.com/user/repo1", details[0].relativePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
