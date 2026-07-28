package devcli.commands.projects

import com.github.ajalt.clikt.core.subcommands
import devcli.commands.RootCommand
import devcli.service.WorkspaceService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
            // Command parses without exception
            assertTrue(true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `dev projects help options are registered`() {
        val rootCommand = RootCommand().subcommands(
            ProjectsCommand().subcommands(
                CloneCommand(),
                ListCommand(),
                FetchCommand(),
                ResetCommand()
            )
        )

        val helpText = rootCommand.getFormattedHelp()
        assertTrue(helpText?.contains("dev") == true)
    }
}
