package devcli.contract

import devcli.createDevCli
import devcli.forge.InMemoryForges
import devcli.forge.api.ForgeCommand
import devcli.forge.api.ForgeJsonFormat
import devcli.forge.api.JobTraceDto
import devcli.forge.api.MergeResultDto
import devcli.forge.api.PipelineJobDto
import devcli.forge.api.PipelineRunDto
import devcli.forge.api.PullRequestDto
import devcli.forge.api.ReviewResultDto
import devcli.issuetracker.InMemoryWorkItems
import devcli.issuetracker.api.IssueTrackerCommand
import devcli.issuetracker.api.JsonFormat
import devcli.issuetracker.api.WorkItemDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliContractTest {

    @Test
    fun `RootCommand registers expected SDLC subcommands`() {
        val root = createDevCli(
            workItems = InMemoryWorkItems(),
            forges = InMemoryForges()
        )
        val subcommands = root.registeredSubcommands().map { it.commandName }.toSet()

        assertTrue(subcommands.contains("issuetracker"), "RootCommand must register 'issuetracker'")
        assertTrue(subcommands.contains("forge"), "RootCommand must register 'forge'")
        assertTrue(subcommands.contains("projects"), "RootCommand must register 'projects'")
        assertTrue(subcommands.contains("doctor"), "RootCommand must register 'doctor'")
    }

    @Test
    fun `IssueTrackerCommand subcommands match SDLC contract`() {
        val workItems = InMemoryWorkItems()
        val issueTracker = IssueTrackerCommand(workItems)
        val subcommands = issueTracker.registeredSubcommands().map { it.commandName }.toSet()

        assertEquals(
            setOf("create", "get", "set-phase", "comment"),
            subcommands,
            "IssueTrackerCommand must match SDLC specification subcommands"
        )
    }

    @Test
    fun `ForgeCommand subcommands match SDLC contract`() {
        val forges = InMemoryForges()
        val forge = ForgeCommand(forges)
        val subcommands = forge.registeredSubcommands().map { it.commandName }.toSet()

        assertTrue(subcommands.contains("branch"), "ForgeCommand must register 'branch'")
        assertTrue(subcommands.contains("pr"), "ForgeCommand must register 'pr'")
        assertTrue(subcommands.contains("pipeline"), "ForgeCommand must register 'pipeline'")

        val pipelineCmd = forge.registeredSubcommands().first { it.commandName == "pipeline" }
        val pipelineSubcommands = pipelineCmd.registeredSubcommands().map { it.commandName }.toSet()
        assertEquals(
            setOf("list", "view", "trace"),
            pipelineSubcommands,
            "ForgeCommand 'pipeline' group must register 'list', 'view', 'trace'"
        )
    }

    @Test
    fun `WorkItemDto JSON schema fulfills deliver contract`() {
        val dto = WorkItemDto(
            id = 42,
            title = "feat(test): sample feature",
            body = "Task description",
            type = "feature",
            phase = "01 Define",
            url = "https://github.com/FilipKrawiec/devcontainer/issues/42"
        )
        val json = JsonFormat.toJson(dto)

        assertTrue(json.contains("\"id\": 42"))
        assertTrue(json.contains("\"title\": \"feat(test): sample feature\""))
        assertTrue(json.contains("\"type\": \"feature\""))
        assertTrue(json.contains("\"phase\": \"01 Define\""))
        assertTrue(json.contains("\"url\": \"https://github.com/FilipKrawiec/devcontainer/issues/42\""))
    }

    @Test
    fun `PullRequest and Merge JSON schemas fulfill deliver contract`() {
        val prDto = PullRequestDto(
            number = 10,
            title = "feat: new capability",
            body = "Closes #42",
            head = "feat-branch",
            base = "main",
            url = "https://github.com/FilipKrawiec/devcontainer/pull/10"
        )
        val prJson = ForgeJsonFormat.toJson(prDto)
        assertTrue(prJson.contains("\"number\": 10"))
        assertTrue(prJson.contains("\"head\": \"feat-branch\""))
        assertTrue(prJson.contains("\"base\": \"main\""))

        val mergeDto = MergeResultDto(
            prNumber = 10,
            strategy = "squash",
            branchDeleted = true
        )
        val mergeJson = ForgeJsonFormat.toJson(mergeDto)
        assertTrue(mergeJson.contains("\"prNumber\": 10"))
        assertTrue(mergeJson.contains("\"strategy\": \"squash\""))
        assertTrue(mergeJson.contains("\"branchDeleted\": true"))

        val reviewDto = ReviewResultDto(
            prNumber = 10,
            verdict = "approve",
            notes = "Quality Engineer APPROVED"
        )
        val reviewJson = ForgeJsonFormat.toJson(reviewDto)
        assertTrue(reviewJson.contains("\"verdict\": \"approve\""))
        assertTrue(reviewJson.contains("\"notes\": \"Quality Engineer APPROVED\""))
    }

    @Test
    fun `Pipeline and JobTrace JSON schemas fulfill deliver contract`() {
        val pipelineDto = PipelineRunDto(
            id = 1001L,
            workflow = "Build and Test",
            branch = "feat/pipeline",
            status = "failure",
            conclusion = "failure",
            event = "pull_request",
            commitSha = "abc12345",
            createdAt = "2026-08-19T12:00:00Z",
            url = "https://github.com/owner/repo/actions/runs/1001",
            jobs = listOf(
                PipelineJobDto(
                    id = 2001L,
                    name = "gradle-test",
                    status = "completed",
                    conclusion = "failure",
                    startedAt = "2026-08-19T12:00:05Z",
                    completedAt = "2026-08-19T12:01:10Z",
                    url = "https://github.com/owner/repo/actions/jobs/2001"
                )
            )
        )
        val runJson = ForgeJsonFormat.toJson(pipelineDto)
        assertTrue(runJson.contains("\"id\": 1001"))
        assertTrue(runJson.contains("\"workflow\": \"Build and Test\""))
        assertTrue(runJson.contains("\"status\": \"failure\""))
        assertTrue(runJson.contains("\"jobs\": ["))

        val traceDto = JobTraceDto(
            jobId = 2001L,
            totalLines = 50,
            matchedLines = 2,
            lines = listOf("[ERROR] Test failed", "[FATAL] Process exited with 1")
        )
        val traceJson = ForgeJsonFormat.toJson(traceDto)
        assertTrue(traceJson.contains("\"jobId\": 2001"))
        assertTrue(traceJson.contains("\"totalLines\": 50"))
        assertTrue(traceJson.contains("\"matchedLines\": 2"))
        assertTrue(traceJson.contains("[ERROR] Test failed"))
    }
}
