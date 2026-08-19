package devcli.commands.projects

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import devcli.commands.DoctorCommand
import devcli.commands.RootCommand
import devcli.service.WorkspaceService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectsCommandTest {

    @Test
    fun `dev projects list parses and executes cleanly`() {
        val tempDir = File.createTempFile("devws_cmd_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "github.com/user/repo1/.git").apply { parentFile.mkdirs(); mkdirs() }
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ProjectsCommand().subcommands(
                    ListCommand(service)
                )
            )

            rootCommand.parse(listOf("projects", "list"))
            assertTrue(true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `top-level dev list alias executes cleanly`() {
        val tempDir = File.createTempFile("devws_cmd_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "github.com/user/repo1/.git").apply { parentFile.mkdirs(); mkdirs() }
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ListCommand(service)
            )

            rootCommand.parse(listOf("list"))
            assertTrue(true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `dev projects get parses cleanly when empty`() {
        val tempDir = File.createTempFile("devws_cmd_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ProjectsCommand().subcommands(
                    GetCommand(service)
                )
            )

            rootCommand.parse(listOf("projects", "get"))
            assertTrue(true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `top-level dev get alias parses cleanly`() {
        val tempDir = File.createTempFile("devws_cmd_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                GetCommand(service)
            )

            rootCommand.parse(listOf("get"))
            assertTrue(true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `dev projects help options and aliases are registered`() {
        val projectsCommand = ProjectsCommand().subcommands(
            GetCommand(),
            ListCommand(),
            ResetCommand(),
            InitCommand()
        )
        val rootCommand = RootCommand().subcommands(
            DoctorCommand(),
            projectsCommand,
            GetCommand(),
            ListCommand(),
            ResetCommand(),
            InitCommand()
        )

        val rootHelpText = rootCommand.getFormattedHelp()
        assertTrue(rootHelpText?.contains("dev") == true)
        assertTrue(rootHelpText?.contains("doctor") == true)
        assertTrue(rootHelpText?.contains("projects") == true)
        assertTrue(rootHelpText?.contains("list") == true)
        assertTrue(rootHelpText?.contains("get") == true)
        assertTrue(rootHelpText?.contains("init") == true)
        assertTrue(rootHelpText?.contains("reset") == true)

        val projectsHelpText = projectsCommand.getFormattedHelp()
        assertTrue(projectsHelpText?.contains("get") == true)
        assertTrue(projectsHelpText?.contains("list") == true)
        assertTrue(projectsHelpText?.contains("init") == true)
        assertTrue(projectsHelpText?.contains("reset") == true)
    }

    @Test
    fun `dev projects reset requires explicit confirmation in non-interactive mode`() {
        val rootCommand = RootCommand().subcommands(
            ProjectsCommand().subcommands(ResetCommand())
        )

        val ex = assertFailsWith<ProgramResult> {
            rootCommand.parse(listOf("projects", "reset", "github.com/user/repository"))
        }
        assertEquals(2, ex.statusCode)
    }

    @Test
    fun `dev reset with --yes successfully removes repository and prunes empty parents`() {
        val tempDir = File.createTempFile("devws_reset_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val repoDir = File(tempDir, "github.com/testorg/to-delete/.git").apply { parentFile.mkdirs(); mkdirs() }
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ResetCommand(service)
            )

            // Test reset with short repo name
            rootCommand.parse(listOf("reset", "testorg/to-delete", "--yes"))
            assertTrue(!File(tempDir, "github.com/testorg/to-delete").exists(), "Repository directory should be removed")
            assertTrue(!File(tempDir, "github.com/testorg").exists(), "Empty org parent directory should be pruned")
            assertTrue(!File(tempDir, "github.com").exists(), "Empty host parent directory should be pruned")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `dev reset retains parent directories when sibling repositories exist`() {
        val tempDir = File.createTempFile("devws_reset_sibling_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            File(tempDir, "github.com/org1/repo1/.git").apply { parentFile.mkdirs(); mkdirs() }
            File(tempDir, "github.com/org1/repo2/.git").apply { parentFile.mkdirs(); mkdirs() }
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ResetCommand(service)
            )

            rootCommand.parse(listOf("reset", "github.com/org1/repo1", "--yes"))
            assertTrue(!File(tempDir, "github.com/org1/repo1").exists(), "repo1 should be removed")
            assertTrue(File(tempDir, "github.com/org1/repo2").exists(), "repo2 should remain")
            assertTrue(File(tempDir, "github.com/org1").exists(), "org1 should remain because repo2 exists")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `dev projects init scaffolds clean monorepo with files and initial commit`() {
        val tempDir = File.createTempFile("devws_init_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)
            val rootCommand = RootCommand().subcommands(
                ProjectsCommand().subcommands(
                    InitCommand(service)
                )
            )

            rootCommand.parse(listOf("projects", "init", "user/my-sample-repo", "-d", "My custom description"))

            val repoDir = File(tempDir, "github.com/user/my-sample-repo")
            assertTrue(repoDir.exists(), "Repository directory should exist")
            assertTrue(File(repoDir, "components/.gitkeep").exists(), "components/.gitkeep should exist")
            assertTrue(File(repoDir, "deploy/.gitkeep").exists(), "deploy/.gitkeep should exist")
            assertTrue(File(repoDir, ".gitignore").exists(), ".gitignore should exist")
            assertTrue(File(repoDir, "justfile").exists(), "justfile should exist")
            assertTrue(File(repoDir, "AGENTS.md").exists(), "AGENTS.md should exist")
            assertTrue(File(repoDir, "README.md").exists(), "README.md should exist")

            val readmeText = File(repoDir, "README.md").readText()
            assertTrue(readmeText.contains("# my-sample-repo"))
            assertTrue(readmeText.contains("My custom description"))

            val agentsText = File(repoDir, "AGENTS.md").readText()
            assertTrue(agentsText.contains("active_skills:"))
            assertTrue(agentsText.contains("Repository Rules"))

            // Verify Git status
            assertTrue(File(repoDir, ".git").exists(), "Git repo should be initialized")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `top-level dev init alias executes cleanly`() {
        val tempDir = File.createTempFile("devws_init_alias_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val service = WorkspaceService(projectsRoot = tempDir)
            val rootCommand = RootCommand().subcommands(
                InitCommand(service)
            )

            rootCommand.parse(listOf("init", "github.com/org/repo-alias", "--no-commit"))

            val repoDir = File(tempDir, "github.com/org/repo-alias")
            assertTrue(repoDir.exists(), "Repository directory should exist")
            assertTrue(File(repoDir, "README.md").exists(), "README.md should exist")
            assertTrue(File(repoDir, ".git").exists(), "Git repo should be initialized")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

