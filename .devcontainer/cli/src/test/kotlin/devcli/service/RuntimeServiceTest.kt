package devcli.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeServiceTest {

    @Test
    fun `inspect reports required tools from configured path and the projects volume state`() {
        val tempDir = File.createTempFile("devws_runtime_test_", "").apply {
            delete()
            mkdirs()
        }
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val projectsDir = File(tempDir, "projects").apply { mkdirs() }
        listOf("dev", "node", "python").forEach { command ->
            File(binDir, command).apply {
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }
        }

        try {
            val report = RuntimeService(
                requiredTools = listOf("dev", "node", "python", "codex"),
                path = binDir.path,
                projectsRoot = projectsDir,
                dockerSocket = File(tempDir, "docker.sock")
            ).inspect()

            assertEquals(listOf("codex"), report.missingTools)
            assertTrue(report.projectsWritable)
            assertFalse(report.dockerSocketAvailable)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
