package devcli.commands.projects

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.ProgramResult
import devcli.commands.RootCommand
import devcli.service.WorkspaceService
import java.io.File
import kotlin.test.Test
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
    fun `dev projects help options are registered`() {
        val projectsCommand = ProjectsCommand().subcommands(
            GetCommand(),
            ListCommand(),
            ResetCommand()
        )
        val rootCommand = RootCommand().subcommands(projectsCommand)

        val rootHelpText = rootCommand.getFormattedHelp()
        assertTrue(rootHelpText?.contains("dev") == true)

        val projectsHelpText = projectsCommand.getFormattedHelp()
        assertTrue(projectsHelpText?.contains("get") == true)
        assertTrue(projectsHelpText?.contains("list") == true)
    }

    @Test
    fun `dev projects reset requires explicit confirmation`() {
        val rootCommand = RootCommand().subcommands(
            ProjectsCommand().subcommands(ResetCommand())
        )

        assertFailsWith<ProgramResult> {
            rootCommand.parse(listOf("projects", "reset", "github.com/user/repository"))
        }
    }
}
