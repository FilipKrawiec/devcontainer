package devcli.commands

import com.github.ajalt.clikt.core.ProgramResult
import devcli.service.RuntimeReport
import devcli.service.RuntimeService
import devcli.service.ToolCategory
import devcli.service.ToolStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DoctorCommandTest {

    @Test
    fun `doctor succeeds when all tools are installed and projects volume is writable`() {
        val fakeService = object : RuntimeService() {
            override fun inspect(): RuntimeReport {
                return RuntimeReport(
                    toolStatuses = listOf(
                        ToolStatus("dev", ToolCategory.CORE, true, "1.0.0"),
                        ToolStatus("git", ToolCategory.CORE, true, "2.47.1"),
                        ToolStatus("node", ToolCategory.RUNTIMES, true, "v24.0.0"),
                        ToolStatus("agy", ToolCategory.AI_AGENTS, true, "0.1.0")
                    ),
                    projectsWritable = true,
                    projectsPath = "/projects",
                    dockerSocketAvailable = true,
                    previewSidecarAvailable = true
                )
            }
        }

        val doctorCommand = DoctorCommand(fakeService)
        doctorCommand.parse(emptyList())
        assertTrue(true)
    }

    @Test
    fun `doctor fails with exit code 1 when required tools are missing`() {
        val fakeService = object : RuntimeService() {
            override fun inspect(): RuntimeReport {
                return RuntimeReport(
                    toolStatuses = listOf(
                        ToolStatus("dev", ToolCategory.CORE, true, "1.0.0"),
                        ToolStatus("git", ToolCategory.CORE, false, null),
                        ToolStatus("node", ToolCategory.RUNTIMES, true, "v24.0.0")
                    ),
                    projectsWritable = true,
                    projectsPath = "/projects",
                    dockerSocketAvailable = true,
                    previewSidecarAvailable = true
                )
            }
        }

        val doctorCommand = DoctorCommand(fakeService)
        val ex = assertFailsWith<ProgramResult> {
            doctorCommand.parse(emptyList())
        }
        assertEquals(1, ex.statusCode)
    }

    @Test
    fun `doctor fails with exit code 1 when projects volume is not writable`() {
        val fakeService = object : RuntimeService() {
            override fun inspect(): RuntimeReport {
                return RuntimeReport(
                    toolStatuses = listOf(
                        ToolStatus("dev", ToolCategory.CORE, true, "1.0.0")
                    ),
                    projectsWritable = false,
                    projectsPath = "/projects",
                    dockerSocketAvailable = true,
                    previewSidecarAvailable = true
                )
            }
        }

        val doctorCommand = DoctorCommand(fakeService)
        val ex = assertFailsWith<ProgramResult> {
            doctorCommand.parse(emptyList())
        }
        assertEquals(1, ex.statusCode)
    }
}
