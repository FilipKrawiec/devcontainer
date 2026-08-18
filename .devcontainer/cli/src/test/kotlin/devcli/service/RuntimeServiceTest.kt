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
                dockerSocket = File(tempDir, "docker.sock"),
                previewSidecarProbe = { true }
            ).inspect()

            assertEquals(listOf("codex"), report.missingTools)
            assertFalse(report.allToolsInstalled)
            assertTrue(report.projectsWritable)
            assertFalse(report.dockerSocketAvailable)
            assertTrue(report.previewSidecarAvailable)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `inspect probes tool versions and categorizes correctly`() {
        val tempDir = File.createTempFile("devws_runtime_test2_", "").apply {
            delete()
            mkdirs()
        }
        val binDir = File(tempDir, "bin").apply { mkdirs() }
        val projectsDir = File(tempDir, "projects").apply { mkdirs() }

        val nodeScript = File(binDir, "node").apply {
            writeText("#!/bin/sh\necho 'v24.0.0'\n")
            setExecutable(true)
        }
        val gitScript = File(binDir, "git").apply {
            writeText("#!/bin/sh\necho 'git version 2.47.1'\n")
            setExecutable(true)
        }

        try {
            val tools = listOf(
                ToolDefinition("git", ToolCategory.CORE, listOf("--version")),
                ToolDefinition("node", ToolCategory.RUNTIMES, listOf("--version")),
                ToolDefinition("claude", ToolCategory.AI_AGENTS, listOf("--version"))
            )

            val report = RuntimeService(
                toolDefinitions = tools,
                path = binDir.path,
                projectsRoot = projectsDir,
                dockerSocket = File(tempDir, "docker.sock"),
                previewSidecarProbe = { false },
                probeVersions = true
            ).inspect()

            val gitStatus = report.toolStatuses.first { it.name == "git" }
            assertTrue(gitStatus.isInstalled)
            assertEquals("2.47.1", gitStatus.version)
            assertEquals(ToolCategory.CORE, gitStatus.category)

            val nodeStatus = report.toolStatuses.first { it.name == "node" }
            assertTrue(nodeStatus.isInstalled)
            assertEquals("v24.0.0", nodeStatus.version)
            assertEquals(ToolCategory.RUNTIMES, nodeStatus.category)

            val claudeStatus = report.toolStatuses.first { it.name == "claude" }
            assertFalse(claudeStatus.isInstalled)
            assertEquals(ToolCategory.AI_AGENTS, claudeStatus.category)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
