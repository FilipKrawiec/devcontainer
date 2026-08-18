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
            ResetCommand()
        )
        val rootCommand = RootCommand().subcommands(
            DoctorCommand(),
            projectsCommand,
            GetCommand(),
            ListCommand(),
            ResetCommand()
        )

        val rootHelpText = rootCommand.getFormattedHelp()
        assertTrue(rootHelpText?.contains("dev") == true)
        assertTrue(rootHelpText?.contains("doctor") == true)
        assertTrue(rootHelpText?.contains("projects") == true)
        assertTrue(rootHelpText?.contains("list") == true)
        assertTrue(rootHelpText?.contains("get") == true)
        assertTrue(rootHelpText?.contains("reset") == true)

        val projectsHelpText = projectsCommand.getFormattedHelp()
        assertTrue(projectsHelpText?.contains("get") == true)
        assertTrue(projectsHelpText?.contains("list") == true)
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
    fun `dev reset with --yes successfully removes repository`() {
        val tempDir = File.createTempFile("devws_reset_test_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val repoDir = File(tempDir, "github.com/user/to-delete/.git").apply { parentFile.mkdirs(); mkdirs() }
            val service = WorkspaceService(projectsRoot = tempDir)

            val rootCommand = RootCommand().subcommands(
                ResetCommand(service)
            )

            rootCommand.parse(listOf("reset", "github.com/user/to-delete", "--yes"))
            assertTrue(!File(tempDir, "github.com/user/to-delete").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
