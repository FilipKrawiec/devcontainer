package devcli.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceRefTest {

    @Test
    fun `short format defaults to GitHub SSH remote and github dot com target directory`() {
        val ref = WorkspaceRef.fromRemote("FilipKrawiec/skills")
        assertEquals("git@github.com:FilipKrawiec/skills.git", ref.remoteUrl)
        assertEquals("github.com/FilipKrawiec/skills", ref.relativePath)
        assertEquals("/projects/github.com/FilipKrawiec/skills", ref.targetDirectoryPath)
    }

    @Test
    fun `full GitHub SSH URL normalizes correctly`() {
        val ref = WorkspaceRef.fromRemote("git@github.com:FilipKrawiec/skills.git")
        assertEquals("git@github.com:FilipKrawiec/skills.git", ref.remoteUrl)
        assertEquals("github.com/FilipKrawiec/skills", ref.relativePath)
        assertEquals("/projects/github.com/FilipKrawiec/skills", ref.targetDirectoryPath)
    }

    @Test
    fun `full GitLab SSH URL normalizes correctly`() {
        val ref = WorkspaceRef.fromRemote("git@gitlab.com:group/project.git")
        assertEquals("git@gitlab.com:group/project.git", ref.remoteUrl)
        assertEquals("gitlab.com/group/project", ref.relativePath)
        assertEquals("/projects/gitlab.com/group/project", ref.targetDirectoryPath)
    }

    @Test
    fun `domain relative path defaults to SSH remote`() {
        val ref = WorkspaceRef.fromRemote("gitlab.com/group/project")
        assertEquals("git@gitlab.com:group/project.git", ref.remoteUrl)
        assertEquals("gitlab.com/group/project", ref.relativePath)
        assertEquals("/projects/gitlab.com/group/project", ref.targetDirectoryPath)
    }

    @Test
    fun `HTTPS URL normalizes correctly`() {
        val ref = WorkspaceRef.fromRemote("https://github.com/FilipKrawiec/skills.git")
        assertEquals("https://github.com/FilipKrawiec/skills.git", ref.remoteUrl)
        assertEquals("github.com/FilipKrawiec/skills", ref.relativePath)
        assertEquals("/projects/github.com/FilipKrawiec/skills", ref.targetDirectoryPath)
    }
}
